package io.kestra.webserver.controllers.api;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.core.execution.Fail;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.context.annotation.Property;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
@Property(name = "kestra.mcp.execution-timeout", value = "PT30S")
class McpToolControllerRunnerTest {

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

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpRepositoryInterface mcpRepository;

    @Inject
    FlowRepositoryInterface flowRepository;

    // -------------------------------------------------------------------------
    // Tool invocation — requires a running executor to process executions
    // -------------------------------------------------------------------------

    @Test
    void givenKnownTool_whenToolCallWithValidInput_thenSuccessResultReturnedViaSse() {
        // Given — flow with a required "message" input; the Fail task's condition is falsy when
        // the input is provided, so the execution succeeds
        String serverName = saveServer(true, null);
        String toolName = saveFlowWithToolAndConditionalFail(serverName);
        String sessionId = initialize(serverName);

        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(toolName, Map.of("message", "hello world"), null)
        );

        // When — execution runs successfully; tool result has isError: false
        String body = client.toBlocking().retrieve(
            mcpPost(serverName, callRequest, sessionId),
            String.class
        );

        // Then
        assertThat(body).contains("\"result\"");
        assertThat(body).contains("\"isError\":false");
    }

    @Test
    void givenKnownTool_whenToolCallWithMissingRequiredInput_thenErrorResultReturnedViaSse() {
        // Given — same flow; the Fail task's condition is truthy when "message" is absent,
        // which causes the execution to fail
        String serverName = saveServer(true, null);
        String toolName = saveFlowWithToolAndConditionalFail(serverName);
        String sessionId = initialize(serverName);

        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(toolName, Map.of(), null)  // "message" argument omitted
        );

        // When — execution fails due to missing required input; tool result has isError: true
        String body = client.toBlocking().retrieve(
            mcpPost(serverName, callRequest, sessionId),
            String.class
        );

        // Then
        assertThat(body).contains("\"result\"");
        assertThat(body).contains("\"isError\":true");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Saves an MCP server record and returns its name. */
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

    private MutableHttpRequest<String> mcpPost(String serverName, String body) {
        return POST(serverUrl(serverName), body)
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .contentType(MediaType.APPLICATION_JSON);
    }

    private MutableHttpRequest<String> mcpPost(String serverName, McpSchema.JSONRPCMessage message) {
        try {
            return mcpPost(serverName, MCP_MAPPER.writeValueAsString(message));
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }

    private MutableHttpRequest<String> mcpPost(String serverName, McpSchema.JSONRPCMessage message, String sessionId) {
        return mcpPost(serverName, message).header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    private String initialize(String serverName) {
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverName, INITIALIZE_REQUEST), Map.class
        );
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    /**
     * Persists a flow with a required {@code message} StringInput and a {@link Fail} task whose
     * condition evaluates to truthy only when the input is absent. This lets a single flow exercise
     * both the valid-input (success) and missing-input (failure) code paths.
     *
     * @return the tool name to use in {@code tools/call} requests
     */
    private String saveFlowWithToolAndConditionalFail(String serverName) {
        String toolName = "tool-" + IdUtils.create().toLowerCase();

        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName(toolName)
            .title("Test Tool")
            .toolDescription("A test MCP tool that requires a 'message' input")
            .mcpServer(serverName)
            .build();

        StringInput messageInput = StringInput.builder()
            .id("message")
            .type(Type.STRING)
            .required(true)
            .build();

        // Fails only when inputs.message is absent/empty — making the same flow useful for
        // both the valid-input and the missing-input test.
        Fail conditionalFail = Fail.builder()
            .id("fail-if-no-input")
            .type(Fail.class.getName())
            .condition(Property.ofExpression("{{ inputs.message is empty }}"))
            .build();

        flowRepository.create(GenericFlow.of(
            Flow.builder()
                .id(IdUtils.create())
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .inputs(List.of(messageInput))
                .tasks(List.of(conditionalFail))
                .triggers(List.of(trigger))
                .build()
        ));
        return toolName;
    }
}
