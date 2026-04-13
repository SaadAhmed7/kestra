package io.kestra.webserver.controllers.api;

import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;

@io.kestra.core.junit.annotations.KestraTest
class McpToolControllerTest {

    private static final String MCP_TOOL_PATH = "/api/v1/main/mcp";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpRepositoryInterface mcpRepository;

    @Test
    void givenUnknownServer_whenConnect_thenNotFoundReturned() {
        // Given
        String nonExistentName = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(POST(MCP_TOOL_PATH + "/io.kestra.test/" + nonExistentName, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void givenDisabledServer_whenConnect_thenServiceUnavailableReturned() {
        // Given
        String serverName = "disabled-" + IdUtils.create();
        Mcp disabled = new Mcp(TenantService.MAIN_TENANT, null, "io.kestra.test", serverName,
            "A disabled server", null, null, null, false, null, false, false, null, null);
        mcpRepository.save(null, disabled);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(POST(MCP_TOOL_PATH + "/io.kestra.test/" + serverName, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }
}