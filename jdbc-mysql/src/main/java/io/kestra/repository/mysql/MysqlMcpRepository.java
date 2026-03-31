package io.kestra.repository.mysql;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcMcpRepository;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlMcpRepository extends AbstractJdbcMcpRepository {
    @Inject
    public MysqlMcpRepository(@Named("mcp") MysqlRepository<Mcp> repository,
        ApplicationEventPublisher<CrudEvent<Mcp>> eventPublisher) {
        super(repository, eventPublisher);
    }
}
