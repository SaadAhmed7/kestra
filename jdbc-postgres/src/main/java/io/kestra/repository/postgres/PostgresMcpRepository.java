package io.kestra.repository.postgres;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcMcpRepository;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresMcpRepository extends AbstractJdbcMcpRepository {
    @Inject
    public PostgresMcpRepository(@Named("mcp") PostgresRepository<Mcp> repository,
        ApplicationEventPublisher<CrudEvent<Mcp>> eventPublisher) {
        super(repository, eventPublisher);
    }
}
