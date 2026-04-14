package io.kestra.core.mcp.repositories;

import io.kestra.core.mcp.models.McpSession;

import java.util.List;
import java.util.Optional;

public interface McpSessionRepositoryInterface {

    Optional<McpSession> find(String tenantId, String namespace, String serverId, String sessionId);

    List<McpSession> findByServerId(String tenantId, String namespace, String serverId);

    /**
     * Returns all sessions hosted on the given SSE node, across all tenants.
     * Used for cross-server routing in horizontally-scaled deployments.
     *
     * @param sseNode the node identifier
     * @return all sessions currently pinned to that node
     */
    List<McpSession> findBySseNode(String sseNode);

    McpSession save(McpSession session);

    Optional<McpSession> delete(String tenantId, String sessionId);
}
