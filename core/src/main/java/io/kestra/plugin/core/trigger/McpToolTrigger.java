package io.kestra.plugin.core.trigger;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
@Schema(title = "Expose a flow as an MCP tool.")
public class McpToolTrigger extends AbstractTrigger {
    @NotNull
    private String toolName;

    @NotNull
    private String title;

    @NotNull
    private String toolDescription;

    @NotNull
    private Annotations annotations;

    @NotNull
    private String mcpServer = "default";




    public record Annotations(
        boolean readOnly,
        boolean openWorld,
        boolean destructive,
        boolean idempotent
    ) {}
}
