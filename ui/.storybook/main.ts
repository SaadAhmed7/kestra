import path from "path"
import {fileURLToPath} from "url"
import {mergeConfig} from "vite"
import type {StorybookConfig} from "@storybook/vue3-vite"

const config: StorybookConfig = {
    stories: [
        "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)",
    ],
    addons: ["@storybook/addon-themes", "@storybook/addon-vitest"],
    framework: {
        name: "@storybook/vue3-vite",
        options: {},
    },
    async viteFinal(viteConfig) {
        const __dirname = path.dirname(fileURLToPath(import.meta.url))
        const {default: viteJSXPlugin} = await import("@vitejs/plugin-vue-jsx")

        viteConfig.plugins = [
            ...(viteConfig.plugins ?? []),
            viteJSXPlugin(),
        ]

        if (viteConfig.resolve) {
            const AliasConfig = [
                ...(viteConfig.resolve.alias as any[]),
                {find: "override", replacement: path.resolve(__dirname, "../src/override/")},
            ]
            viteConfig.resolve.alias = AliasConfig
        }

        // Silence "Sourcemap for X points to a source file outside its package"
        // warnings from node_modules — cross-package scss sourcemap references.
        if (viteConfig.customLogger) {
            const isElementPlusSourcemapWarning = (msg: string) =>
                /sourcemap/i.test(msg) && msg.includes("points to a source file outside its package") && msg.includes("node_modules")
            const origWarn = viteConfig.customLogger.warn.bind(viteConfig.customLogger)
            const origWarnOnce = viteConfig.customLogger.warnOnce.bind(viteConfig.customLogger)
            viteConfig.customLogger.warn = (msg, opts) => { if (!isElementPlusSourcemapWarning(msg)) origWarn(msg, opts) }
            viteConfig.customLogger.warnOnce = (msg, opts) => { if (!isElementPlusSourcemapWarning(msg)) origWarnOnce(msg, opts) }
        }

        return mergeConfig(viteConfig, {
            define: {"process.env": {}},
            optimizeDeps: {
                // Vite's esbuild dependency scan only crawls from configured entry
                // points at server startup. Story files aren't referenced from any
                // app entry, so a package first imported by a story (rather than by
                // the app itself) is only discovered lazily, mid-test-run, the first
                // time that story loads. Discovering a new dependency triggers Vite to
                // re-run the optimizer and reload the dev server, which drops whatever
                // browser session vitest's addon-vitest runner had open — surfacing as
                // "Browser connection was closed while running tests" / "[birpc] rpc is
                // closed" in CI (this cache is gitignored, so it never survives from a
                // previous run there, unlike local dev). Feeding every story file (plus
                // the shared preview) as scan entries makes the optimizer pre-bundle
                // everything up front instead of triggering a mid-run reload.
                // See https://github.com/storybookjs/storybook/issues/33347#issuecomment-4511096054
                entries: [
                    path.resolve(__dirname, "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)"),
                    path.resolve(__dirname, "./preview.jsx"),
                ],
            },
        })
    },
}
export default config
