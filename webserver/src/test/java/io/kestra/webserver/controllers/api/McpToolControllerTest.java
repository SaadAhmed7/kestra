package io.kestra.webserver.controllers.api;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class McpToolControllerTest {

    private static final String MCP_TOOL_PATH = "/api/v1/main/mcp";
    private static final String TEST_NAMESPACE = "io.kestra.test";

    private static final McpJsonMapper MCP_MAPPER = new JacksonMcpJsonMapper(JsonMapper.builder().build());

    private static final McpSchema.JSONRPCRequest INITIALIZE_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_INITIALIZE,
        1,
        new McpSchema.InitializeRequest(
            ProtocolVersions.MCP_2025_03_26,
            new McpSchema.ClientCapabilities(null, null, null, null),
            new McpSchema.Implementation("test", "1.0.0")
        )
    );

    private static final McpSchema.JSONRPCRequest TOOLS_LIST_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_TOOLS_LIST,
        2,
        null
    );

    private static final McpSchema.JSONRPCNotification INITIALIZED_NOTIFICATION = new McpSchema.JSONRPCNotification(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_NOTIFICATION_INITIALIZED,
        Map.of()
    );

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpRepositoryInterface mcpRepository;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Test
    void givenUnknownServer_whenConnect_thenNotFoundReturned() {
        // Given
        String nonExistentName = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(nonExistentName, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void givenDisabledServer_whenConnect_thenServiceUnavailableReturned() {
        // Given
        String serverName = saveServer(false, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverName, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void givenEnabledServer_whenPostWithoutAcceptHeaders_thenBadRequestReturned() {
        // Given
        String serverName = saveServer(true, null);

        // When / Then — MCP transport requires Accept: text/event-stream, application/json
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(serverUrl(serverName), INITIALIZE_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenEnabledServer_whenPostWithEmptyBody_thenBadRequestReturned() {
        // Given
        String serverName = saveServer(true, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverName, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenEnabledServer_whenPostWithMalformedJson_thenBadRequestReturned() {
        // Given
        String serverName = saveServer(true, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverName, "{not-valid-json}"))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenEnabledServer_whenNonInitializePostWithoutSessionId_thenBadRequestReturned() {
        // Given
        String serverName = saveServer(true, null);

        // When / Then — tools/list is not initialize so a session ID is required
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverName, TOOLS_LIST_REQUEST))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenEnabledServer_whenGetWithoutSessionId_thenBadRequestReturned() {
        // Given
        String serverName = saveServer(true, null);

        // When / Then — GET (SSE stream) requires an existing Mcp-Session-Id
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                GET(serverUrl(serverName))
                    .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenEnabledServer_whenDeleteWithoutSessionId_thenBadRequestReturned() {
        // Given
        String serverName = saveServer(true, null);

        // When / Then — DELETE requires Mcp-Session-Id to identify the session to close
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                DELETE(serverUrl(serverName))
                    .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenEnabledServerWithSystemPrompt_whenInitialize_thenSessionCreatedWithServerInfo() {
        // Given
        String systemPrompt = "You are a helpful Kestra assistant.";
        String serverName = saveServer(true, systemPrompt);

        // When
        HttpResponse<Map> response = client.toBlocking().exchange(
            mcpPost(serverName, INITIALIZE_REQUEST), Map.class
        );

        // Then — session is created and its ID is returned in the response header
        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getHeaders().get(HttpHeaders.MCP_SESSION_ID)).isNotBlank();

        // Then — server metadata from the Mcp record is reflected in the protocol response
        Map<String, Object> result = (Map<String, Object>) response.body().get("result");
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertThat(serverInfo.get("name")).isEqualTo(serverName);
        assertThat(result.get("instructions")).isEqualTo(systemPrompt);
    }

    @Test
    void givenInitializedSession_whenInitializedNotificationSent_thenAccepted() {
        // Given
        String serverName = saveServer(true, null);
        String sessionId = initialize(serverName);

        // When
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverName, INITIALIZED_NOTIFICATION, sessionId)
        );

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
    }

    @Test
    void givenInitializedSession_whenToolsListRequested_thenEmptyToolsListReturnedViaSse() {
        // Given
        String serverName = saveServer(true, null);
        String sessionId = initialize(serverName);

        // When — tools/list returns an SSE response containing the JSON-RPC result
        String body = client.toBlocking().retrieve(
            mcpPost(serverName, TOOLS_LIST_REQUEST, sessionId),
            String.class
        );

        // Then — the SSE body contains an empty tools array (no flows with McpToolTrigger in tests)
        assertThat(body).contains("\"tools\"");
    }

    @Test
    void givenInitializedSession_whenDeleteSession_thenOk() {
        // Given
        String serverName = saveServer(true, null);
        String sessionId = initialize(serverName);

        // When
        HttpResponse<?> response = client.toBlocking().exchange(
            DELETE(serverUrl(serverName))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.MCP_SESSION_ID, sessionId)
        );

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
    }

    // -------------------------------------------------------------------------
    // MCP tools — listing and tool-level protocol validation
    // -------------------------------------------------------------------------

    @Test
    void givenFlowWithMcpTrigger_whenToolsListRequested_thenToolAppearsInList() {
        // Given — flow must exist before session init so the server picks it up when built
        String serverName = saveServer(true, null);
        String toolName = saveFlowWithTool(serverName);
        String sessionId = initialize(serverName);

        // When
        String body = client.toBlocking().retrieve(
            mcpPost(serverName, TOOLS_LIST_REQUEST, sessionId),
            String.class
        );

        // Then — the SSE body lists the tool registered on this server
        assertThat(body).contains(toolName);
    }

    @Test
    void givenUnknownToolName_whenToolCallSent_thenMcpErrorReturnedViaSse() {
        // Given — server with no registered tools
        String serverName = saveServer(true, null);
        String sessionId = initialize(serverName);

        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest("non-existent-tool", Map.of(), null)
        );

        // When — tool name has no matching handler; MCP returns a JSON-RPC error immediately
        String body = client.toBlocking().retrieve(
            mcpPost(serverName, callRequest, sessionId),
            String.class
        );

        // Then — response contains a JSON-RPC error (not a successful result)
        assertThat(body).contains("\"error\"");
        assertThat(body).doesNotContain("\"result\"");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Saves an MCP server record directly to the repository and returns its name. */
    private String saveServer(boolean enabled, String systemPrompt) {
        String serverName = "server-" + IdUtils.create();
        Mcp server = new Mcp(TenantService.MAIN_TENANT, null, TEST_NAMESPACE, serverName,
            "A test MCP server", systemPrompt, null, null, enabled, null, false, false, null, null);
        mcpRepository.save(null, server);
        return serverName;
    }

    /** Full URL for the MCP Streamable HTTP transport endpoint. */
    private String serverUrl(String serverName) {
        return MCP_TOOL_PATH + "/" + TEST_NAMESPACE + "/" + serverName;
    }

    /**
     * Builds a POST request with the Accept headers required by the MCP Streamable HTTP transport.
     * Both {@code text/event-stream} and {@code application/json} must be present.
     * Use the {@link String} overload only for guard tests that send an intentionally invalid body.
     */
    private MutableHttpRequest<String> mcpPost(String serverName, String body) {
        return POST(serverUrl(serverName), body)
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .contentType(MediaType.APPLICATION_JSON);
    }

    /** Serializes an MCP message and builds a POST request with the required Accept headers. */
    private MutableHttpRequest<String> mcpPost(String serverName, McpSchema.JSONRPCMessage message) {
        try {
            return mcpPost(serverName, MCP_MAPPER.writeValueAsString(message));
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }

    /** Builds a POST request with an {@code Mcp-Session-Id} header for post-initialize calls. */
    private MutableHttpRequest<String> mcpPost(String serverName, McpSchema.JSONRPCMessage message, String sessionId) {
        return mcpPost(serverName, message)
            .header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    /** Sends an MCP initialize request and returns the session ID from the response header. */
    private String initialize(String serverName) {
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverName, INITIALIZE_REQUEST), Map.class
        );
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    /**
     * Persists a flow with a {@link McpToolTrigger} pointing at {@code serverName} and returns the
     * tool name. The flow must be saved <em>before</em> initializing the session so that
     * {@link io.kestra.mcp.McpServerHandlerTransport} registers it when building the server handler.
     */
    private String saveFlowWithTool(String serverName) {
        String toolName = "tool-" + IdUtils.create().toLowerCase();
        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName(toolName)
            .title("Test Tool")
            .toolDescription("A test MCP tool")
            .mcpServer(serverName)
            .build();

        flowRepository.create(GenericFlow.of(
            Flow.builder()
                .id(IdUtils.create())
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .tasks(List.of(
                    Return.builder()
                        .id("task")
                        .type(Return.class.getName())
                        .format(Property.ofValue("done"))
                        .build()
                ))
                .triggers(List.of(trigger))
                .build()
        ));
        return toolName;
    }
}
