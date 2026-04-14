package io.kestra.core.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;

public record McpSession(
    String tenantId,
    String namespace,
    String serverId,
    String sessionId,
    String sseNode,
    String userId
) implements HasUID {

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(tenantId, sessionId);
    }
}
