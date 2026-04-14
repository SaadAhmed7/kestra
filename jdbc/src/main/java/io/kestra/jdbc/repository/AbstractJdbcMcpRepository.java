package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.mcp.models.Mcp;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.mcp.repositories.McpRepositoryInterface;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class AbstractJdbcMcpRepository extends AbstractJdbcCrudRepository<Mcp> implements McpRepositoryInterface {
    private final ApplicationEventPublisher<CrudEvent<Mcp>> eventPublisher;

    public AbstractJdbcMcpRepository(io.kestra.jdbc.AbstractJdbcRepository<Mcp> jdbcRepository,
        ApplicationEventPublisher<CrudEvent<Mcp>> eventPublisher) {
        super(jdbcRepository);
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Mcp> get(String tenantId, String id) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Record record = context
                    .select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("id", String.class).eq(id))
                    .fetchAny();
                return record == null ? Optional.empty() : Optional.of(jdbcRepository.map(record));
            });
    }

    @Override
    public Optional<Mcp> findByName(String tenantId, String name) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Record record = context
                    .select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("name", String.class).eq(name))
                    .fetchAny();
                return record == null ? Optional.empty() : Optional.of(jdbcRepository.map(record));
            });
    }

    @Override
    public ArrayListTotal<Mcp> list(Pageable pageable, String tenantId) {
        return findPage(pageable, tenantId, DSL.noCondition());
    }

    @Override
    public Mcp save(Mcp previousMcp, Mcp mcp) {
        if (previousMcp != null && previousMcp.equals(mcp)) {
            return previousMcp;
        }

        Mcp toSave;
        if (previousMcp == null) {
            toSave = new Mcp(mcp.tenantId(), mcp.id(), mcp.namespace(),
                mcp.name(), mcp.description(), mcp.systemPrompt(), mcp.serverType(), mcp.authType(),
                mcp.enabled(), mcp.iconUrl(), mcp.isDefault(), mcp.deleted(),
                Instant.now(), Instant.now());
        } else {
            toSave = new Mcp(mcp.tenantId(), mcp.id(), mcp.namespace(),
                mcp.name(), mcp.description(), mcp.systemPrompt(), mcp.serverType(), mcp.authType(),
                mcp.enabled(), mcp.iconUrl(), mcp.isDefault(), mcp.deleted(),
                previousMcp.created(), Instant.now());
        }

        this.jdbcRepository.persist(toSave);
        this.eventPublisher.publishEvent(CrudEvent.of(previousMcp, toSave));

        return toSave;
    }

    @Override
    public Optional<Mcp> delete(String tenantId, String id) {
        Optional<Mcp> mcp = this.get(tenantId, id);
        if (mcp.isEmpty()) {
            return Optional.empty();
        }

        Mcp deleted = mcp.get().toDeleted();
        this.jdbcRepository.persist(deleted);
        this.eventPublisher.publishEvent(CrudEvent.delete(mcp.get()));

        return Optional.of(deleted);
    }
}
