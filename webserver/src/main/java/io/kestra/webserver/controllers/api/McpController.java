package io.kestra.webserver.controllers.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
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
    public PagedResults<Mcp> listMcps(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort) {
        return PagedResults.of(mcpRepository.list(PageableUtils.from(page, size, sort), tenantService.resolveTenant()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Get an MCP server")
    public Mcp getMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        return mcpRepository.get(tenantService.resolveTenant(), id).orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post
    @Operation(tags = {"Mcp"}, summary = "Create an MCP server")
    public HttpResponse<Mcp> createMcp(
        @RequestBody(description = "The MCP server to create") @Body @Valid Mcp mcp) {
        String tenantId = tenantService.resolveTenant();

        if (mcpRepository.get(tenantId, mcp.id()).isPresent()) {
            throw new ConstraintViolationException(
                Collections.singleton(
                    ManualConstraintViolation.of(
                        "MCP id already exists",
                        mcp,
                        Mcp.class,
                        "mcp.id",
                        mcp.id()
                    )
                )
            );
        }

        Mcp toSave = new Mcp(tenantId, mcp.id(), mcp.namespace(), mcp.flowId(),
            mcp.title(), mcp.description(), mcp.enabled(), false, null, null);

        return HttpResponse.ok(mcpRepository.save(null, toSave));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Update an MCP server")
    public HttpResponse<Mcp> updateMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id,
        @RequestBody(description = "The MCP server to update") @Body @Valid Mcp mcp) {
        String tenantId = tenantService.resolveTenant();

        Optional<Mcp> existing = mcpRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        if (!mcp.id().equals(id)) {
            throw new ConstraintViolationException(
                Collections.singleton(
                    ManualConstraintViolation.of(
                        "Illegal MCP id update",
                        mcp,
                        Mcp.class,
                        "mcp.id",
                        mcp.id()
                    )
                )
            );
        }

        Mcp toSave = new Mcp(tenantId, mcp.id(), mcp.namespace(), mcp.flowId(),
            mcp.title(), mcp.description(), mcp.enabled(), false, null, null);

        return HttpResponse.ok(mcpRepository.save(existing.get(), toSave));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Delete an MCP server")
    public HttpResponse<Void> deleteMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        return mcpRepository.delete(tenantService.resolveTenant(), id)
            .map(ignored -> HttpResponse.<Void>status(HttpStatus.NO_CONTENT))
            .orElse(HttpResponse.status(HttpStatus.NOT_FOUND));
    }
}
