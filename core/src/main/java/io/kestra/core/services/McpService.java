package io.kestra.core.services;

import io.kestra.core.contexts.KestraConfig;
import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class McpService {

    @Inject
    private McpRepositoryInterface mcpRepository;

    @Inject
    private KestraConfig kestraConfig;

    public void ensureDefaultMcpServer(final String tenantId) {
        String defaultId = IdUtils.fromParts(Mcp.DEFAULT_NAME, tenantId);
        if (mcpRepository.get(tenantId, defaultId).isPresent()) {
            return;
        }

        Mcp defaultServer = new Mcp(
            tenantId,
            defaultId,
            kestraConfig.getSystemFlowNamespace(),
            Mcp.DEFAULT_NAME,
            "Default MCP server for this tenant. Exposes all MCP Tool triggers as tools.",
            "Expose Kestra flows as tools. Invoke a tool only when the user's request clearly " +
            "maps to executing one of the available flows, using the flow's inputs as the tool " +
            "parameters. Do not invent tools or capabilities beyond the provided flows. If no " +
            "suitable flow exists, state that the request cannot be fulfilled. Do not provide " +
            "explanations about Kestra unless explicitly asked.",
            Mcp.ServerType.PRIVATE,
            Mcp.AuthType.BASIC,
            true,
            null,
            false, false, null, null
        );

        mcpRepository.save(null, defaultServer);
    }
}
