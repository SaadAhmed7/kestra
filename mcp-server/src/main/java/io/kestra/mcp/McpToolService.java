package io.kestra.mcp;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionStreamingService;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.sse.Event;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class McpToolService {
    private final DispatchQueueInterface<Execution> executionQueue;
    private final FlowRepositoryInterface flowRepositoryInterface;
    private final FlowToolSchemaMapper flowToolSchemaMapper;
    private final ExecutionStreamingService streamingService;
    private final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;
    private final Duration executionTimeout;

    private final LoadingCache<ToolHandlerCacheKey, McpServerFeatures.AsyncToolSpecification> asyncToolSpecificationCache = Caffeine.newBuilder()
        .weakValues()
        .build((ToolHandlerCacheKey key) -> buildAsyncToolSpecification(key.flow(), key.toolTrigger()));

    // Todo: find out what info we should be handing back to the user in this situation.
    private static final McpSchema.CallToolResult FLOW_ERROR_CALL_TOOL_RESULT = McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("Failed to execute flow")
        .build();

    public McpToolService(
        DispatchQueueInterface<Execution> executionQueue,
        FlowRepositoryInterface flowRepositoryInterface,
        FlowToolSchemaMapper flowToolSchemaMapper,
        ExecutionStreamingService streamingService, ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher,
        @Value("${kestra.mcp.execution-timeout:PT5M}") Duration executionTimeout
        ) {
        this.executionQueue = executionQueue;
        this.flowRepositoryInterface = flowRepositoryInterface;
        this.flowToolSchemaMapper = flowToolSchemaMapper;
        this.streamingService = streamingService;
        this.eventPublisher = eventPublisher;
        this.executionTimeout = executionTimeout;
    }


    public List<McpServerFeatures.AsyncToolSpecification> listToolSpecsForServer(String tenantId, String namespace, String serverId) {
        return fetchFlowWithMcpToolTrigger(tenantId, namespace, serverId).stream().flatMap(flow -> flow.getTriggers().stream()
                .filter(isMcpTriggerTypeAndEnabledPredicate())
                .map(trigger -> getAsyncToolSpecification(flow, (McpToolTrigger) trigger))
            ).toList();
    }

    private McpServerFeatures.AsyncToolSpecification getAsyncToolSpecification(Flow flow, McpToolTrigger toolTrigger) {
        log.debug("Getting AsyncToolSpecification for flow: {}/{}/{}, trigger: {}", flow.getTenantId(), flow.getNamespace(), flow.getId(), toolTrigger.getId());
        return asyncToolSpecificationCache.get(new ToolHandlerCacheKey(flow, toolTrigger));
    }

    private McpServerFeatures.AsyncToolSpecification buildAsyncToolSpecification(Flow flow, McpToolTrigger toolTrigger) {
        return new McpServerFeatures.AsyncToolSpecification(
            flowToolSchemaMapper.buildTool(flow, toolTrigger),
            buildCallHandler(flow, toolTrigger)
        );
    }

    private BiFunction<McpAsyncServerExchange, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> buildCallHandler(
        Flow flow,
        McpToolTrigger toolTrigger
    ) {
        final List<String> defaultsInputs = ListUtils.emptyOnNull(flow.getInputs())
            .stream().map(Input::getId).toList();

        return (exchange, request) -> {
            Map<String, Object> input = request.arguments().entrySet().stream()
                .filter(entry -> defaultsInputs.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Map<String, Object> additionalInputs = request.arguments().entrySet().stream()
                .filter(entry -> !defaultsInputs.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            return runFlowForMcpTask(flow, input, additionalInputs, toolTrigger)
                .map(execution -> McpSchema.CallToolResult.builder()
                    .structuredContent(execution.getOutputs() != null && execution.getState().isSuccess() ? execution.getOutputs() : Map.of())
                    .isError(!execution.getState().isSuccess())
                    .build())
                .onErrorReturn(Exception.class, FLOW_ERROR_CALL_TOOL_RESULT);
        };
    }

    private Mono<Execution> runFlowForMcpTask(
        Flow flow,
        Map<String, Object> input,
        Map<String, Object> additionalInputs,
        McpToolTrigger toolTrigger
    ) {
        Execution execution = toolTrigger.evaluate(flow, input, additionalInputs);

        try {
            executionQueue.emit(execution);
            eventPublisher.publishEvent(new CrudEvent<>(execution, null, CrudEventType.CREATE));


            String subscriberId = UUID.randomUUID().toString();
            return Flux.<Event<Execution>>create(emitter -> streamingService.registerSubscriber(
                    execution.getId(),
                    subscriberId,
                    emitter,
                    flow
                ))
                .timeout(executionTimeout)
                .last()
                .map(Event::getData)
                .doFinally(signalType -> streamingService.unregisterSubscriber(execution.getId(), subscriberId));
        } catch (QueueException e) {
            return Mono.error(e);
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

    private record ToolHandlerCacheKey(
        Flow flow,
        McpToolTrigger toolTrigger
    ) {
        @Override
        public boolean equals(Object o) {

            if (!(o instanceof ToolHandlerCacheKey(Flow flow1, McpToolTrigger trigger))) return false;
            return Objects.equals(flow.getTenantId(), flow1.getTenantId())
                && Objects.equals(flow.getNamespace(), flow1.getNamespace())
                && Objects.equals(flow.getId(), flow1.getId())
                && Objects.equals(flow.getRevision(), flow1.getRevision())
                && Objects.equals(flow.getUpdated(), flow1.getUpdated())
                && Objects.equals(toolTrigger, trigger);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getRevision(), flow.getUpdated(), toolTrigger);
        }
    }
}
