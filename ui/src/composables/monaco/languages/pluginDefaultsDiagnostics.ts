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
 * @param pluginType the {@code type} of the task/trigger under inspection
 * @param property   the property reported as missing by the schema validator
 * @param defaults   the flow-level plugin defaults
 */
export function pluginDefaultProvidesProperty(
    pluginType: string,
    property: string,
    defaults: FlowPluginDefault[],
): boolean {
    return defaults.some(
        (entry) =>
            (pluginType === entry.type || pluginType.startsWith(entry.type)) &&
            Object.prototype.hasOwnProperty.call(entry.values, property),
    )
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
