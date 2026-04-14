package io.kestra.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.mcp.models.McpSessionNotificationEvent;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.mcp.repositories.McpSessionRepositoryInterface;
import io.kestra.core.server.ServerInstance;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
public class SessionProxyRepository {
    @Inject
    private McpSessionRepositoryInterface sessionRepository;

    @Inject
    private KeyedDispatchQueueInterface<McpSessionNotificationEvent> mcpSessionNotificationQueue;

    @Inject
    private ObjectMapper objectMapper;

    private final Map<KestraMcpTransportContext, KestraSessionProxy> sessionProxies = new ConcurrentHashMap<>();
    private final Map<KestraMcpTransportContext, McpStreamableServerSession> streamableSessions = new ConcurrentHashMap<>();

    private final Sinks.Many<SessionProxyNotification> localJsonrpcResponseFluxSink = Sinks.many().multicast().onBackpressureBuffer();

    private QueueSubscriber<McpSessionNotificationEvent> notificationSubscriber;

    @PostConstruct
    public void startNotificationConsumer() {
        localJsonrpcResponseFluxSink.asFlux().subscribe(notification -> {
            KestraMcpTransportContext context = notification.kestraMcpTransportContext();
            sessionRepository.find(
                context.getTenantId(),
                context.getNamespace(),
                context.getServerId(),
                context.getSessionId()
            ).ifPresentOrElse(
                session -> {
                    try {
                        mcpSessionNotificationQueue.emit(
                            session.sseNode(),
                            McpSessionNotificationEvent.response(
                                session.sseNode(),
                                context.getTenantId(),
                                context.getNamespace(),
                                context.getServerId(),
                                context.getSessionId(),
                                notification.jsonrpcResponse()
                            )
                        );
                    } catch (QueueException e) {
                        log.error("Failed to emit response to queue for session {}: {}", context.getSessionId(), e.getMessage(), e);
                    }
                },
                () -> log.warn("Cannot route response: session not found in repository, sessionId={}", context.getSessionId())
            );
        });

        notificationSubscriber = mcpSessionNotificationQueue.subscriber(ServerInstance.INSTANCE_ID);
        notificationSubscriber.subscribe(either -> {
            if (either.isRight()) {
                log.warn("Failed to deserialize MCP session notification: {}", either.getRight().getMessage());
                return;
            }

            McpSessionNotificationEvent event = either.getLeft();

            KestraMcpTransportContext context = KestraMcpTransportContext.builder()
                .tenantId(event.tenantId())
                .namespace(event.namespace())
                .serverId(event.serverId())
                .sessionId(event.sessionId())
                .build();

            // Null response + null method = delete signal (session closed on the owning node)
            if (event.response() == null && event.method() == null) {
                Optional.ofNullable(streamableSessions.remove(context))
                    .ifPresent(McpStreamableServerSession::delete);
                return;
            }

            McpStreamableServerSession session = streamableSessions.get(context);
            if (session == null) {
                log.debug("No local session found for event, sessionId={}", event.sessionId());
                return;
            }

            if (event.method() != null) {
                session.sendNotification(event.method(), event.params())
                    .doOnError(e -> log.error("Failed to deliver notification to session {}: {}", event.sessionId(), e.getMessage()))
                    .onErrorComplete()
                    .subscribe();
                return;
            }

            McpSchema.JSONRPCResponse response = objectMapper.convertValue(event.response(), McpSchema.JSONRPCResponse.class);
            session.accept(response)
                .doOnError(e -> log.error("Failed to deliver response to session {}: {}", event.sessionId(), e.getMessage()))
                .onErrorComplete()
                .subscribe();

        });
    }

    public Collection<McpStreamableServerSession> listMcpStreamableServerSession() {
        return streamableSessions.values();
    }

    public void clear() {
        streamableSessions.clear();
    }

    @PreDestroy
    public void stopNotificationConsumer() {
        if (notificationSubscriber != null) {
            notificationSubscriber.close();
        }
    }

    Optional<KestraSessionProxy> findKestraSessionProxy(KestraMcpTransportContext context) {
        return getMcpSessionFromTransportContext(context).map(_ ->
            sessionProxies.computeIfAbsent(
                context, _ -> new KestraSessionProxy(context, localJsonrpcResponseFluxSink)
            )
        );
    }

    Optional<McpStreamableServerSession> findMcpStreamableServerSession(KestraMcpTransportContext context) {
        return Optional.ofNullable(streamableSessions.get(context));
    }

    public void close(KestraMcpTransportContext context) {
        getMcpSessionFromTransportContext(context).ifPresent(session -> {
            try {
                mcpSessionNotificationQueue.emit(
                    session.sseNode(),
                    McpSessionNotificationEvent.response(
                        session.sseNode(),
                        context.getTenantId(), context.getNamespace(), context.getServerId(),
                        context.getSessionId(), null  // null response = delete signal
                    )
                );
            } catch (QueueException e) {
                log.error("Failed to emit delete signal for session {}: {}", context.getSessionId(), e.getMessage(), e);
            }
        });

        sessionProxies.remove(context);
        sessionRepository.delete(context.getTenantId(), context.getSessionId());
    }

    void addProxyForMcpStreamableServerSession(KestraMcpTransportContext context, McpStreamableServerSession mcpStreamableServerSession) {
        if (sessionProxies.get(context) != null || getMcpSessionFromTransportContext(context).isPresent()) {
            throw new RuntimeException("Unable to add session proxy as on is already registered");
        }

        sessionProxies.put(context, new KestraSessionProxy(context, localJsonrpcResponseFluxSink));

        sessionRepository.save(new McpSession(
            context.getTenantId(),
            context.getNamespace(),
            context.getServerId(),
            context.getSessionId(),
            ServerInstance.INSTANCE_ID,
            context.getUserId()
        ));

        streamableSessions.put(context, mcpStreamableServerSession);
    }

    /**
     * Takes over the SSE ownership of a session that is currently registered on another node.
     * <p>
     * Notifies the previous owning node to release its in-memory state, updates {@code sseNode}
     * in the repository to point to this instance, and registers the supplied session locally so
     * that the queue consumer can deliver server-to-client notifications to it.
     * <p>
     * Call {@link #deregisterSseSession(KestraMcpTransportContext)} when the SSE connection closes.
     *
     * @param context the transport context identifying the session
     * @param session the local session that will serve the SSE stream
     */
    public void takeSseOwnership(KestraMcpTransportContext context, McpStreamableServerSession session) {
        getMcpSessionFromTransportContext(context).ifPresent(existing -> {
            if (!ServerInstance.INSTANCE_ID.equals(existing.sseNode())) {
                try {
                    mcpSessionNotificationQueue.emit(
                        existing.sseNode(),
                        McpSessionNotificationEvent.response(
                            existing.sseNode(),
                            context.getTenantId(), context.getNamespace(), context.getServerId(),
                            context.getSessionId(), null  // null = delete signal
                        )
                    );
                } catch (QueueException e) {
                    log.error("Failed to emit SSE-takeover signal for session {}: {}", context.getSessionId(), e.getMessage(), e);
                }
            }
        });

        sessionRepository.save(new McpSession(
            context.getTenantId(), context.getNamespace(), context.getServerId(),
            context.getSessionId(), ServerInstance.INSTANCE_ID, context.getUserId()
        ));

        streamableSessions.put(context, session);
    }

    /**
     * Removes a session from the local {@code streamableSessions} map when its SSE connection
     * closes. Does not delete the session from the repository — the session remains valid until
     * the client explicitly sends a {@code DELETE} request.
     *
     * @param context the transport context identifying the session
     */
    public void deregisterSseSession(KestraMcpTransportContext context) {
        streamableSessions.remove(context);
    }

    /**
     * Emits a server-to-client notification to all sessions of the same MCP servers
     * that are hosted on other nodes. Called by the transport provider's
     * {@code notifyClients} to ensure horizontally-scaled nodes receive broadcasts.
     *
     * @param method the MCP notification method (e.g. {@code notifications/tools/list_changed})
     * @param params optional notification parameters
     */
    public Mono<Void> notifyRemoteSessions(String method, Object params) {
        return Flux.fromIterable(sessionProxies.keySet())
            .map(ctx -> new ServerKey(ctx.getTenantId(), ctx.getNamespace(), ctx.getServerId()))
            .distinct()
            .flatMap(key -> Flux.fromIterable(
                sessionRepository.findByServerId(key.tenantId(), key.namespace(), key.serverId())
            ))
            .filter(session -> !ServerInstance.INSTANCE_ID.equals(session.sseNode()))
            .flatMap(session -> {
                try {
                    mcpSessionNotificationQueue.emit(
                        session.sseNode(),
                        McpSessionNotificationEvent.notification(
                            session.sseNode(),
                            session.tenantId(), session.namespace(), session.serverId(),
                            session.sessionId(), method, params
                        )
                    );
                } catch (QueueException e) {
                    log.error("Failed to emit notification for session {}: {}", session.sessionId(), e.getMessage(), e);
                }
                return Mono.<Void>empty();
            })
            .then();
    }

    private Optional<McpSession> getMcpSessionFromTransportContext(KestraMcpTransportContext transportContext) {
        return sessionRepository.find(
            transportContext.getTenantId(),
            transportContext.getNamespace(),
            transportContext.getServerId(),
            transportContext.getSessionId()
        );
    }

    private record ServerKey(String tenantId, String namespace, String serverId) {}

    public record SessionProxyNotification(
        McpSchema.JSONRPCResponse jsonrpcResponse,
        KestraMcpTransportContext kestraMcpTransportContext
    ) {}
}
