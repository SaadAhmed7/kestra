import {vueRouter} from "storybook-vue3-router";
import Logs from "../../../../src/components/executions/Logs.vue";
import {useExecutionsStore} from "../../../../src/stores/executions";
import {useAuthStore} from "override/stores/auth";
import {useAxios} from "../../../../src/utils/axios";
import {storageKeys} from "../../../../src/utils/constants";
import fixture from "./Logs.fixture.json";

/**
 * Reproducer for: execution logs not showing in Default view without page refresh.
 *
 * Root cause: the `followedExecution` watcher in TaskRunDetails was async and
 * awaited `loadFlowForExecution` before calling `loadLogs`, meaning logs were
 * never fetched if the flow load failed or was delayed.
 *
 * Additionally, `Logs.vue` had no initial `loadLogs()` call for the temporal view.
 */

const ROUTES = [
    {path: "/", name: "home", component: {template: "<div />"}},
    {
        path: "/:tenant?/executions/:namespace/:flowId/:id/:tab?",
        name: "executions/update",
        component: {template: "<div />"},
    },
    {
        path: "/:tenant?/flows/update/:namespace/:id/:tab?",
        name: "flows/update",
        component: {template: "<div />"},
    },
    {
        path: "/:tenant?/executions",
        name: "executions/list",
        component: {template: "<div />"},
    },
];

function getDecorators({logsViewType = "false"} = {}) {
    return [
        () => ({
            setup() {
                const authStore = useAuthStore();
                authStore.user = {
                    id: "123",
                    firstName: "John",
                    lastName: "Doe",
                    email: "john.doe@example.com",
                    isAllowed: () => true,
                    hasAnyActionOnAnyNamespace: () => true,
                };

                // Pre-load the execution into the store (simulates ExecutionRoot having
                // already received the execution via SSE before the Logs tab renders)
                const executionsStore = useExecutionsStore();
                executionsStore.execution = fixture.execution;
                executionsStore.flow = fixture.flow;

                // Mock all API calls
                const axios = useAxios();
                axios.get = (url) => {
                    if (url.includes("/logs/")) {
                        return Promise.resolve({data: fixture.logs});
                    }
                    if (url.includes("/executions/flows/") || url.includes("/flow")) {
                        return Promise.resolve({data: fixture.flow});
                    }
                    if (url.includes("/dependencies")) {
                        return Promise.resolve({data: {count: 0, flows: []}});
                    }
                    return Promise.resolve({data: []});
                };

                localStorage.setItem(storageKeys.LOGS_VIEW_TYPE, logsViewType);
            },
            template: "<div style='margin: 1rem; height: 90vh; display: flex; flex-direction: column'><story /></div>",
        }),
        vueRouter(ROUTES, {
            initialRoute: `/executions/company.team/hello-world/${fixture.execution.id}/logs`,
        }),
    ];
}

export default {
    title: "Components/Executions/Logs",
    component: Logs,
    parameters: {
        layout: "fullscreen",
    },
};

/**
 * Default view (compact, grouped by task). Logs must appear without any user
 * interaction — no refresh, no button click.
 *
 * Before the fix this story showed an empty log panel.
 */
export const DefaultView = {
    decorators: getDecorators({logsViewType: "false"}),
};

/**
 * Temporal view (raw, timestamp-ordered). Logs must also appear on initial render.
 *
 * Before the fix, logs only appeared if the log-level query param changed after mount.
 */
export const TemporalView = {
    decorators: getDecorators({logsViewType: "true"}),
};
