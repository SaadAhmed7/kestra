package io.kestra.mcp;

import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class McpServerFactory {
    private static final String JSONRPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String SERVER_VERSION = "1.0.0";

    @Inject
    private McpRepositoryInterface mcpRepository;

    @Inject
    private McpToolService mcpToolService;

    /**
     * Handle an MCP JSON-RPC request and return the appropriate response.
     * Returns null for notifications (no id).
     */
    public McpSchema.JSONRPCResponse handleRequest(String tenantId, String namespace, String serverId, McpSchema.JSONRPCRequest request) {
        try {
            return switch (request.method()) {
                case "initialize" -> handleInitialize(request, tenantId, serverId);
                case "ping" -> successResponse(request.id(), Map.of());
                case "tools/list" -> handleToolsList(request, tenantId, namespace, serverId);
                case "tools/call" -> handleToolsCall(request, tenantId, namespace, serverId);
                default -> errorResponse(request.id(), ErrorCodes.METHOD_NOT_FOUND,
                    "Method not found: " + request.method());
            };
        } catch (Exception e) {
            log.error("Error handling MCP request method={}", request.method(), e);
            return errorResponse(request.id(), ErrorCodes.INTERNAL_ERROR,
                "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle an MCP notification (no response expected).
     */
    public void handleNotification(McpSchema.JSONRPCNotification notification) {
        // Notifications like "notifications/initialized" are acknowledged silently
        log.debug("Received MCP notification: {}", notification.method());
    }

    private McpSchema.JSONRPCResponse handleInitialize(McpSchema.JSONRPCRequest request, String tenantId, String serverId) {
        Mcp mcpServer = mcpRepository.get(tenantId, serverId).orElseThrow(
            () -> new RuntimeException("Unable to find mcp server id" + serverId)
        );



        String serverTitle = mcpServer.title();

        McpSchema.InitializeResult result = new McpSchema.InitializeResult(
            PROTOCOL_VERSION,
            McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build(),
            new McpSchema.Implementation(serverTitle, SERVER_VERSION),
            mcpServer.description()
        );
        return successResponse(request.id(), result);
    }

    private McpSchema.JSONRPCResponse handleToolsList(McpSchema.JSONRPCRequest request, String tenantId, String namespace, String serverId) {
        List<McpSchema.Tool> tools = mcpToolService.listToolSpecsForServer(tenantId, namespace, serverId);
        McpSchema.ListToolsResult result = new McpSchema.ListToolsResult(tools, null);
        return successResponse(request.id(), result);
    }

    @SuppressWarnings("unchecked")
    private McpSchema.JSONRPCResponse handleToolsCall(McpSchema.JSONRPCRequest request, String tenantId, String namespace, String serverId) {
        Map<String, Object> params = request.params() instanceof Map<?, ?>
            ? (Map<String, Object>) request.params()
            : Map.of();

        String name = (String) params.get("name");
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?>
            ? (Map<String, Object>) params.get("arguments")
            : Map.of();

        if (name == null) {
            return errorResponse(request.id(), ErrorCodes.INVALID_PARAMS,
                "Invalid params: missing tool name");
        }

        Optional<McpSchema.Tool> tool = mcpToolService.getSpecTool(tenantId, namespace, serverId, name);
        if (tool.isPresent()) {
            McpSchema.CallToolResult result = mcpToolService.callTool(tenantId, namespace, serverId, tool.get(), arguments);
            return successResponse(request.id(), result);
        }

        return errorResponse(request.id(), ErrorCodes.INVALID_PARAMS, "Unknown tool: " + name);
    }

    private McpSchema.JSONRPCResponse successResponse(Object id, Object result) {
        return new McpSchema.JSONRPCResponse(JSONRPC_VERSION, id, result, null);
    }

    private McpSchema.JSONRPCResponse errorResponse(Object id, int code, String message) {
        return new McpSchema.JSONRPCResponse(JSONRPC_VERSION, id, null,
            new McpSchema.JSONRPCResponse.JSONRPCError(code, message, null));
    }

    /**
     * MCP standard error codes.
     */
    static final class ErrorCodes {
        static final int PARSE_ERROR = -32700;
        static final int INVALID_REQUEST = -32600;
        static final int METHOD_NOT_FOUND = -32601;
        static final int INVALID_PARAMS = -32602;
        static final int INTERNAL_ERROR = -32603;
    }
}
