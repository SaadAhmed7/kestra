import Triggers from "../../../../src/components/admin/triggers/Triggers.vue";
import {vueRouter} from "storybook-vue3-router";
import {expect, waitFor} from "storybook/test";
import {setMockClient} from "@kestra-io/kestra-sdk"
import {mockApiRoute, mockClientFallback} from "../../../../.storybook/apiMock";

// TriggersGrid (the "add" tab) and TriggersManage (the "manage" tab) both reach their data through
// generated SDK functions - pluginsStore.listTriggers() -> PluginsAPI.listTriggerPlugins() and
// utils/triggers.ts searchTriggers() -> TriggersAPI.searchTriggers(). Those use the SDK's own fetch
// client, so neither the axios instance setMockClient() swaps nor vi.mock() of the SDK submodule
// reaches them; mockApiRoute() registers the payload at the fetch layer, which does work.

const ROUTES = [
    {
        path: "/",
        name: "home",
        component: {template: "<div>home</div>"}
    },
    {
        path: "/:tab?",
        name: "admin/triggers",
        component: Triggers
    },
    {
        path: "/flows/edit/:namespace/:id",
        name: "flows/update",
        component: {template: "<div>update flow</div>"}
    },
    // Each manage-tab row links its namespace through KsEntityLink, which resolves this route inside
    // a computed. Without it vue-router throws "No match for namespaces/update" mid-render and the
    // table body silently comes up empty — the rows are in ElTable's data but never reach the DOM.
    {
        path: "/namespaces/:id",
        name: "namespaces/update",
        component: {template: "<div>namespace</div>"}
    },
]

const meta = {
    title: "Components/Admin/Triggers",
    component: Triggers,
}

export default meta;

/** Catalogue entries for the "add" tab, shaped like listTriggerPlugins' `results`. */
const triggerPlugins = [
    {
        "type": "io.kestra.plugin.core.trigger.Schedule",
        "name": "Schedule",
        "description": "Trigger a flow on a `cron` schedule.",
    },
    {
        "type": "io.kestra.plugin.core.trigger.Webhook",
        "name": "Webhook",
        "description": "Trigger a flow from an HTTP request.",
    },
]

const triggersData = [
    {
        "trigger": {
            "id": "every10min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": true,
            "cron": "10 * * * *"
        },
        "state": {
            "namespace": "company.team",
            "flowId": "trigger_test_foo",
            "triggerId": "every10min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": true,
            "locked": false
        }
    },
    {
        "trigger": {
            "id": "every5min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": false,
            "cron": "5 * * * *"
        },
        "state": {
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_bar",
            "triggerId": "every5min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": false,
            "locked": false
        }
    },
    {
        "trigger": {
            "backfill": true,
            "id": "every1min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": false,
            "cron": "1 * * * *"
        },
        "state": {
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_backfill_running",
            "triggerId": "every1min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": false,
            "locked": true
        }
    },
    {
        "trigger": {
            "backfill": {
                "paused": true
            },
            "id": "every1min",
            "type": "io.kestra.plugin.core.trigger.Schedule",
            "disabled": false,
            "cron": "1 * * * *"
        },
        "state": {
            "namespace": "io.kestra.company",
            "flowId": "trigger_tests_backfill_paused",
            "triggerId": "every1min",
            "updatedAt": "2025-04-15T14:34:19Z",
            "disabled": false,
            "locked": false
        }
    }
]

/**
 * Renders the page with `tab` active.
 *
 * The tab is pinned through `triggersDefaultTab` rather than the router alone: `vueRouter`'s
 * `initialRoute` lands asynchronously, so Triggers.vue's `{immediate: true}` watch on
 * `route.params.tab` fires first, sees no tab, and replaces the route with its default — which
 * dragged a `/manage` story back to `/add`. Triggers.vue reads that key inside `<script setup>`
 * (i.e. per instance), so setting it here, before `<Triggers/>` is created, is picked up. It is
 * always written, never only for "manage", because localStorage outlives a story in the shared
 * browser page and would otherwise leak into whichever story runs next.
 */
const Template = (tab) => () => ({
    setup() {
        localStorage.setItem("triggersDefaultTab", tab)

        mockApiRoute("GET /plugins/triggers", {results: triggerPlugins, total: triggerPlugins.length})
        mockApiRoute("GET /triggers/search", {results: triggersData, total: triggersData.length})

        const store = {}
        store.get = async function (uri) {
            if (uri.includes("/distinct-namespaces")) {
                return {
                    data: [
                        "io.kestra.company",
                        "company.team",
                        "io.kestra.plugin",
                        "io.kestra",
                    ]
                }
            }

            // Anything this story doesn't answer itself falls back to the shared table in
            // .storybook/apiMock.js, which reports the route if nothing there covers it either.
            return mockClientFallback("GET", uri)
        }

        store.post = async function (uri, data) {
            return mockClientFallback("POST", uri, data)
        }

        store.put = async function (uri, data) {
            return mockClientFallback("PUT", uri, data)
        }

        setMockClient(store);

        return () =>
            <Triggers />
    }
});

/** The "add" tab (the component's default), showing the trigger catalogue. */
export const Default = {
    render: Template("add"),
    decorators: [vueRouter(ROUTES, {initialRoute: "/add"})],
    play: async ({canvasElement}) => {
        await waitFor(
            () => {
                const text = canvasElement.textContent ?? "";
                expect(text).toContain("Schedule");
                expect(text).toContain("Webhook");
            },
            {timeout: 5000},
        );
    },
}

/**
 * The "manage" tab, which is what `triggersData` was always written for — disabled, locked and
 * backfill-paused rows. Until this story existed the fixture never reached a component: the story
 * only ever rendered the default "add" tab, so TriggersManage never mounted.
 */
export const Manage = {
    render: Template("manage"),
    decorators: [vueRouter(ROUTES, {initialRoute: "/manage"})],
    play: async ({canvasElement}) => {
        await waitFor(
            () => {
                const text = canvasElement.textContent ?? "";
                for (const {state} of triggersData) {
                    expect(text).toContain(state.flowId);
                }
            },
            {timeout: 8000},
        );
    },
}
