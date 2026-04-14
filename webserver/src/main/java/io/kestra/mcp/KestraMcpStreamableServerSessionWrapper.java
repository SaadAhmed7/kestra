package io.kestra.mcp;

import io.modelcontextprotocol.server.McpNotificationHandler;
import io.modelcontextprotocol.server.McpRequestHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

class KestraMcpStreamableServerSessionWrapper extends McpStreamableServerSession {
    public KestraMcpStreamableServerSessionWrapper(String id, McpSchema.ClientCapabilities clientCapabilities, McpSchema.Implementation clientInfo, Duration requestTimeout, Map<String, McpRequestHandler<?>> requestHandlers, Map<String, McpNotificationHandler> notificationHandlers, Supplier<Mono<Void>> onClose) {
        super(id, clientCapabilities, clientInfo, requestTimeout, requestHandlers, notificationHandlers, onClose);
    }

    public KestraMcpStreamableServerSessionWrapper(String id, McpSchema.ClientCapabilities clientCapabilities, McpSchema.Implementation clientInfo, Duration requestTimeout, Map<String, McpRequestHandler<?>> requestHandlers, Map<String, McpNotificationHandler> notificationHandlers) {
        super(id, clientCapabilities, clientInfo, requestTimeout, requestHandlers, notificationHandlers);
    }
}
