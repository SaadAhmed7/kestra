package io.kestra.webserver.controllers.api;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.Mcp;
import io.kestra.core.mcp.services.McpService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class McpControllerTest {

    private static final String MCP_PATH = "/api/v1/main/mcp";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpService mcpService;

    @Test
    void givenValidMcp_whenCreate_thenMcpIsCreated() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());

        // When
        Mcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(mcp.id());
        assertThat(created.namespace()).isEqualTo(mcp.namespace());
        assertThat(created.name()).isEqualTo(mcp.name());
        assertThat(created.enabled()).isTrue();
    }

    @Test
    void givenMcpAlreadyExists_whenCreateWithSameId_thenValidationErrorReturned() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void givenMcpWithMissingRequiredFields_whenCreate_thenValidationErrorReturned() {
        // Given — null name and namespace violate @NotBlank/@NotNull
        Mcp mcp = new Mcp(null, IdUtils.create(), null, null, null, null, null, null, true, null, false, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void givenExistingMcp_whenGet_thenMcpIsReturned() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);

        // When
        Mcp retrieved = client.toBlocking().retrieve(GET(MCP_PATH + "/" + mcp.id()), Mcp.class);

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(mcp.id());
        assertThat(retrieved.name()).isEqualTo(mcp.name());
    }

    @Test
    void givenNonExistingMcp_whenGet_thenNotFoundReturned() {
        // Given
        String nonExistentId = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(GET(MCP_PATH + "/" + nonExistentId), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenMultipleMcps_whenList_thenPagedResultsReturned() {
        // Given
        Mcp mcpOne = buildMcp(IdUtils.create());
        Mcp mcpTwo = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcpOne), Mcp.class);
        client.toBlocking().retrieve(POST(MCP_PATH, mcpTwo), Mcp.class);

        // When
        PagedResults<Mcp> results = client.toBlocking().retrieve(
            GET(MCP_PATH + "?page=1&size=100"),
            Argument.of(PagedResults.class, Mcp.class)
        );

        // Then
        assertThat(results).isNotNull();
        assertThat(results.getTotal()).isGreaterThanOrEqualTo(2);
        List<String> ids = results.getResults().stream().map(Mcp::id).toList();
        assertThat(ids).contains(mcpOne.id(), mcpTwo.id());
    }

    @Test
    void givenExistingMcp_whenUpdate_thenMcpIsUpdated() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);
        Mcp updated = new Mcp(null, mcp.id(), mcp.namespace(), "Updated Name", mcp.description(), null, null, null, false, null, false, false, null, null);

        // When
        Mcp result = client.toBlocking().retrieve(PUT(MCP_PATH + "/" + mcp.id(), updated), Mcp.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void givenNonExistingMcp_whenUpdate_thenNotFoundReturned() {
        // Given
        String nonExistentId = IdUtils.create();
        Mcp mcp = buildMcp(nonExistentId);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(PUT(MCP_PATH + "/" + nonExistentId, mcp), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void givenExistingMcpWithMismatchedId_whenUpdate_thenValidationErrorReturned() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);
        Mcp withDifferentId = buildMcp(IdUtils.create());

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(PUT(MCP_PATH + "/" + mcp.id(), withDifferentId), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void givenExistingMcp_whenDelete_thenNoContentReturned() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);

        // When
        HttpResponse<Void> response = client.toBlocking().exchange(DELETE(MCP_PATH + "/" + mcp.id()));

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
    }

    @Test
    void givenNonExistingMcp_whenDelete_thenNotFoundReturned() {
        // Given
        String nonExistentId = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(DELETE(MCP_PATH + "/" + nonExistentId))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void givenReservedName_whenCreate_thenValidationErrorReturned() {
        // Given
        Mcp mcp = new Mcp(null, null, "io.kestra.test.mcp", Mcp.DEFAULT_NAME,
            "A description", null, null, null, true, null, false, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void givenExistingMcp_whenUpdateWithReservedName_thenValidationErrorReturned() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());
        Mcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);
        Mcp renamed = new Mcp(null, created.id(), created.namespace(), Mcp.DEFAULT_NAME,
            created.description(), null, null, null, true, null, false, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(PUT(MCP_PATH + "/" + created.id(), renamed), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void givenDefaultMcp_whenDelete_thenForbiddenReturned() {
        // Given — provision via the service; the API blocks creating "default" directly
        mcpService.ensureDefaultMcpServer(TenantService.MAIN_TENANT);
        String defaultId = IdUtils.fromParts(Mcp.DEFAULT_NAME, TenantService.MAIN_TENANT);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(DELETE(MCP_PATH + "/" + defaultId))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    @Test
    void givenExistingMcp_whenToggle_thenEnabledStateFlipped() {
        // Given
        Mcp mcp = buildMcp(IdUtils.create());
        Mcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), Mcp.class);
        assertThat(created.enabled()).isTrue();

        // When
        Mcp toggled = client.toBlocking().retrieve(
            PATCH(MCP_PATH + "/" + created.id() + "/toggle", ""), Mcp.class);

        // Then
        assertThat(toggled.enabled()).isFalse();
    }

    @Test
    void givenNonExistingMcp_whenToggle_thenNotFoundReturned() {
        // Given
        String nonExistentId = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(PATCH(MCP_PATH + "/" + nonExistentId + "/toggle", ""), Mcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    private static Mcp buildMcp(String id) {
        return new Mcp(null, id, "io.kestra.test.mcp", "Test MCP Server", "A test description", null, null, null, true, null, false, false, null, null);
    }
}
