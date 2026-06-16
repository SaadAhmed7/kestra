import {describe, it, expect} from "vitest"
import {
    parseFlowPluginDefaults,
    pluginDefaultProvidesProperty,
    extractMissingRequiredProperty,
    findEnclosingPluginType,
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
