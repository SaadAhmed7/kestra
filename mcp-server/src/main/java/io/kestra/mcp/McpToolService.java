package io.kestra.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionStreamingService;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.sse.Event;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

@Singleton
public class McpToolService {
    private final FlowInputOutput flowInputOutput;
    private final DispatchQueueInterface<Execution> executionQueue;
    private final FlowRepositoryInterface flowRepositoryInterface;
    private final FlowToolSchemaMapper flowToolSchemaMapper;
    private final ExecutionStreamingService streamingService;

    private final Duration executionTimeout;

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    public McpToolService(
        FlowInputOutput flowInputOutput,
        DispatchQueueInterface<Execution> executionQueue,
        FlowRepositoryInterface flowRepositoryInterface,
        FlowToolSchemaMapper flowToolSchemaMapper,
        ExecutionStreamingService streamingService,
        @Value("${kestra.mcp.execution-timeout:PT5M}") Duration executionTimeout
    ) {
        this.flowInputOutput = flowInputOutput;
        this.executionQueue = executionQueue;
        this.flowRepositoryInterface = flowRepositoryInterface;
        this.flowToolSchemaMapper = flowToolSchemaMapper;
        this.streamingService = streamingService;
        this.executionTimeout = executionTimeout;
    }


    public List<McpSchema.Tool> listToolSpecsForServer(String tenantId, String namespace, String serverId) {
        return fetchFlowWithMcpToolTrigger(tenantId, namespace, serverId).stream().flatMap(flow -> flow.getTriggers().stream()
                .filter(isMcpTriggerTypeAndEnabledPredicate())
                .map(trigger -> flowToolSchemaMapper.buildTool(flow, (McpToolTrigger) trigger))
            ).toList();
    }

    public Optional<McpSchema.Tool> getSpecTool(String tenantId, String namespace, String serverId, String toolName) {
        return fetchFlowWithMcpToolTrigger(tenantId, namespace, serverId).stream().flatMap(flow ->
            flow.getTriggers().stream()
                .filter(isMcpTriggerTypeAndEnabledPredicate())
                .filter(t -> ((McpToolTrigger) t).getToolName().equals(toolName))
                .map(t -> (McpToolTrigger) t)
                .findFirst()
                .map(trigger -> flowToolSchemaMapper.buildTool(flow, trigger))
                .stream()
        ).findFirst();
    }

    /**
     * Call a tool by name: execute the underlying flow, wait for completion, return outputs.
     */
    public McpSchema.CallToolResult callTool(String tenantId, String namespace, String serverId, McpSchema.Tool tool, Map<String, Object> arguments) {
        Optional<Flow> flowOpt = fetchFlowWithMcpToolTrigger(tenantId, namespace, serverId)
            .stream().filter(
                flow -> flow.getTriggers().stream()
                    .filter(isMcpTriggerTypeAndEnabledPredicate())
                    .anyMatch(
                        filter -> ((McpToolTrigger) filter).getToolName().equals(tool.name())
                    )
            ).findFirst();
        if (flowOpt.isEmpty()) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Flow not found for tool: " + tool.name())
                .isError(true)
                .build();
        }

        Flow flow = flowOpt.get();
        Map<String, Object> inputs = arguments != null ? arguments : Map.of();

        // Create execution
        Execution execution = Execution.newExecution(
            flow,
            null,
            List.of(new Label("system_mcp", "true")),
            Optional.empty()
        );

        // Resolve inputs through FlowInputOutput
        Map<String, Object> resolvedInputs;
        try {
            resolvedInputs = flowInputOutput.readExecutionInputs(flow, execution, inputs);
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Input validation failed: " + e.getMessage())
                .isError(true)
                .build();
        }

        execution = execution.withInputs(resolvedInputs);

        try {
            executionQueue.emit(execution);
        } catch (QueueException e) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Failed to queue execution: " + e.getMessage())
                .isError(true)
                .build();
        }

        // Wait for execution completion
        String subscriberId = UUID.randomUUID().toString();
        final Execution emittedExecution = execution;
        try {
            Execution completed = Flux.<Event<Execution>>create(emitter ->
                    streamingService.registerSubscriber(
                        emittedExecution.getId(),
                        subscriberId,
                        emitter,
                        flow
                    )
                )
                .last()
                .map(Event::getData)
                .timeout(executionTimeout)
                .doFinally(signalType -> streamingService.unregisterSubscriber(emittedExecution.getId(), subscriberId))
                .block();

            if (completed == null) {
                return McpSchema.CallToolResult.builder()
                    .addTextContent("Execution completed but no result returned")
                    .isError(true)
                    .build();
            }

            if (completed.getState().isFailed()) {
                return McpSchema.CallToolResult.builder()
                    .addTextContent("Execution failed with state: " + completed.getState().getCurrent())
                    .isError(true)
                    .build();
            }

            // Return outputs as JSON text content
            Map<String, Object> outputs = completed.getOutputs();
            String outputText;
            if (outputs == null || outputs.isEmpty()) {
                outputText = "Execution completed successfully with no outputs.";
            } else {
                try {
                    outputText = MAPPER.writeValueAsString(outputs);
                } catch (JsonProcessingException e) {
                    outputText = outputs.toString();
                }
            }

            return McpSchema.CallToolResult.builder()
                .addTextContent(outputText)
                .build();
        } catch (Exception e) {
            streamingService.unregisterSubscriber(emittedExecution.getId(), subscriberId);
            return McpSchema.CallToolResult.builder()
                .addTextContent("Execution error: " + e.getMessage())
                .isError(true)
                .build();
        }
    }

    private List<Flow> fetchFlowWithMcpToolTrigger(String tenantId, String namespace, String serverId) {
        return flowRepositoryInterface.find(Pageable.unpaged(), tenantId, null).stream()
            .filter(flow -> flow.getNamespace().equals(namespace))
            .filter(flow ->
                flow.getTriggers().stream().anyMatch(trigger ->
                    trigger.getClass().equals(McpToolTrigger.class) && ((McpToolTrigger) trigger).getMcpServer().equals(serverId)
                )
            ).toList();
    }

    private static Predicate<AbstractTrigger> isMcpTriggerTypeAndEnabledPredicate() {
        return trigger -> trigger.getClass().equals(McpToolTrigger.class) && !trigger.isDisabled();
    }
}
