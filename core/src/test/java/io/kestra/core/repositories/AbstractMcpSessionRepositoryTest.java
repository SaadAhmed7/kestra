package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.models.mcp.McpSession;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public abstract class AbstractMcpSessionRepositoryTest {

    @Inject
    private McpSessionRepositoryInterface mcpSessionRepository;

    @Test
    void givenNewSessionWhenSaveThenPersisted() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession session = createSession(tenant);

        // When
        McpSession saved = mcpSessionRepository.save(session);

        // Then
        assertThat(saved.sessionId()).isEqualTo(session.sessionId());
        assertThat(saved.serverId()).isEqualTo(session.serverId());
        assertThat(saved.namespace()).isEqualTo(session.namespace());
        assertThat(saved.sseNode()).isEqualTo(session.sseNode());
        assertThat(saved.tenantId()).isEqualTo(tenant);
    }

    @Test
    void givenExistingSessionWhenFindThenReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession saved = mcpSessionRepository.save(createSession(tenant));

        // When
        Optional<McpSession> found = mcpSessionRepository.find(
            tenant, saved.namespace(), saved.serverId(), saved.sessionId()
        );

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().sessionId()).isEqualTo(saved.sessionId());
        assertThat(found.get().sseNode()).isEqualTo(saved.sseNode());
    }

    @Test
    void givenUnknownSessionIdWhenFindThenEmpty() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpSession> found = mcpSessionRepository.find(
            tenant, "io.kestra.test", "server-id", IdUtils.create()
        );

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void givenMultipleSessionsWhenFindByServerIdThenAllReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.test";
        String serverId = "server-" + IdUtils.create();

        mcpSessionRepository.save(createSession(tenant, namespace, serverId, "node-1"));
        mcpSessionRepository.save(createSession(tenant, namespace, serverId, "node-1"));
        // Different server — must not appear in results
        mcpSessionRepository.save(createSession(tenant, namespace, "other-server", "node-2"));

        // When
        List<McpSession> results = mcpSessionRepository.findByServerId(tenant, namespace, serverId);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.serverId().equals(serverId));
    }

    @Test
    void givenSessionsAcrossNodesWhenFindBySseNodeThenOnlyMatchingNodeReturned() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String targetNode = "node-" + IdUtils.create();

        mcpSessionRepository.save(createSession(tenant1, "io.kestra.test", "srv-1", targetNode));
        mcpSessionRepository.save(createSession(tenant2, "io.kestra.test", "srv-2", targetNode));
        // Different node — must not appear
        mcpSessionRepository.save(createSession(tenant1, "io.kestra.test", "srv-3", "other-node"));

        // When
        List<McpSession> results = mcpSessionRepository.findBySseNode(targetNode);

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(s -> s.sseNode().equals(targetNode));
    }

    @Test
    void givenExistingSessionWhenDeleteThenRemovedAndReturnedOnce() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession saved = mcpSessionRepository.save(createSession(tenant));

        // When
        Optional<McpSession> deleted = mcpSessionRepository.delete(tenant, saved.sessionId());

        // Then
        assertThat(deleted).isPresent();
        assertThat(deleted.get().sessionId()).isEqualTo(saved.sessionId());
        assertThat(mcpSessionRepository.find(tenant, saved.namespace(), saved.serverId(), saved.sessionId()))
            .isEmpty();
    }

    @Test
    void givenUnknownSessionIdWhenDeleteThenEmpty() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpSession> result = mcpSessionRepository.delete(tenant, IdUtils.create());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void givenSessionsAcrossTenantsWhenFindByServerIdThenOnlyCurrentTenantReturned() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.test";
        String serverId = "server-" + IdUtils.create();

        mcpSessionRepository.save(createSession(tenant1, namespace, serverId, "node-1"));
        mcpSessionRepository.save(createSession(tenant2, namespace, serverId, "node-2"));

        // When / Then
        assertThat(mcpSessionRepository.findByServerId(tenant1, namespace, serverId)).hasSize(1);
        assertThat(mcpSessionRepository.findByServerId(tenant2, namespace, serverId)).hasSize(1);
    }

    @Test
    void givenExistingSessionWhenSaveAgainThenUpdated() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession original = mcpSessionRepository.save(createSession(tenant));
        McpSession updated = new McpSession(
            original.tenantId(), original.namespace(), original.serverId(),
            original.sessionId(), "new-node", null
        );

        // When
        mcpSessionRepository.save(updated);

        // Then
        Optional<McpSession> found = mcpSessionRepository.find(
            tenant, original.namespace(), original.serverId(), original.sessionId()
        );
        assertThat(found).isPresent();
        assertThat(found.get().sseNode()).isEqualTo("new-node");
    }

    private static McpSession createSession(String tenantId) {
        return createSession(tenantId, "io.kestra.test", "server-" + IdUtils.create(), "node-1");
    }

    private static McpSession createSession(String tenantId, String namespace, String serverId, String sseNode) {
        return new McpSession(tenantId, namespace, serverId, IdUtils.create(), sseNode, null);
    }
}
