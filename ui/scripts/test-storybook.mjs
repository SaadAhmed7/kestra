#!/usr/bin/env node
// `test:storybook` is the script `kestra-io/actions`' frontend-tests workflow
// actually invokes (`npm run test:storybook -- --coverage`) — there is no CI
// job matrix we control from this repo to shard across parallel jobs.
//
// Coverage-v8 keeps precise per-script coverage in the browser's renderer
// process for the whole run. Across the full story set that grows until the
// renderer is OOM-killed (surfaces as "Browser connection was closed while
// running tests"). Running the suite as several smaller vitest processes
// instead of one long-lived one bounds how much a single renderer
// accumulates. This script does that sequentially, in a single job, then
// merges the per-shard reports with merge-coverage.mjs (vitest's own
// `--merge-reports --coverage` heap-corrupts on the v8 remap step when
// merging shards — see that script for details).
//
// Without --coverage there's nothing accumulating in the renderer across the
// whole run, so there's no need to shard: this runs the project directly.
import {spawnSync} from "node:child_process"
import {rmSync} from "node:fs"

const SHARD_COUNT = Number(process.env.STORYBOOK_TEST_SHARDS) || 4

const args = process.argv.slice(2)
const coverageIndex = args.indexOf("--coverage")
const wantsCoverage = coverageIndex !== -1
if (wantsCoverage) {
    args.splice(coverageIndex, 1)
}

function run(vitestArgs) {
    return spawnSync("npx", ["vitest", "run", "--project=storybook", ...vitestArgs, ...args], {stdio: "inherit"}).status ?? 1
}

if (!wantsCoverage) {
    process.exit(run([]))
}

const shardsDir = "coverage/.storybook-shards"
rmSync(shardsDir, {recursive: true, force: true})

for (let shard = 1; shard <= SHARD_COUNT; shard++) {
    const status = run([
        `--shard=${shard}/${SHARD_COUNT}`,
        "--coverage",
        `--coverage.reportsDirectory=${shardsDir}/shard-${shard}`,
        "--coverage.reporter=json",
    ])
    if (status !== 0) {
        process.exit(status)
    }
}

process.exit(spawnSync("node", ["scripts/merge-coverage.mjs", shardsDir, "coverage"], {stdio: "inherit"}).status ?? 1)
