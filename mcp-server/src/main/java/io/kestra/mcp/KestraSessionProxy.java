package io.kestra.mcp;


import io.kestra.core.models.mcp.McpSessionNotificationEvent;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.*;

@AllArgsConstructor
public class KestraSessionProxy {
    private final KestraMcpTransportContext transportContext;
    private final Sinks.Many<SessionProxyRepository.SessionProxyNotification> jsonrpcResponseFluxSink;


    public Mono<Void> accept(McpSchema.JSONRPCResponse response) {
        jsonrpcResponseFluxSink.tryEmitNext(
            new SessionProxyRepository.SessionProxyNotification(
                response,
                transportContext
            )
        );
        return Mono.empty();
    }

    public Mono<Void> delete() {
        jsonrpcResponseFluxSink.tryEmitComplete();
        return Mono.empty();
    }
}
