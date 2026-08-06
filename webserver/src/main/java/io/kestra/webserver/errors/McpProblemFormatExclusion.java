package io.kestra.webserver.errors;


import jakarta.inject.Singleton;

/**
 * The Model Context Protocol transport, whose errors are JSON-RPC 2.0 envelopes rather than problem documents.
 *
 * <p>Only the transport is excluded, not everything under {@code /mcp}: {@code /mcp/servers} is the ordinary
 * management API for configuring MCP servers and reports errors like any other Kestra endpoint. The transport
 * proper is {@code /mcp/{serverId}}, where some routes legitimately answer with an empty body and a JSON-RPC
 * client would not know what to do with a problem document.
 */
@Singleton
public class McpProblemFormatExclusion implements ProblemFormatExclusion {
    private static final String MCP_SEGMENT = "/mcp/";
    private static final String MANAGEMENT_SEGMENT = "/mcp/servers";

    @Override
    public boolean excludes(final String path) {
        return path.contains(MCP_SEGMENT) && !path.contains(MANAGEMENT_SEGMENT);
    }
}
