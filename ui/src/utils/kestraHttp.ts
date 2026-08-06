import NProgress from "nprogress"
import type {Router} from "vue-router"
import {configureClient, useClient, asProblem, type ProblemDetail} from "@kestra-io/kestra-sdk"

// ── NProgress helpers ────────────────────────────────────────────────────────

let pendingRoute = false
let requestsTotal = 0
let requestsCompleted = 0

/** Request-option flag marking a request that must be left out of progress accounting. */
const SKIP_PROGRESS = "__kestraSkipProgress"

function progressComplete() {
    pendingRoute = false
    requestsTotal = 0
    requestsCompleted = 0
    NProgress.done()
}

function initProgress() {
    requestsTotal++
    if (requestsTotal === 1) {
        setTimeout(() => {
            NProgress.start()
            NProgress.set(requestsCompleted / requestsTotal)
        }, 0)
    } else {
        NProgress.set(requestsCompleted / requestsTotal)
    }
}

function increaseProgress() {
    setTimeout(() => {
        requestsCompleted++
        if (requestsCompleted >= requestsTotal) progressComplete()
        else NProgress.set(requestsCompleted / requestsTotal - 0.1)
    }, 50)
}

// ── Types ────────────────────────────────────────────────────────────────────

export interface KestraHttpError extends Error {
    status?: number
    /**
     * The RFC 9457 problem document, when the failure came from the Kestra API — which is every error the
     * app raises against it. Prefer this, or the `asProblem` helper, over digging into `response.data`.
     */
    problem?: ProblemDetail
    response?: {
        /**
         * The parsed body: the problem document for any API error, an arbitrary body otherwise.
         *
         * Deliberately `unknown` rather than `any` so it cannot be dereferenced without a narrowing step
         * — but note that a `catch (e: any)` call site defeats that, so the `noLegacyErrorFields` unit
         * test is what actually keeps reads of the removed `message`/`_embedded`/`invalids` fields out.
         * Use `problem`, or the `asProblem` helper, instead of narrowing this by hand.
         */
        data: unknown
        status: number
        statusText: string
        headers: Record<string, string>
        request: {responseURL: string}
        config: {method: string; url: string; showMessageOnError?: boolean}
    }
    config?: {method: string; url: string; showMessageOnError?: boolean}
}

/**
 * Rebuilds an axios-like `data` object from an Error the SDK flattened a non-problem body onto. Only
 * reached for responses from outside the API surface.
 */
function legacyData(error: KestraHttpError): Record<string, unknown> {
    const data: Record<string, unknown> = {message: error.message}
    for (const key of Object.keys(error)) {
        if (key !== "status" && key !== "message" && key !== "problem") {
            data[key] = (error as unknown as Record<string, unknown>)[key]
        }
    }
    return data
}

export interface KestraHttpOptions {
    router?: Router
    coreStore?: {message: unknown; error: unknown}
    beforeLogout?: () => void
    isLoggedIn?: () => boolean
    onError?: (type: "message" | "error", error: unknown) => void
    /**
     * Called on a 401 when the user is not logged in. Return (or resolve) `true` to
     * retry the original request once more - e.g. EE attempts a silent token refresh
     * first and retries only if it succeeds. Defaults to navigating to the login route
     * without retrying.
     */
    onUnauthorized?: (navigateToLogin: () => void, error: KestraHttpError) => Promise<boolean> | boolean | void
}

/**
 * Configures the shared fetch client both generated SDK endpoint calls and useClient()'s
 * ad-hoc calls go through, wiring NProgress, centralized 401/404/400 error handling, and a
 * 401-retry hook (EE plugs in silent token refresh). configureClient()'s own request/response/
 * error interceptors normalize content-type/accept and error status/message before ours run,
 * and cover useClient() too - it shares client.interceptors under the hood.
 *
 * Returns the generated-endpoint client, so callers can register extra per-request behavior
 * (e.g. a CSRF header) via `client.interceptors.request.use(...)` - that also reaches
 * useClient() calls. Bind `useClient()` itself to `$http` for ad-hoc calls.
 */
export function setupKestraHttp(
    clientConfig: Record<string, unknown> = {},
    options: KestraHttpOptions = {},
): ReturnType<typeof configureClient> {
    const {
        router,
        coreStore,
        beforeLogout,
        isLoggedIn = () => false,
        onError = (type: "message" | "error", error: unknown) => {
            if (!coreStore) return
            const kestraError = error as KestraHttpError
            if (type === "message") {
                coreStore.message = {
                    variant: "error",
                    problem: kestraError.problem,
                    status: kestraError.response?.status,
                    request: {
                        method: kestraError.response?.config.method ?? "GET",
                        url: kestraError.response?.config.url ?? "unknown url",
                    },
                }
            } else {
                coreStore.error = kestraError.response?.status
            }
        },
        onUnauthorized = (navigate: () => void) => {
            beforeLogout?.()
            navigate()
            return false
        },
    } = options

    function navigateToLogin() {
        if (!router) return
        const currentPath = window.location.pathname
        router.push({
            name: "login",
            query: currentPath.includes("/login") ? {} : {from: currentPath},
        })
    }

    function handleErrorCentrally(error: KestraHttpError): KestraHttpError {
        const status = error.status
        if (status === 404) {
            // Let callers handle an expected 404 locally (e.g. rehydrating a Copilot thread that no
            // longer exists) by passing `showMessageOnError: false`, instead of the global not-found page.
            if (error.config?.showMessageOnError !== false) {
                onError("error", error)
            }
        } else if (status !== 401 && status !== 400 && error.response?.data && error.config?.showMessageOnError !== false) {
            onError("message", error)
        }
        return error
    }

    // Wraps get/post/put/patch/delete (and the SDK's client.request) so a 401 triggers
    // onUnauthorized and, if it resolves truthy, retries the ORIGINAL call exactly once
    // (re-invoking the unwrapped function with the same arguments) - no config
    // serialization/replay needed, no risk of retry loops since the retry bypasses this
    // wrapper.
    function withAuthRetry<F extends (...args: any[]) => Promise<any>>(fn: F): F {
        return (async (...args: Parameters<F>) => {
            try {
                return await fn(...args)
            } catch (error) {
                const kestraError = error as KestraHttpError
                if (kestraError.status === 401 && !isLoggedIn()) {
                    const shouldRetry = await onUnauthorized(navigateToLogin, kestraError)
                    if (shouldRetry) return fn(...args)
                }
                throw error
            }
        }) as F
    }

    const client = configureClient(clientConfig)

    // Long-lived SSE streams must not participate in progress accounting: the SDK runs request
    // interceptors for them (createSseClient calls them from its onRequest hook) but never the
    // response/error ones, so such a request would stay pending forever and pin the bar open.
    // Tagging the whole `sse` namespace covers every current and future stream endpoint.
    const sse = (client as unknown as {sse?: Record<string, (streamOptions: Record<string, unknown>) => unknown>}).sse
    for (const method of Object.keys(sse ?? {})) {
        const streamFn = sse![method].bind(sse)
        sse![method] = (streamOptions) => streamFn({...streamOptions, [SKIP_PROGRESS]: true})
    }

    client.interceptors.request.use((request, opts: unknown) => {
        if (typeof document !== "undefined" && !(opts as Record<string, unknown>)?.[SKIP_PROGRESS]) initProgress()
        return request
    })

    // Fires for both successful and failed responses (the SDK checks response.ok AFTER
    // running response interceptors) - a genuine network error (no response) is ticked
    // from the error interceptor's no-response branch instead.
    client.interceptors.response.use((response) => {
        increaseProgress()
        return response
    })

    client.interceptors.error.use((error, response, request, opts) => {
        const kestraError = error as KestraHttpError
        if (!response) {
            increaseProgress()
            return kestraError
        }

        // An API error is a problem document, and `response.data` IS that document — the same value the
        // useClient facade attaches, so both call paths finally expose one identical shape. Anything else
        // came from outside the API surface (Micronaut's own responses, the Apps error layout, plain text);
        // keep reconstructing those from the flattened Error so they still reach their call sites.
        const problem = asProblem(kestraError)
        const data: Record<string, unknown> = problem
            ? (problem as unknown as Record<string, unknown>)
            : legacyData(kestraError)

        const responseHeaders: Record<string, string> = {}
        response.headers.forEach((value, key) => {responseHeaders[key] = value})

        kestraError.problem = problem
        kestraError.response = {
            data,
            status: response.status,
            statusText: response.statusText,
            headers: responseHeaders,
            request: {responseURL: response.url},
            config: {
                method: request?.method ?? "",
                url: request?.url ?? "",
                showMessageOnError: (opts as {showMessageOnError?: boolean} | undefined)?.showMessageOnError,
            },
        }
        kestraError.config = kestraError.response.config

        // A 400 rejects like any other error, so `instanceof Error`, `.status` and `.response` all hold on
        // the status the bulk endpoints use. handleErrorCentrally still keeps it out of the global toast.
        return handleErrorCentrally(kestraError)
    })

    // client.get/post/put/patch/delete/request and useClient()'s get/post/put/patch/delete
    // are looked up live off these objects by every caller, so wrapping the methods in
    // place here covers both paths for every future call, no matter where it's made from.
    for (const target of [client, useClient()] as const) {
        const targetAny = target as unknown as Record<string, (...args: any[]) => Promise<any>>
        for (const method of ["get", "post", "put", "patch", "delete", "request", "stream"]) {
            if (typeof targetAny[method] === "function") targetAny[method] = withAuthRetry(targetAny[method].bind(target))
        }
    }

    // ── Router hooks: NProgress on navigation ────────────────────────────────
    router?.beforeEach(() => {
        if (pendingRoute) requestsTotal--
        pendingRoute = true
        initProgress()
    })
    router?.afterEach(() => {
        if (pendingRoute) {
            increaseProgress()
            pendingRoute = false
        }
    })

    return client
}
