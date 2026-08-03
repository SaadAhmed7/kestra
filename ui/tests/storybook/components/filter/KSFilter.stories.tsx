import {vueRouter} from "storybook-vue3-router";
import type {Meta, StoryObj} from "@storybook/vue3";
import {expect, waitFor} from "storybook/test";
import {useAuthStore} from "override/stores/auth";
import {useMiscStore} from "override/stores/misc";
import {useNamespacesStore} from "override/stores/namespaces";
import fixture from "../executions/Executions.fixture.json";
import Executions from "../../../../src/components/executions/Executions.vue";
import {mockApiRoute} from "../../../../.storybook/apiMock";

// executionsStore.findExecutions() calls ExecutionsAPI.searchExecutions(), a generated SDK function
// that goes through the SDK's own fetch client - so neither the axios instance setMockClient() swaps
// nor vi.mock() of the SDK submodule reaches it. mockApiRoute() registers the handler at the fetch
// layer, which is the seam that does work, and hands it the real request query so paging and
// filtering are driven by what the component actually sends.

const SEARCHABLE_FIELDS = ["id", "namespace", "flowId"] as const;
const LABEL_FILTER_PATTERN = /filters\[labels]\[(\w+)]\[(.+)]/;

const toArray = (value: any) => Array.isArray(value)
    ? value
    : value.split(",");

/**
 * Keyed by the flat `filters[field][OPERATION]` query keys the component puts on the wire.
 * A key with no entry here is ignored rather than treated as "match nothing", so an unemulated
 * filter degrades to "no narrowing" instead of an empty table.
 */
const FILTER_MAP: {[key: string]: (e: any, value: any) => boolean} = {
    "filters[namespace][IN]": (e, value) => toArray(value).includes(e.namespace),
    "filters[namespace][NOT_IN]": (e, value) => !toArray(value).includes(e.namespace),
    "filters[namespace][CONTAINS]": (e, value) => e.namespace?.toLowerCase().includes(value.toLowerCase()),
    "filters[flowId][EQUALS]": (e, value) => e.flowId?.toLowerCase() === value.toLowerCase(),
    "filters[flowId][NOT_EQUALS]": (e, value) => e.flowId?.toLowerCase() !== value.toLowerCase(),
    "filters[flowId][CONTAINS]": (e, value) => e.flowId?.toLowerCase().includes(value.toLowerCase()),
    "filters[state][IN]": (e, value) => toArray(value).includes(e.state?.current),
    "filters[state][NOT_IN]": (e, value) => !toArray(value).includes(e.state?.current),
    "filters[kind][EQUALS]": (e, value) => e.kind === value,
    "filters[scope][EQUALS]": (e, value) => e.scope === value,
    "filters[scope][NOT_EQUALS]": (e, value) => e.scope !== value,
    "filters[childFilter][EQUALS]": (e, value) => e.childFilter === value,
    "filters[triggerExecutionId][EQUALS]": (e, value) => e.triggerExecutionId === value,
    "filters[triggerExecutionId][NOT_EQUALS]": (e, value) => e.triggerExecutionId !== value,
    "filters[timeRange][EQUALS]": () => true,
};

const hasLabel = (e: any, key: string, value: string) =>
    e.labels?.some((l: any) => l.key === key && l.value === value);

function filterExecutions(executions: any[], params: Record<string, string>): any[] {
    return Object.entries(params).reduce((filtered, [key, value]) => {
        if (!value) return filtered;

        if (key === "filters[q][EQUALS]") {
            return filtered.filter((e: any) =>
                SEARCHABLE_FIELDS.some(field =>
                    e[field]?.toLowerCase().includes(value.toLowerCase())
                )
            );
        }

        if (FILTER_MAP[key]) {
            return filtered.filter(e => FILTER_MAP[key](e, value));
        }

        if (key.startsWith("filters[labels]")) {
            const match = key.match(LABEL_FILTER_PATTERN);
            if (!match) return filtered;

            return filtered.filter(e =>
                match[1] === "EQUALS"
                    ? hasLabel(e, match[2], value)
                    : !hasLabel(e, match[2], value)
            );
        }

        return filtered;
    }, [...executions]);
}

const getNamespaces = (data: any[]): string[] => (
    Array.from(new Set(data
        .map(item => item.namespace).filter(Boolean)))
        .sort()
);

const MOCK_USER = {
    isAllowed: () => true,
    hasAnyActionOnAnyNamespace: () => true,
} as any;

const MOCK_CONFIGS = {
    hiddenLabelsPrefixes: ["system_"],
    edition: "OSS"
} as any;

const ROUTER_ROUTES = [
    {
        path: "/",
        name: "home",
        component: {template: "<div>home</div>"}
    },
    {
        path: "/flows/update/:namespace/:id?/:flowId?",
        name: "flows/update",
        component: {template: "<div>updateflows</div>"}
    }, {
        path: "/executions/update/:namespace/:id?/:flowId?",
        name: "executions/update",
        component: {template: "<div>executions</div>"}
    },
    {
        path: "/executions/:id?/:flowId?",
        name: "executions/list",
        component: {template: "<div>executions</div>"}
    },
    // Every row links its namespace through KsEntityLink, which resolves this route inside a
    // computed. Without it vue-router throws "No match for namespaces/update" mid-render and the
    // table body comes up empty even though the rows are in the table's data.
    {
        path: "/namespaces/:id",
        name: "namespaces/update",
        component: {template: "<div>namespace</div>"}
    }
];

function getDecorators(data: any[]) {
    const FIXTURE_NAMESPACES = getNamespaces(data);

    return [
        () => ({
            setup() {
                useAuthStore().user = MOCK_USER;
                useMiscStore().configs = MOCK_CONFIGS;
                useNamespacesStore().loadAutocomplete = () => Promise.resolve(FIXTURE_NAMESPACES);

                mockApiRoute("GET /executions/search", ({query}: {query: URLSearchParams}) => {
                    const page = Number(query.get("page")) || 1;
                    const size = Number(query.get("size")) || 25;
                    const filtered = filterExecutions(data, Object.fromEntries(query.entries()));
                    const start = (page - 1) * size;
                    return {results: filtered.slice(start, start + size), total: filtered.length};
                });
            },
            template: "<div style='margin:2rem'><story /></div>"
        }),
        vueRouter(ROUTER_ROUTES, {initialRoute: "/executions/123/645"}),
    ];
}

const meta: Meta<typeof Executions> = {
    title: "Components/Filter/KSFilter",
    component: Executions,
    parameters: {layout: "fullscreen"}
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    decorators: getDecorators(fixture.results),
    args: {embed: false, topbar: false, filter: true, visibleCharts: false},
    /**
     * Pins the fixture to the rendered table. Without an assertion this story passed while the table
     * rendered zero rows, which is how the dead `vi.mock` it used to rely on stayed invisible.
     */
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        await waitFor(
            () => {
                const text = canvasElement.textContent ?? "";
                expect(text).toContain(fixture.results[0].flowId);
                expect(text).toContain(fixture.results[0].namespace);
            },
            {timeout: 8000},
        );
    },
};
