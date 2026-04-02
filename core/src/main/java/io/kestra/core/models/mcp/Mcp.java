package io.kestra.core.models.mcp;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.utils.IdUtils;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record Mcp(
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId,

    @NotNull
    @NotBlank
    String id,

    @NotNull
    @NotBlank
    String namespace,

    String flowId,

    @NotNull
    @NotBlank
    String title,

    // ToDo: rename to instruction to match mcp spec
    String description,

    boolean enabled,

    @Hidden
    boolean deleted,

    @Hidden
    Instant created,

    @Hidden
    Instant updated
) implements HasUID, SoftDeletable<Mcp> {

    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(tenantId, id);
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public Mcp toDeleted() {
        return new Mcp(tenantId, id, namespace, flowId, title, description, enabled, true, created, updated);
    }
}
