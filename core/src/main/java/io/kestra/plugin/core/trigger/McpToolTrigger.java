package io.kestra.plugin.core.trigger;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;


@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor
@Plugin
@Schema(title = "Expose a flow as an MCP tool.")
public class McpToolTrigger extends AbstractTrigger implements TriggerOutput<McpToolTrigger.Output> {
    @NotNull
    private String toolName;

    @NotNull
    private String title;

    @NotNull
    private String toolDescription;

    @Builder.Default
    private Annotations annotations = new Annotations(false, true, true, false);

    @NotNull
    @Builder.Default
    private String mcpServer = "default";

    public Execution evaluate(
        Flow flow,
        Map<String, Object> input,
        Map<String, Object> additionalInputs
    ) {
        return Execution.builder()
            .inputs(input)
            .flowId(flow.getId())
            .state(new State())
            .id(IdUtils.create())
            .flowRevision(flow.getRevision())
            .namespace(flow.getNamespace())
            .tenantId(flow.getTenantId())
            .kind(ExecutionKind.NORMAL)
            .trigger(ExecutionTrigger.of(
                this,
                (io.kestra.core.models.tasks.Output) new McpToolTrigger.Output(additionalInputs)
            ))
            .build();
    }

    public static class Output extends HashMap<String, Object> implements io.kestra.core.models.tasks.Output  {
        public Output(Map<String, Object> map) {
            super(map);
        }
    }


    public record Annotations(
        boolean readOnly,
        boolean openWorld,
        boolean destructive,
        boolean idempotent
    ) {}
}
