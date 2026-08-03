import {vueRouter} from "storybook-vue3-router";
import {provide} from "vue";
import {expect, waitFor} from "storybook/test";
import FileExplorer, {FILES_OPEN_TAB_INJECTION_KEY, FILES_CLOSE_TAB_INJECTION_KEY} from "../../../../src/components/inputs/FileExplorer.vue";
import {mockApiRoute} from "../../../../.storybook/apiMock";

// namespacesStore.readDirectory() calls FilesAPI.listNamespaceDirectoryFiles(), a generated SDK
// function that goes through the SDK's own fetch client - so neither the axios instance
// setMockClient() swaps nor vi.mock() of the SDK submodule reaches it. mockApiRoute() registers the
// payload at the fetch layer, which is the seam that does work.
const FILES = [
    {fileName: "directory 1", type: "Directory"},
    {fileName: "directory 2", type: "Directory"},
    {fileName: "animals.txt", type: "File"},
];

const meta = {
    title: "inputs/FileExplorer",
    component: FileExplorer,
    decorators: [
        vueRouter([
            {
                path: "/",
                component: {template: "<div></div>"}
            },
        ])
    ]
}

export default meta;

export const Default = {
    render: () => ({
        setup() {
            mockApiRoute("GET /namespaces/:namespace/files/directory", FILES);

            provide(FILES_OPEN_TAB_INJECTION_KEY, () => {})
            provide(FILES_CLOSE_TAB_INJECTION_KEY, () => {})

            return () => <div style="margin: 1rem;">
                <FileExplorer currentNS="example"/>
            </div>
        }
    }),
    /**
     * Pins the fixture to the rendered tree. Without an assertion this story passed while showing
     * the "No files found" empty state, which is exactly how a dead mock stayed invisible.
     */
    play: async ({canvasElement}) => {
        await waitFor(
            () => {
                const text = canvasElement.textContent ?? "";
                for (const {fileName} of FILES) {
                    expect(text).toContain(fileName);
                }
            },
            {timeout: 5000},
        );
    },
};
