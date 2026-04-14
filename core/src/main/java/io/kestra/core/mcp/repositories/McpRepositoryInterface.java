package io.kestra.core.mcp.repositories;

import java.util.Optional;

import io.kestra.core.mcp.models.Mcp;
import io.kestra.core.repositories.ArrayListTotal;
import io.micronaut.data.model.Pageable;

public interface McpRepositoryInterface {

    Optional<Mcp> get(String tenantId, String id);

    Optional<Mcp> findByName(String tenantId, String name);

    ArrayListTotal<Mcp> list(Pageable pageable, String tenantId);

    Mcp save(Mcp previousMcp, Mcp mcp);

    Optional<Mcp> delete(String tenantId, String id);
}
