package io.kestra.webserver.controllers.api;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.Mcp;
import io.kestra.core.mcp.services.McpService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.models.api.ApiMcp;
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
        ApiMcp mcp = buildMcp(IdUtils.create());

        // When
        ApiMcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotBlank();
        assertThat(created.namespace()).isEqualTo(mcp.namespace());
        assertThat(created.name()).isEqualTo(mcp.name());
        assertThat(created.enabled()).isTrue();
        assertThat(created.created()).isNotNull();
    }

    @Test
    void givenMcpAlreadyExists_whenCreateWithSameName_thenConflictReturned() {
        // Given
        ApiMcp mcp = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class);

        // When / Then — same name → same derived id → conflict
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
    }

    @Test
    void givenMcpWithMissingRequiredFields_whenCreate_thenValidationErrorReturned() {
        // Given — null name and namespace violate @NotBlank/@NotNull
        ApiMcp mcp = new ApiMcp(null, null, null, null, null, null, null, true, null, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void givenExistingMcp_whenGet_thenMcpIsReturned() {
        // Given
        ApiMcp mcp = buildMcp(IdUtils.create());
        ApiMcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class);

        // When
        ApiMcp retrieved = client.toBlocking().retrieve(GET(MCP_PATH + "/" + created.id()), ApiMcp.class);

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.name()).isEqualTo(mcp.name());
    }

    @Test
    void givenNonExistingMcp_whenGet_thenNotFoundReturned() {
        // Given
        String nonExistentId = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(GET(MCP_PATH + "/" + nonExistentId), ApiMcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenMultipleMcps_whenList_thenPagedResultsReturned() {
        // Given
        ApiMcp mcpOne = buildMcp(IdUtils.create());
        ApiMcp mcpTwo = buildMcp(IdUtils.create());
        ApiMcp createdOne = client.toBlocking().retrieve(POST(MCP_PATH, mcpOne), ApiMcp.class);
        ApiMcp createdTwo = client.toBlocking().retrieve(POST(MCP_PATH, mcpTwo), ApiMcp.class);

        // When
        PagedResults<ApiMcp> results = client.toBlocking().retrieve(
            GET(MCP_PATH + "?page=1&size=100"),
            Argument.of(PagedResults.class, ApiMcp.class)
        );

        // Then
        assertThat(results).isNotNull();
        assertThat(results.getTotal()).isGreaterThanOrEqualTo(2);
        List<String> ids = results.getResults().stream().map(ApiMcp::id).toList();
        assertThat(ids).contains(createdOne.id(), createdTwo.id());
    }

    @Test
    void givenExistingMcp_whenUpdate_thenMcpIsUpdated() {
        // Given
        ApiMcp mcp = buildMcp(IdUtils.create());
        ApiMcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class);
        ApiMcp update = new ApiMcp(null, created.namespace(), "Updated Name", created.description(),
            null, null, null, false, null, false, null, null);

        // When
        ApiMcp result = client.toBlocking().retrieve(PUT(MCP_PATH + "/" + created.id(), update), ApiMcp.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void givenNonExistingMcp_whenUpdate_thenNotFoundReturned() {
        // Given
        String nonExistentId = IdUtils.create();
        ApiMcp mcp = buildMcp(IdUtils.create());

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(PUT(MCP_PATH + "/" + nonExistentId, mcp), ApiMcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void givenExistingMcp_whenDelete_thenNoContentReturned() {
        // Given
        ApiMcp mcp = buildMcp(IdUtils.create());
        ApiMcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class);

        // When
        HttpResponse<Void> response = client.toBlocking().exchange(DELETE(MCP_PATH + "/" + created.id()));

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
        // Given — "default" is a reserved name
        ApiMcp mcp = new ApiMcp(null, "io.kestra.test.mcp", Mcp.DEFAULT_NAME,
            "A description", null, null, null, true, null, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void givenExistingMcp_whenUpdateWithReservedName_thenValidationErrorReturned() {
        // Given
        ApiMcp mcp = buildMcp(IdUtils.create());
        ApiMcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class);
        ApiMcp renamed = new ApiMcp(null, created.namespace(), Mcp.DEFAULT_NAME,
            created.description(), null, null, null, true, null, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(PUT(MCP_PATH + "/" + created.id(), renamed), ApiMcp.class)
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
        ApiMcp mcp = buildMcp(IdUtils.create());
        ApiMcp created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcp.class);
        assertThat(created.enabled()).isTrue();

        // When
        ApiMcp toggled = client.toBlocking().retrieve(
            PATCH(MCP_PATH + "/" + created.id() + "/toggle", ""), ApiMcp.class);

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
            () -> client.toBlocking().retrieve(PATCH(MCP_PATH + "/" + nonExistentId + "/toggle", ""), ApiMcp.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    /** Builds a valid {@link ApiMcp} request payload with a unique name. */
    private static ApiMcp buildMcp(String uniqueSuffix) {
        return new ApiMcp(null, "io.kestra.test.mcp", "test-mcp-" + uniqueSuffix,
            "A test description", null, null, null, true, null, false, null, null);
    }
}
