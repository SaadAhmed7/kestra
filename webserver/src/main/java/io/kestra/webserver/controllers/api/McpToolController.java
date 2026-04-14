package io.kestra.webserver.controllers.api;

import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.kestra.core.tenant.TenantService;
import io.micronaut.http.annotation.*;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.kestra.mcp.McpServerHandlerTransport;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import io.kestra.core.models.mcp.McpSession;
import io.kestra.mcp.McpSessionFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import io.kestra.mcp.KestraMcpTransportContext;
import io.kestra.core.repositories.McpSessionRepositoryInterface;

import java.util.Optional;

@Slf4j
@Controller("/api/v1/{tenant}/mcp")
public class McpToolController {
    @Inject
    private McpServerHandlerTransport handlerRegistry;

    @Inject
    private TenantService tenantService;

    @Inject
    McpSessionFactory sessionFactory;

    @Inject
    McpRepositoryInterface mcpRepository;

    @Get("/{namespace}/{server}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_EVENT_STREAM})
    public Mono<? extends HttpResponse<?>> handleGetRequest(
        @NotNull String tenant,
        @NotNull String namespace,
        @NotNull String server,
        HttpRequest<String> request) {
        return handleRequest(tenant, namespace, server, request);
    }


    @Delete("/{namespace}/{server}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON})
    public Mono<? extends HttpResponse<?>> handleDeleteRequest(
        @NotNull String tenant,
        @NotNull String namespace,
        @PathVariable String server,
        HttpRequest<String> request) {
        return handleRequest(tenant, namespace, server, request);
    }

    @Post("/{namespace}/{server}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON})
    public Mono<? extends HttpResponse<?>> handleRequest(
        @NotNull String tenant,
        @NotNull String namespace,
        @PathVariable String server,
        HttpRequest<String> request) {

        String tenantId = tenantService.resolveTenant();

        Optional<Mcp> mcpServer = mcpRepository.findByName(tenantId, server);
        if (mcpServer.isEmpty()) {
            return Mono.just(HttpResponse.notFound());
        }
        if (!mcpServer.get().enabled()) {
            return Mono.just(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE));
        }

        var transportContext = sessionFactory.build(
            tenantId, namespace, server, request.getHeaders().get(HttpHeaders.MCP_SESSION_ID)
        );

        return handlerRegistry.getServerHandler(transportContext).handleRequest(request, transportContext);
    }
}
