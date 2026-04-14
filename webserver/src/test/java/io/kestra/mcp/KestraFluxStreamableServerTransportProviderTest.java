package io.kestra.mcp;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.mcp.models.McpSessionNotificationEvent;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.mcp.repositories.McpSessionRepositoryInterface;
import io.kestra.core.server.ServerInstance;
import io.micronaut.http.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "h2")
class KestraFluxStreamableServerTransportProviderTest {

    private static final McpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(JsonMapper.builder().build());

    private static final McpSchema.JSONRPCRequest INITIALIZE_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_INITIALIZE,
        1,
        new McpSchema.InitializeRequest(
            ProtocolVersions.MCP_2025_03_26,
            new McpSchema.ClientCapabilities(null, null, null, null),
            new McpSchema.Implementation("test-client", "1.0.0")
        )
    );

    private static final McpSchema.JSONRPCNotification NOTIFICATION_REQUEST = new McpSchema.JSONRPCNotification(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_NOTIFICATION_INITIALIZED,
        null
    );

    private static final McpSchema.JSONRPCRequest TOOLS_LIST_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_TOOLS_LIST,
        2,
        null
    );

    @Inject
    SessionProxyRepository sessionProxyRepository;

    @Inject
    McpSessionRepositoryInterface mcpSessionRepository;

    @Inject
    KeyedDispatchQueueInterface<McpSessionNotificationEvent> mcpSessionNotificationQueue;

    @Test
    void givenServerIsShuttingDown_whenNewRequestComes_thenRejectRequest() {
        // Given
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        provider.closeGracefully().block();
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void givenThing_whenAction_thenResult() {
        // Given — an unsupported HTTP method (PUT) with otherwise valid headers
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        KestraMcpTransportContext context = buildTransportContext();
        MutableHttpRequest<String> request = HttpRequest.<String>create(HttpMethod.PUT, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    void givenARequestWithInvalidResponseType_whenRequestReceived_thenRejectRequest() {
        // Given — Accept header contains only application/json, missing the required text/event-stream
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenPostRequestWithEmptyBody_whenRequestReceived_thenRejectRequest() {
        // Given — POST with no body at all
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        KestraMcpTransportContext context = buildTransportContext();
        MutableHttpRequest<String> request = HttpRequest.<String>create(HttpMethod.POST, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenPostRequestWithInvalidBody_whenRequestReceived_thenRejectRequest() {
        // Given — POST with a body that is not valid JSON-RPC
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", "{{not valid json}}")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenInitializeRequest_whenRequestReceived_thenSessionIdsReturned() {
        // Given
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getHeaders().get(HttpHeaders.MCP_SESSION_ID)).isNotNull();
        assertThat(response.getHeaders().get(HttpHeaders.MCP_SESSION_ID)).isEqualTo(context.getSessionId());
    }

    @Test
    void givenNotificationRequest_whenRequestReceived_thenRequestIsAccepted() {
        // Given — a session established via initialize, then a notification posted with the session ID
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, context).block();

        // When
        HttpRequest<String> notifRequest = HttpRequest.POST("/mcp", toJson(NOTIFICATION_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(notifRequest, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenRequestReceived_thenRequestIsAccepted() {
        // Given
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(),
            context.getNamespace(),
            context.getServerId(),
            context.getSessionId(),
            "other-server-instance-id",
            null
        ));

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);

        // When
        HttpRequest<String> notifRequest = HttpRequest.POST("/mcp", toJson(NOTIFICATION_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(notifRequest, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenToolCallReceived_thenRequestIsHandledByEphemeralSession() {
        // Given — session exists on another server
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(), context.getNamespace(), context.getServerId(),
            context.getSessionId(), "other-server-instance-id", null
        ));

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        provider.setSessionFactory(buildSessionFactory());

        // When — a tools/list request arrives for that cross-server session
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(TOOLS_LIST_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then — an ephemeral session handles the request locally; 200 not 503
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void givenSessionIsOnSeparateInstance_whenNotifyClientsIsCalled_thenNotificationSentViaQueue() throws InterruptedException {
        // Given — a local session to provide (tenantId, namespace, serverId) context to the provider
        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext localContext = buildTransportContext();
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, localContext).block();

        // And — a remote session for the same server, hosted on a different node
        String remoteSseNode = UUID.randomUUID().toString();
        String remoteSessionId = UUID.randomUUID().toString();
        mcpSessionRepository.save(new McpSession(
            localContext.getTenantId(), localContext.getNamespace(), localContext.getServerId(),
            remoteSessionId, remoteSseNode, null
        ));

        // Subscribe to the queue for the remote node before broadcasting
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionNotificationEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionNotificationEvent> subscriber = mcpSessionNotificationQueue.subscriber(remoteSseNode);
        subscriber.subscribe(either -> {
            if (either.isLeft()) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        // When — a tools/list change notification is broadcast
        provider.notifyClients(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null).block();

        // Then — the notification was routed to the queue for the remote node
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().method()).isEqualTo(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED);
        assertThat(received.get().sseNode()).isEqualTo(remoteSseNode);
        assertThat(received.get().sessionId()).isEqualTo(remoteSessionId);

        subscriber.close();
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenDeleteReceived_thenOwningNodeNotifiedAndSessionDeleted() throws InterruptedException {
        // Given — session exists on another server
        String remoteSseNode = UUID.randomUUID().toString();
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(), context.getNamespace(), context.getServerId(),
            context.getSessionId(), remoteSseNode, null
        ));

        // Subscribe to the remote node's queue before sending the DELETE
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionNotificationEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionNotificationEvent> subscriber = mcpSessionNotificationQueue.subscriber(remoteSseNode);
        subscriber.subscribe(either -> {
            if (either.isLeft()) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);

        // When — DELETE arrives on this server for a session owned by another server
        HttpRequest<String> deleteRequest = HttpRequest.<String>create(HttpMethod.DELETE, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(deleteRequest, context).block();

        // Then — 200 OK
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // And — the session is removed from the repository
        assertThat(mcpSessionRepository.find(
            context.getTenantId(), context.getNamespace(), context.getServerId(), context.getSessionId()
        )).isEmpty();

        // And — the owning node received a delete signal via the queue (null response + null method)
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().sseNode()).isEqualTo(remoteSseNode);
        assertThat(received.get().sessionId()).isEqualTo(context.getSessionId());
        assertThat(received.get().response()).isNull();
        assertThat(received.get().method()).isNull();

        subscriber.close();
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenGetReceived_thenSseOwnershipTransferredToThisNode() throws InterruptedException {
        // Given — session exists on another server
        String remoteSseNode = UUID.randomUUID().toString();
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(), context.getNamespace(), context.getServerId(),
            context.getSessionId(), remoteSseNode, null
        ));

        // Subscribe to the remote node's queue before sending the GET
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionNotificationEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionNotificationEvent> subscriber = mcpSessionNotificationQueue.subscriber(remoteSseNode);
        subscriber.subscribe(either -> {
            if (either.isLeft()) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), sessionProxyRepository);
        provider.setSessionFactory(buildSessionFactory());

        // When — GET arrives on this server for a session owned by another server
        HttpRequest<String> getRequest = HttpRequest.<String>GET("/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(getRequest, context).block();

        // Then — SSE stream is established on this server (200 OK, not 404)
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // And — the repository now records this node as the SSE owner
        assertThat(mcpSessionRepository.find(
            context.getTenantId(), context.getNamespace(), context.getServerId(), context.getSessionId()
        )).isPresent().get().extracting(McpSession::sseNode).isEqualTo(ServerInstance.INSTANCE_ID);

        // And — the previous owning node received a delete signal to release its in-memory state
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().sseNode()).isEqualTo(remoteSseNode);
        assertThat(received.get().sessionId()).isEqualTo(context.getSessionId());
        assertThat(received.get().response()).isNull();
        assertThat(received.get().method()).isNull();

        subscriber.close();
    }

    private static KestraMcpTransportContext buildTransportContext() {
        return KestraMcpTransportContext.builder()
            .tenantId("test-tenant")
            .namespace("io.kestra.test")
            .serverId("default")
            .build();
    }

    private static String toJson(Object message) {
        try {
            return JSON_MAPPER.writeValueAsString(message);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message to JSON", e);
        }
    }

    private static McpStreamableServerSession.Factory buildSessionFactory() {
        return initRequest -> {
            McpSchema.InitializeResult result = new McpSchema.InitializeResult(
                ProtocolVersions.MCP_2025_03_26,
                new McpSchema.ServerCapabilities(null, null, null, null, null, null),
                new McpSchema.Implementation("test-server", "1.0.0"),
                null
            );
            McpStreamableServerSession session = new KestraMcpStreamableServerSessionWrapper(
                UUID.randomUUID().toString(),
                initRequest.capabilities(),
                initRequest.clientInfo(),
                Duration.ofSeconds(30),
                Map.of(),
                Map.of()
            );
            return new McpStreamableServerSession.McpStreamableServerSessionInit(session, Mono.just(result));
        };
    }
}
