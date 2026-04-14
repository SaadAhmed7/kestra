package io.kestra.core.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.queues.event.KeyedDispatchEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpSessionNotificationEvent(
    String sseNode,
    String tenantId,
    String namespace,
    String serverId,
    String sessionId,
    Object response,
    String method,
    Object params
) implements KeyedDispatchEvent {

    public static McpSessionNotificationEvent response(
        String sseNode, String tenantId, String namespace, String serverId, String sessionId,
        Object response
    ) {
        return new McpSessionNotificationEvent(sseNode, tenantId, namespace, serverId, sessionId, response, null, null);
    }

    public static McpSessionNotificationEvent notification(
        String sseNode, String tenantId, String namespace, String serverId, String sessionId,
        String method, Object params
    ) {
        return new McpSessionNotificationEvent(sseNode, tenantId, namespace, serverId, sessionId, null, method, params);
    }

    @Override
    public String key() {
        return sseNode;
    }
}
