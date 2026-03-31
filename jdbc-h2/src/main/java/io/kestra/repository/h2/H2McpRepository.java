package io.kestra.repository.h2;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcMcpRepository;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2McpRepository extends AbstractJdbcMcpRepository {
    @Inject
    public H2McpRepository(@Named("mcp") H2Repository<Mcp> repository,
        ApplicationEventPublisher<CrudEvent<Mcp>> eventPublisher) {
        super(repository, eventPublisher);
    }
}
