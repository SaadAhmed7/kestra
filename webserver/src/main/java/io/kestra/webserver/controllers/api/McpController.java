package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.mcp.models.Mcp;
import io.kestra.core.mcp.repositories.McpRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.models.api.ApiMcp;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/v1/{tenant}/mcp")
@Slf4j
public class McpController {

    @Inject
    private McpRepositoryInterface mcpRepository;

    @Inject
    protected TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Mcp"}, summary = "List MCP servers")
    public PagedResults<ApiMcp> listMcps(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort) {
        return PagedResults.of(
            mcpRepository.list(PageableUtils.from(page, size, sort), tenantService.resolveTenant())
                .map(ApiMcp::from)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Get an MCP server")
    public ApiMcp getMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        return mcpRepository.get(tenantService.resolveTenant(), id)
            .map(ApiMcp::from)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post
    @Operation(tags = {"Mcp"}, summary = "Create an MCP server")
    public HttpResponse<ApiMcp> createMcp(
        @RequestBody(description = "The MCP server to create") @Body @Valid ApiMcp mcp) {
        String tenantId = tenantService.resolveTenant();

        if (Mcp.DEFAULT_NAME.equals(mcp.name())) {
            throw new InvalidException(mcp, "MCP name '" + Mcp.DEFAULT_NAME + "' is reserved");
        }

        String id = IdUtils.from(mcp.name());
        if (mcpRepository.get(tenantId, id).isPresent()) {
            throw new ConflictException("MCP server already exists for id: '" + id + "'");
        }

        Mcp toSave = new Mcp(tenantId, null, mcp.namespace(),
            mcp.name(), mcp.description(), mcp.systemPrompt(), mcp.serverType(), mcp.authType(),
            mcp.enabled(), mcp.iconUrl(), false, false, null, null);

        return HttpResponse.ok(ApiMcp.from(mcpRepository.save(null, toSave)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Update an MCP server")
    public HttpResponse<ApiMcp> updateMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id,
        @RequestBody(description = "The MCP server to update") @Body @Valid ApiMcp mcp) {
        String tenantId = tenantService.resolveTenant();

        Optional<Mcp> existing = mcpRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id);
        }

        if (Mcp.DEFAULT_NAME.equals(mcp.name()) != existing.get().isDefault()) {
            throw new InvalidException(mcp, "MCP name '" + Mcp.DEFAULT_NAME + "' is reserved");
        }

        Mcp toSave = new Mcp(tenantId, id, mcp.namespace(),
            mcp.name(), mcp.description(), mcp.systemPrompt(), mcp.serverType(), mcp.authType(),
            mcp.enabled(), mcp.iconUrl(), false, false, null, null);

        return HttpResponse.ok(ApiMcp.from(mcpRepository.save(existing.get(), toSave)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Delete an MCP server")
    public HttpResponse<Void> deleteMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        String tenantId = tenantService.resolveTenant();
        Optional<Mcp> existing = mcpRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id);
        }
        if (existing.get().isDefault()) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "The default MCP server cannot be deleted");
        }
        return mcpRepository.delete(tenantId, id)
            .map(ignored -> HttpResponse.<Void>status(HttpStatus.NO_CONTENT))
            .orElse(HttpResponse.status(HttpStatus.NOT_FOUND));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Patch(uri = "{id}/toggle")
    @Operation(tags = {"Mcp"}, summary = "Toggle an MCP server's enabled state")
    public HttpResponse<ApiMcp> toggleMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        String tenantId = tenantService.resolveTenant();
        Optional<Mcp> existing = mcpRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id);
        }
        Mcp mcp = existing.get();
        Mcp toggled = new Mcp(tenantId, mcp.id(), mcp.namespace(),
            mcp.name(), mcp.description(), mcp.systemPrompt(), mcp.serverType(), mcp.authType(),
            !mcp.enabled(), mcp.iconUrl(), false, false, null, null);
        return HttpResponse.ok(ApiMcp.from(mcpRepository.save(mcp, toggled)));
    }
}
