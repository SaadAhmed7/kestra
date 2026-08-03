/**
 * Types for the Storybook/Vitest API double. The implementation is `apiMock.js`, which must stay
 * plain JS: it is imported first by `preview.jsx` and `vitest.setup.js` to patch `window.fetch`
 * before any `src/` or SDK module is evaluated.
 */

/** What a route handler is given about the request it is answering. */
export interface ApiMockContext {
    /** Upper-case HTTP method. */
    method: string;
    /** The `/api/v1`-onward path, tenant segment removed. */
    path: string;
    /** The URL as the caller passed it. */
    url: string;
    /** Parsed query string — where paging and filtering live for GETs. */
    query: URLSearchParams;
    /** Request body, when the caller had one. */
    body?: unknown;
}

export type ApiMockHandler = unknown | ((context: ApiMockContext) => unknown);

/**
 * Register a route handler for the duration of the current story, taking precedence over the shared
 * handler table. Call it from the story's `setup()` or a decorator.
 *
 * @param key `METHOD <path from /api/v1 onward>`, e.g. `"GET /flows/search"`. Supports `:param`
 *   segments and a trailing `*`.
 */
export function mockApiRoute(key: string, handler: ApiMockHandler): void;

/** Name the running story in "unmocked API request" warnings and reset per-story state. */
export function beginStoryScope(label?: string): void;

/** Resolve an API call the same way the installed fetch wrapper does. */
export function resolveApiRequest(
    method: string,
    rawUrl: string,
    context?: Partial<ApiMockContext>,
): {status: number; data: unknown};

/** Axios-like adapter over {@link resolveApiRequest}, for story-local `setMockClient()` catch-alls. */
export function mockClientFallback(
    method: string,
    uri: string,
    data?: unknown,
): {data: unknown; status: number; statusText: string; headers: Record<string, string>};

/** The installed `fetch` wrapper, also handed to the SDK via `configureClient({fetch})`. */
export const apiFetch: typeof fetch;
