package io.kestra.mcp;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Null;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class McpSessionFactory {

    @Inject
    SessionProxyRepository sessionProxyRepository;

    public KestraMcpTransportContext build(String tenantId, String namespace, String serverId, @Nullable String sessionId) {
        return KestraMcpTransportContext.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .serverId(serverId)
            .sessionId(sessionId != null ? sessionId : UUID.randomUUID().toString())
            .userId(null)
            .build();
    }
}
