package io.kestra.core.repositories;

import java.util.Optional;

import io.kestra.core.models.mcp.Mcp;
import io.micronaut.data.model.Pageable;

public interface McpRepositoryInterface {

    Optional<Mcp> get(String tenantId, String id);

    ArrayListTotal<Mcp> list(Pageable pageable, String tenantId);

    Mcp save(Mcp previousMcp, Mcp mcp);

    Optional<Mcp> delete(String tenantId, String id);
}
