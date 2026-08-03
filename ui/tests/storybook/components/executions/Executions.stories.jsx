import {vueRouter} from "storybook-vue3-router";
import {expect, waitFor} from "storybook/test";
import Executions from "../../../../src/components/executions/Executions.vue";
import {useMiscStore} from "override/stores/misc";
import {useAuthStore} from "override/stores/auth";
import fixture from "./Executions.fixture.json"
import fixtureS from "./Executions-s.fixture.json"
import {setMockClient} from "@kestra-io/kestra-sdk"
import {mockApiRoute, mockClientFallback} from "../../../../.storybook/apiMock";

// executionsStore.findExecutions() calls ExecutionsAPI.searchExecutions(), a generated SDK function
// that goes through the SDK's own fetch client - so neither the axios instance setMockClient() swaps
// nor vi.mock() of the SDK submodule reaches it. mockApiRoute() registers the payload at the fetch
// layer, which is the seam that does work. Both fixtures are already `{results, total}`, the
// endpoint's own response shape.

function getDecorators(data) {
    return [
        () => {
            return {
                setup () {
                    const authStore = useAuthStore()
                    const miscStore = useMiscStore()

                    authStore.user = {
                        id: "123",
                        firstName: "John",
                        lastName: "Doe",
                        email: "john.doe@example.com",
                        isAllowed: () => true,
                        hasAnyActionOnAnyNamespace: () => true,
                    }
                    miscStore.configs = {
                        hiddenLabelsPrefixes: ["system_"]
                    }
                    mockApiRoute("GET /executions/search", data)

                    const axios = {}
                    axios.get = (uri) => mockClientFallback("GET", uri)
                    setMockClient(axios);
                },
                template: "<div style='margin:2rem'><story /></div>"
            }
        },
        vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"}
        },
          {
            path: "/flows/update/:namespace/:id?/:flowId?",
            name: "flows/update",
            component: {template: "<div>updateflows</div>"}
          },{
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
          // computed. Without it vue-router throws "No match for namespaces/update" mid-render and
          // the table body comes up empty even though the rows are in the table's data.
          {
            path: "/namespaces/:id",
            name: "namespaces/update",
            component: {template: "<div>namespace</div>"}
          }
        ], {
            initialRoute: "/executions/123/645"
        }),
    ]
}

// Story configuration
export default {
    title: "Components/Executions",
    component: Executions,
    parameters: {
        layout: "fullscreen"
    }
};


/**
 * Pins the fixture to the rendered table. Without an assertion these stories passed while the table
 * rendered zero rows, which is how the dead `vi.mock` they used to rely on stayed invisible.
 */
function expectRowsRendered(data) {
    return async ({canvasElement}) => {
        await waitFor(
            () => {
                const text = canvasElement.textContent ?? "";
                expect(text).toContain(data.results[0].flowId);
                expect(text).toContain(data.results[0].namespace);
            },
            {timeout: 8000},
        );
    };
}

// Stories
export const SmallData = {
    decorators: getDecorators(fixtureS),
    args: {
        hidden: [],
        statuses: [],
        isReadOnly: false,
        embed: true,
        topbar: false,
        filter: false
    },
    play: expectRowsRendered(fixtureS),
};

export const BiggerData = {
    decorators: getDecorators(fixture),
    args: {
        hidden: [],
        statuses: [],
        topbar: false,
        filter: false
    },
    play: expectRowsRendered(fixture),
};