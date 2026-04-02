package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.kestra.mcp.McpServerFactory;
import io.modelcontextprotocol.spec.McpSchema;
import io.kestra.core.tenant.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller("/api/v1/{tenant}/namespace/{namespace}/mcp/{server}")
public class McpToolController {
    private final TenantService tenantService;

    private static final ObjectMapper MAPPER = io.kestra.core.serializers.JacksonMapper.ofJson(false);

    private io.kestra.mcp.McpToolService mcpService;

    private McpServerFactory mcpServerFactory;

    public McpToolController(TenantService tenantService, McpServerFactory mcpServerFactory) {
        this.tenantService = tenantService;
        this.mcpServerFactory = mcpServerFactory;
    }


    // SSE session management
    private final ConcurrentHashMap<String, FluxSink<Event<String>>> sseSessions = new ConcurrentHashMap<>();

    /**
     * Streamable HTTP transport: single POST endpoint for all JSON-RPC requests.
     * Accepts a single JSON-RPC message or a batch (JSON array) per the MCP spec.
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> handleStreamableHttp(
        @PathVariable String tenant,
        @PathVariable String namespace,
        @PathVariable String server,
        @Body Object rawBody
    ) {
        ensureMcpEnabled();
        String tenantId = tenantService.resolveTenant();

        if (rawBody instanceof List<?> batch) {
            return handleBatch(tenantId, batch);
        }

        if (rawBody instanceof Map<?, ?> rawRequest) {
            return handleSingleMessage(tenantId, namespace, server, rawRequest);
        }

        return HttpResponse.badRequest();
    }

    /**
     * GET on MCP endpoint — spec requires either SSE stream or 405.
     * We return 405 since server-initiated messages use the /sse sub-path.
     */
    @Get(produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> handleGet(@PathVariable String tenant) {
        ensureMcpEnabled();
        return HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * DELETE on MCP endpoint — spec allows 405 for servers that don't support session termination.
     */
    @Delete(produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> handleDelete(@PathVariable String tenant) {
        ensureMcpEnabled();
        return HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<?> handleSingleMessage(String tenantId, String namespace, String serverId,  Map<?, ?> rawRequest) {
        Object id = rawRequest.get("id");
        String method = (String) rawRequest.get("method");

        if (id == null) {
            // Notification — spec: return 202 Accepted with no body
            McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification("2.0", method, rawRequest.get("params"));
            mcpServerFactory.handleNotification(notification);
            return HttpResponse.status(HttpStatus.ACCEPTED);
        }

        // Request
        McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest("2.0", method, id, rawRequest.get("params"));
        McpSchema.JSONRPCResponse response = mcpServerFactory.handleRequest(tenantId, namespace, serverId, request);
        return HttpResponse.ok(response);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<?> handleBatch(String tenantId, List<?> batch) {
        List<McpSchema.JSONRPCResponse> responses = new ArrayList<>();
        boolean hasRequests = false;

        for (Object element : batch) {
            if (!(element instanceof Map<?, ?> rawRequest)) {
                continue;
            }

            Object id = rawRequest.get("id");
            String method = (String) rawRequest.get("method");

            if (id == null) {
                // Notification
                McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification("2.0", method, rawRequest.get("params"));
                mcpServerFactory.handleNotification(notification);
            } else {
                // Request
                hasRequests = true;
                McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest("2.0", method, id, rawRequest.get("params"));
                responses.add(mcpServerFactory.handleRequest(tenantId, request));
            }
        }

        if (!hasRequests) {
            // Batch of only notifications — spec: return 202 Accepted with no body
            return HttpResponse.status(HttpStatus.ACCEPTED);
        }

        return HttpResponse.ok(responses);
    }

    /**
     * SSE transport: open an SSE stream and send the endpoint event.
     */
    @Get(uri = "/sse", produces = MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<String>> handleSseConnect(
        @PathVariable String tenant,
        HttpRequest<?> request
    ) {
        ensureMcpEnabled();
        String sessionId = UUID.randomUUID().toString();

        return Flux.<Event<String>>create(emitter -> {
            sseSessions.put(sessionId, emitter);
            emitter.onDispose(() -> sseSessions.remove(sessionId));

            // Build the messages endpoint URL relative to the request
            String baseUrl = request.getUri().toString().replace("/sse", "");
            String messagesUrl = baseUrl + "/messages?sessionId=" + sessionId;
            emitter.next(Event.of(messagesUrl).name("endpoint"));
        });
    }

    /**
     * SSE transport: receive JSON-RPC messages for an SSE session.
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/messages", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> handleSseMessage(
        @PathVariable String tenant,
        @QueryValue String sessionId,
        @Body Map<String, Object> rawRequest
    ) {
        ensureMcpEnabled();

        FluxSink<Event<String>> sink = sseSessions.get(sessionId);
        if (sink == null) {
            return HttpResponse.notFound();
        }

        String tenantId = tenantService.resolveTenant();
        Object id = rawRequest.get("id");
        String method = (String) rawRequest.get("method");

        if (id == null) {
            // Notification
            McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification("2.0", method, rawRequest.get("params"));
            mcpServerFactory.handleNotification(notification);
        } else {
            // Request — dispatch and send response via SSE
            McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest("2.0", method, id, rawRequest.get("params"));
            McpSchema.JSONRPCResponse response = mcpServerFactory.handleRequest(tenantId, namesp, request);

            try {
                String json = MAPPER.writeValueAsString(response);
                sink.next(Event.of(json).name("message"));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize MCP response", e);
            }
        }

        // Return accepted for the POST
        return HttpResponse.accepted();
    }

//    /**
//     * Listens for Tool CRUD events and pushes {@code notifications/tools/list_changed}
//     * to all connected SSE sessions so that MCP clients refresh their tool list.
//     */
//    @EventListener
//    public void onToolChanged(io.kestra.core.events.CrudEvent<Tool> event) {
//        notifyToolsChanged();
//    }

    /**
     * Sends a {@code notifications/tools/list_changed} JSON-RPC notification
     * to every active SSE session.
     */
    private void notifyToolsChanged() {
        if (sseSessions.isEmpty()) {
            return;
        }

        McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification(
            "2.0", "notifications/tools/list_changed", null
        );

        String json;
        try {
            json = MAPPER.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tools/list_changed notification", e);
            return;
        }

        log.debug("Broadcasting tools/list_changed to {} SSE session(s)", sseSessions.size());
        sseSessions.forEach((sessionId, sink) -> {
            try {
                sink.next(Event.of(json).name("message"));
            } catch (Exception e) {
                log.warn("Failed to send tools/list_changed to session {}", sessionId, e);
            }
        });
    }

    private void ensureMcpEnabled() {
        //Todo: implement enabled for mcp server
//        String tenantId = tenantService.resolveTenant();
//        if (!mcpService.isMcpEnabled(tenantId)) {
//            throw new HttpStatusException(io.micronaut.http.HttpStatus.NOT_FOUND, "MCP server is not enabled");
//        }
    }
}
