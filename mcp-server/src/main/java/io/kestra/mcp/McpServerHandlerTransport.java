package io.kestra.mcp;

import io.micronaut.http.HttpRequest;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class McpServerHandlerTransport {
    private final Map<HandlerKey, KestraFluxStreamableServerTransportProvider> handlers = new ConcurrentHashMap<>();

    @Inject
    McpErrorResponseMapper mcpErrorResponseMapper;

    @Inject
    McpToolService mcpToolService;

    @Inject
    SessionProxyRepository sessionProxyRepository;

    public KestraFluxStreamableServerTransportProvider getServerHandler(
        KestraMcpTransportContext kestraMcpTransportContext
    ) {
        return handlers.computeIfAbsent(HandlerKey.from(kestraMcpTransportContext), handlerKey -> {
            KestraFluxStreamableServerTransportProvider transportProvider = new KestraFluxStreamableServerTransportProvider(
                mcpErrorResponseMapper,
                sessionProxyRepository
            );

            buildServer(handlerKey, transportProvider);
            return transportProvider;
        });
    }

    private void buildServer(
        HandlerKey handlerKey,
        KestraFluxStreamableServerTransportProvider serverTransport
    ) {
        McpServer.AsyncSpecification<?> mcpServerSpec = McpServer.async(serverTransport)
            .capabilities(
                McpSchema.ServerCapabilities.builder()
                    .tools(true)
                    .build()
            );

        mcpServerSpec.tools(this.mcpToolService.listToolSpecsForServer(
            handlerKey.tenantId(),
            handlerKey.namespace(),
            handlerKey.serverId()
        )).build();
    }


    private record HandlerKey(
        String tenantId,
        String namespace,
        String serverId
    ) {
        public static HandlerKey from(KestraMcpTransportContext kestraMcpTransportContext) {
            return new HandlerKey(
                kestraMcpTransportContext.getTenantId(),
                kestraMcpTransportContext.getNamespace(),
                kestraMcpTransportContext.getServerId()
            );
        }
    }
}
