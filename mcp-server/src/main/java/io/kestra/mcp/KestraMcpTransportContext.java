package io.kestra.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@EqualsAndHashCode
public class KestraMcpTransportContext implements McpTransportContext {
    private final String tenantId;
    private final String namespace;
    private final String serverId;
    private final String userId;
    private String sessionId;

    @Override
    public Object get(String key) {
        return Map.of(
            "tenantId", tenantId,
            "namespace", namespace,
            "serverId", serverId,
            "sessionId", sessionId,
            "userId", userId
        ).get(key);
    }
}
