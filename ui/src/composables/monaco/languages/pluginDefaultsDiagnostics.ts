import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

/**
 * A flow-level plugin default entry. The backend {@code PluginDefaultService} merges the
 * declared {@code values} into every matching task/trigger before a flow runs, so a property
 * supplied here is effectively present even though it is not written on the task itself.
 */
export interface FlowPluginDefault {
    type: string;
    values: Record<string, unknown>;
}

/**
 * Parses the top-level {@code pluginDefaults} section of a flow source.
 *
 * <p>Only flow-level defaults are visible to the editor; namespace/tenant/global defaults live
 * on the backend and are intentionally out of scope here.</p>
 *
 * @param source the raw YAML flow source
 * @return the declared plugin defaults, or an empty array when none/invalid
 */
export function parseFlowPluginDefaults(source: string): FlowPluginDefault[] {
    try {
        const parsed = YAML_UTILS.parse(source, false) as Record<string, unknown> | undefined
        const defaults = parsed?.pluginDefaults
        if (!Array.isArray(defaults)) {
            return []
        }
        return defaults.filter(
            (entry): entry is FlowPluginDefault =>
                !!entry &&
                typeof entry === "object" &&
                typeof (entry as FlowPluginDefault).type === "string" &&
                !!(entry as FlowPluginDefault).values &&
                typeof (entry as FlowPluginDefault).values === "object",
        )
    } catch {
        return []
    }
}

/**
 * Returns {@code true} when a plugin default matching {@code pluginType} supplies {@code property}.
 *
 * <p>Matching mirrors {@code PluginDefaultService#defaults}: a default applies when its {@code type}
 * equals the plugin type, or is a prefix of it (e.g. {@code io.kestra.plugin.core.http} matches
 * {@code io.kestra.plugin.core.http.Request}).</p>
 *
 * <p>When an {@code aliasResolver} is provided, both the plugin type and the default type are also
 * compared in their canonical form, mirroring {@code PluginDefaultService#addAliases} which resolves
 * an alias default to its canonical class before merging. The resolver should map a (possibly aliased)
 * type to its canonical class name, returning the input unchanged when it is not an alias.</p>
 *
 * @param pluginType    the {@code type} of the task/trigger under inspection
 * @param property      the property reported as missing by the schema validator
 * @param defaults      the flow-level plugin defaults
 * @param aliasResolver optional resolver from an alias type to its canonical class name
 */
export function pluginDefaultProvidesProperty(
    pluginType: string,
    property: string,
    defaults: FlowPluginDefault[],
    aliasResolver?: (type: string) => string,
): boolean {
    const resolve = (type: string) => aliasResolver?.(type) ?? type
    const resolvedPluginType = resolve(pluginType)

    return defaults.some((entry) => {
        if (!Object.prototype.hasOwnProperty.call(entry.values, property)) {
            return false
        }
        if (pluginType === entry.type || pluginType.startsWith(entry.type)) {
            return true
        }
        const resolvedEntryType = resolve(entry.type)
        return (
            resolvedPluginType === resolvedEntryType ||
            resolvedPluginType.startsWith(resolvedEntryType)
        )
    })
}

/**
 * Extracts the property name from a JSON-schema "missing required property" diagnostic.
 *
 * <p>The bundled language server emits messages of the form {@code Missing property "uri".};
 * the match is intentionally lenient about surrounding wording and quote style.</p>
 *
 * @param message the diagnostic message
 * @return the missing property name, or {@code undefined} when the message is not a missing-property one
 */
export function extractMissingRequiredProperty(message: string): string | undefined {
    return /missing property\s+["']([^"']+)["']/i.exec(message)?.[1]
}

/**
 * Finds the {@code type} of the closest task/trigger object enclosing the given source offset.
 *
 * <p>Walks from the innermost localized element outward, returning the first object that declares
 * a string {@code type} — matching how the backend applies defaults to any mapping with a type.</p>
 *
 * @param source the raw YAML flow source
 * @param offset the character offset of the diagnostic within the source
 * @return the enclosing plugin type, or {@code undefined} when none can be resolved
 */
export function findEnclosingPluginType(source: string, offset: number): string | undefined {
    try {
        const localized = YAML_UTILS.localizeElementAtIndex(source, offset)
        if (!localized) {
            return undefined
        }
        const candidates = [...(localized.parents ?? []), localized.value]
        for (let i = candidates.length - 1; i >= 0; i--) {
            const candidate = candidates[i]
            if (
                candidate &&
                typeof candidate === "object" &&
                typeof (candidate as Record<string, unknown>).type === "string"
            ) {
                return (candidate as Record<string, unknown>).type as string
            }
        }
    } catch {
        /* ignore parse/localize errors and keep the original marker */
    }
    return undefined
}

/**
 * The minimal shape of a monaco marker needed to decide whether it should be suppressed.
 */
export interface SuppressibleMarker {
    message: string;
    startLineNumber: number;
    startColumn: number;
}

/**
 * Builds an alias-to-canonical plugin type map from the flow schema definitions.
 *
 * <p>The flow schema emits each task/trigger subschema's {@code type} property as an {@code enum}
 * listing the canonical class first followed by its aliases (see {@code JsonSchemaGenerator}). A
 * single-value {@code type} is emitted as {@code const} and contributes no alias. The resulting map
 * lets the marker filter treat a default declared with an alias type as equivalent to the canonical
 * task type, mirroring {@code PluginDefaultService#addAliases}.</p>
 *
 * @param definitions the flow schema {@code definitions} object (keyed by class name)
 * @return a map from each alias type to its canonical class name
 */
export function buildPluginAliasMap(
    definitions: Record<string, unknown> | undefined | null,
): Record<string, string> {
    const map: Record<string, string> = {}
    if (!definitions) {
        return map
    }
    for (const definition of Object.values(definitions)) {
        const typeEnum = (definition as {properties?: {type?: {enum?: unknown}}})
            ?.properties?.type?.enum
        if (!Array.isArray(typeEnum) || typeEnum.length <= 1) {
            continue
        }
        const [canonical, ...aliases] = typeEnum
        if (typeof canonical !== "string") {
            continue
        }
        for (const alias of aliases) {
            if (typeof alias === "string") {
                map[alias] = canonical
            }
        }
    }
    return map
}

/**
 * Returns the subset of {@code markers} that should remain after suppressing
 * "missing required property" diagnostics that are covered by the flow's {@code pluginDefaults}.
 *
 * <p>This is the pure decision logic shared by the monaco marker hook. A marker is dropped only when
 * it is a missing-required-property diagnostic <em>and</em> a flow-level plugin default matching the
 * enclosing task/trigger type actually supplies that property. Every other marker — including genuine
 * missing-required errors with no matching default — is kept untouched.</p>
 *
 * @param markers       the markers currently reported by the YAML schema validator
 * @param source        the raw YAML flow source the markers refer to
 * @param offsetAt      maps a (line, column) marker position to a character offset in {@code source}
 * @param aliasResolver optional resolver from an alias type to its canonical class name
 * @return the markers to keep
 */
export function filterPluginDefaultMarkers<T extends SuppressibleMarker>(
    markers: T[],
    source: string,
    offsetAt: (lineNumber: number, column: number) => number,
    aliasResolver?: (type: string) => string,
): T[] {
    const defaults = parseFlowPluginDefaults(source)
    if (!defaults.length) {
        return markers
    }

    return markers.filter((marker) => {
        const property = extractMissingRequiredProperty(marker.message)
        if (!property) {
            return true
        }
        const pluginType = findEnclosingPluginType(
            source,
            offsetAt(marker.startLineNumber, marker.startColumn),
        )
        if (!pluginType) {
            return true
        }
        // Drop the marker only when a matching default actually supplies the property.
        return !pluginDefaultProvidesProperty(pluginType, property, defaults, aliasResolver)
    })
}
