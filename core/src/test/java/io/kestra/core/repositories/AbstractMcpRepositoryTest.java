package io.kestra.core.repositories;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public abstract class AbstractMcpRepositoryTest {

    @Inject
    private McpRepositoryInterface mcpRepository;

    @Test
    void givenNewMcpWhenSaveThenPersistedWithTimestamps() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Mcp mcp = createMcp(tenant);

        // When
        Mcp saved = mcpRepository.save(null, mcp);

        // Then
        assertThat(saved.id()).isEqualTo(mcp.id());
        assertThat(saved.title()).isEqualTo(mcp.title());
        assertThat(saved.namespace()).isEqualTo(mcp.namespace());
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.deleted()).isFalse();
        assertThat(saved.created()).isNotNull();
        assertThat(saved.updated()).isNotNull();
    }

    @Test
    void givenExistingMcpWhenGetThenReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Mcp saved = mcpRepository.save(null, createMcp(tenant));

        // When
        Optional<Mcp> found = mcpRepository.get(tenant, saved.id());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.id());
        assertThat(found.get().title()).isEqualTo(saved.title());
    }

    @Test
    void givenUnknownIdWhenGetThenEmpty() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<Mcp> found = mcpRepository.get(tenant, "non-existent-id");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void givenExistingMcpWhenUpdateThenChangesPersistedAndCreatedPreserved() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Mcp original = mcpRepository.save(null, createMcp(tenant));
        Mcp updated = new Mcp(tenant, original.id(), original.namespace(),
            "Updated Title", "Updated description", false, false, null, null);

        // When
        Mcp result = mcpRepository.save(original, updated);

        // Then
        assertThat(result.title()).isEqualTo("Updated Title");
        assertThat(result.description()).isEqualTo("Updated description");
        assertThat(result.enabled()).isFalse();
        assertThat(result.created()).isEqualTo(original.created());
        assertThat(result.updated()).isAfterOrEqualTo(original.updated());
    }

    @Test
    void givenUnchangedMcpWhenSaveThenPreviousReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Mcp original = mcpRepository.save(null, createMcp(tenant));

        // When
        Mcp result = mcpRepository.save(original, original);

        // Then
        assertThat(result).isEqualTo(original);
    }

    @Test
    void givenExistingMcpWhenDeleteThenSoftDeletedAndNoLongerVisible() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Mcp saved = mcpRepository.save(null, createMcp(tenant));

        // When
        Optional<Mcp> deleted = mcpRepository.delete(tenant, saved.id());

        // Then
        assertThat(deleted).isPresent();
        assertThat(deleted.get().deleted()).isTrue();
        assertThat(mcpRepository.get(tenant, saved.id())).isEmpty();
    }

    @Test
    void givenUnknownIdWhenDeleteThenEmpty() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<Mcp> result = mcpRepository.delete(tenant, "non-existent-id");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void givenMultipleMcpsWhenListThenAllReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpRepository.save(null, createMcp(tenant));
        mcpRepository.save(null, createMcp(tenant));

        // When
        ArrayListTotal<Mcp> results = mcpRepository.list(Pageable.from(1, 10), tenant);

        // Then
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.getTotal()).isEqualTo(2);
    }

    @Test
    void givenDeletedMcpWhenListThenExcludedFromResults() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Mcp toDelete = mcpRepository.save(null, createMcp(tenant));
        mcpRepository.save(null, createMcp(tenant));
        mcpRepository.delete(tenant, toDelete.id());

        // When
        ArrayListTotal<Mcp> results = mcpRepository.list(Pageable.from(1, 10), tenant);

        // Then
        assertThat(results.size()).isEqualTo(1);
    }

    @Test
    void givenMcpsAcrossTenantsWhenListThenOnlyCurrentTenantReturned() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpRepository.save(null, createMcp(tenant1));
        mcpRepository.save(null, createMcp(tenant2));

        // When / Then
        assertThat(mcpRepository.list(Pageable.from(1, 10), tenant1).size()).isEqualTo(1);
        assertThat(mcpRepository.list(Pageable.from(1, 10), tenant2).size()).isEqualTo(1);
    }

    private static Mcp createMcp(String tenantId) {
        String id = IdUtils.create();
        return new Mcp(tenantId, id, "io.kestra.test", "Test MCP " + id, "A test MCP server", true, false, null, null);
    }
}
