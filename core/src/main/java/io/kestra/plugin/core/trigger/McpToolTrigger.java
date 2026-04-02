package io.kestra.plugin.core.trigger;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
public class McpToolTrigger extends AbstractTrigger {
    @NotNull
    private final String toolName;

    @NotNull
    private final String title;

    @NotNull
    private final String toolDescription;

    @NotNull
    private final Annotations annotations;

    @NotNull
    private final String mcpServer = "default";




    public record Annotations(
        boolean readOnly,
        boolean openWorld,
        boolean destructive,
        boolean idempotent
    ) {}
}
