import {describe, it, expect} from "vitest"
import {
    parseFlowPluginDefaults,
    pluginDefaultProvidesProperty,
    extractMissingRequiredProperty,
    findEnclosingPluginType,
    filterPluginDefaultMarkers,
} from "../../../../src/composables/monaco/languages/pluginDefaultsDiagnostics"

const FLOW = `id: valid_workflow
namespace: io.kestra.blx

tasks:
  - id: test_utl
    type: io.kestra.plugin.core.http.Request

pluginDefaults:
  - type: io.kestra.plugin.core.http.Request
    values:
      method: "GET"
      uri: https://ready911.com
`

describe("parseFlowPluginDefaults", () => {
    it("should extract declared plugin defaults", () => {
        // When
        const defaults = parseFlowPluginDefaults(FLOW)

        // Then
        expect(defaults).toHaveLength(1)
        expect(defaults[0].type).toBe("io.kestra.plugin.core.http.Request")
        expect(defaults[0].values).toMatchObject({method: "GET", uri: "https://ready911.com"})
    })

    it("should return an empty array when there is no pluginDefaults section", () => {
        expect(parseFlowPluginDefaults("id: f\nnamespace: n\ntasks: []\n")).toEqual([])
    })

    it("should return an empty array for invalid yaml", () => {
        expect(parseFlowPluginDefaults("::: not yaml :::")).toEqual([])
    })

    it("should ignore malformed default entries", () => {
        const source = `pluginDefaults:\n  - type: io.kestra.x\n  - values: {a: 1}\n`
        // entry without values and entry without type are both dropped
        expect(parseFlowPluginDefaults(source)).toEqual([])
    })
})

describe("pluginDefaultProvidesProperty", () => {
    const defaults = parseFlowPluginDefaults(FLOW)

    it("should match on exact type and supplied property", () => {
        expect(
            pluginDefaultProvidesProperty("io.kestra.plugin.core.http.Request", "uri", defaults),
        ).toBe(true)
    })

    it("should match when the default type is a prefix of the plugin type", () => {
        const prefixed = [{type: "io.kestra.plugin.core.http", values: {uri: "x"}}]
        expect(
            pluginDefaultProvidesProperty("io.kestra.plugin.core.http.Request", "uri", prefixed),
        ).toBe(true)
    })

    it("should not match a property the default does not supply", () => {
        expect(
            pluginDefaultProvidesProperty("io.kestra.plugin.core.http.Request", "body", defaults),
        ).toBe(false)
    })

    it("should not match an unrelated plugin type", () => {
        expect(
            pluginDefaultProvidesProperty("io.kestra.plugin.core.log.Log", "uri", defaults),
        ).toBe(false)
    })

    it("should match through an alias resolver when raw types differ", () => {
        // Given a default declared with a legacy alias type
        const aliased = [{type: "io.kestra.core.tasks.http.Request", values: {uri: "x"}}]
        const resolver = (type: string) =>
            type === "io.kestra.core.tasks.http.Request"
                ? "io.kestra.plugin.core.http.Request"
                : type

        // Then the canonical forms match
        expect(
            pluginDefaultProvidesProperty("io.kestra.plugin.core.http.Request", "uri", aliased, resolver),
        ).toBe(true)
        // ...and without the resolver the raw, unequal types do not
        expect(
            pluginDefaultProvidesProperty("io.kestra.plugin.core.http.Request", "uri", aliased),
        ).toBe(false)
    })
})

describe("extractMissingRequiredProperty", () => {
    it("should extract the property name from the language-server message", () => {
        expect(extractMissingRequiredProperty('Missing property "uri".')).toBe("uri")
    })

    it("should be case-insensitive and quote-style tolerant", () => {
        expect(extractMissingRequiredProperty("missing property 'method'")).toBe("method")
    })

    it("should return undefined for unrelated diagnostics", () => {
        expect(extractMissingRequiredProperty('Value is not accepted. Valid values: "GET".')).toBeUndefined()
    })
})

describe("findEnclosingPluginType", () => {
    it("should resolve the enclosing task type for an offset inside the task", () => {
        // Given an offset pointing at the task id value
        const offset = FLOW.indexOf("test_utl")

        // Then
        expect(findEnclosingPluginType(FLOW, offset)).toBe("io.kestra.plugin.core.http.Request")
    })

    it("should return undefined when no typed object encloses the offset", () => {
        const offset = FLOW.indexOf("valid_workflow")
        expect(findEnclosingPluginType(FLOW, offset)).toBeUndefined()
    })
})

describe("filterPluginDefaultMarkers", () => {
    // Maps a (1-based line, 1-based column) position to a character offset in FLOW,
    // mirroring monaco's IModel#getOffsetAt.
    const offsetAt = (lineNumber: number, column: number): number => {
        const lines = FLOW.split("\n")
        let offset = 0
        for (let i = 0; i < lineNumber - 1; i++) {
            offset += lines[i].length + 1
        }
        return offset + (column - 1)
    }

    // All three markers point at the http Request task (line 5: "  - id: test_utl").
    const uriMissing = {message: 'Missing property "uri".', startLineNumber: 5, startColumn: 5}
    const bodyMissing = {message: 'Missing property "body".', startLineNumber: 5, startColumn: 5}
    const typeError = {message: "Incorrect type. Expected \"string\".", startLineNumber: 6, startColumn: 11}

    it("should drop only the missing-required marker covered by a pluginDefault", () => {
        // When
        const kept = filterPluginDefaultMarkers(
            [uriMissing, bodyMissing, typeError],
            FLOW,
            offsetAt,
        )

        // Then `uri` (supplied by the default) is removed; `body` and the type error remain.
        expect(kept).toEqual([bodyMissing, typeError])
    })

    it("should leave markers untouched when the flow has no pluginDefaults", () => {
        const noDefaults = "id: f\nnamespace: n\ntasks:\n  - id: t\n    type: io.kestra.plugin.core.http.Request\n"
        const markers = [{message: 'Missing property "uri".', startLineNumber: 4, startColumn: 5}]

        expect(filterPluginDefaultMarkers(markers, noDefaults, offsetAt)).toEqual(markers)
    })

    it("should suppress via the alias resolver when the default uses an aliased type", () => {
        // Given a flow whose default is declared with a legacy alias type
        const aliasFlow = `id: f
namespace: n

tasks:
  - id: t
    type: io.kestra.plugin.core.http.Request

pluginDefaults:
  - type: io.kestra.core.tasks.http.Request
    values:
      uri: https://x
`
        const aliasOffsetAt = (lineNumber: number, column: number): number => {
            const lines = aliasFlow.split("\n")
            let offset = 0
            for (let i = 0; i < lineNumber - 1; i++) {
                offset += lines[i].length + 1
            }
            return offset + (column - 1)
        }
        const resolver = (type: string) =>
            type === "io.kestra.core.tasks.http.Request"
                ? "io.kestra.plugin.core.http.Request"
                : type
        const markers = [{message: 'Missing property "uri".', startLineNumber: 5, startColumn: 5}]

        // Without the resolver the raw types differ, so the marker is kept...
        expect(filterPluginDefaultMarkers(markers, aliasFlow, aliasOffsetAt)).toEqual(markers)
        // ...with the resolver the canonical types match and the marker is suppressed.
        expect(filterPluginDefaultMarkers(markers, aliasFlow, aliasOffsetAt, resolver)).toEqual([])
    })
})
