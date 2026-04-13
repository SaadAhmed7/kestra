package io.kestra.core.models.mcp;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.IdUtils;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Represents an MCP (Model Context Protocol) server configuration.
 */
public record Mcp(
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId,

    @Hidden
    String id,

    @NotNull
    @NotBlank
    String namespace,

    @NotNull
    @NotBlank
    String name,

    String description,

    String systemPrompt,

    ServerType serverType,

    AuthType authType,

    boolean enabled,

    String iconUrl,

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    boolean isDefault,

    @Hidden
    boolean deleted,

    @Hidden
    Instant created,

    @Hidden
    Instant updated
) implements HasUID, SoftDeletable<Mcp> {

    /** The well-known name of the default MCP server, auto-provisioned per tenant. */
    public static final String DEFAULT_NAME = "default";

    /**
     * Controls the visibility of the MCP server.
     */
    public enum ServerType {
        PRIVATE,
        PUBLIC;

        @JsonCreator
        public static ServerType fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, ServerType.class);
        }
    }

    /**
     * Authentication type for private MCP servers.
     * Only relevant when {@link ServerType} is {@link ServerType#PRIVATE}.
     */
    public enum AuthType {
        BASIC,
        API_TOKEN,
        OAUTH2;

        @JsonCreator
        public static AuthType fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, AuthType.class);
        }
    }

    /**
     * Derives {@code id} from {@code name} when not provided, applies defaults for
     * {@code serverType} and {@code authType}, and computes the read-only {@code isDefault} flag.
     */
    public Mcp {
        if (id == null && name != null) {
            id = IdUtils.from(name);
        }
        if (serverType == null) {
            serverType = ServerType.PRIVATE;
        }
        if (authType == null) {
            authType = AuthType.BASIC;
        }
        isDefault = DEFAULT_NAME.equals(name);
    }

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDeleted() {
        return deleted;
    }

    /** {@inheritDoc} */
    @Override
    public Mcp toDeleted() {
        return new Mcp(tenantId, id, namespace, name, description, systemPrompt,
            serverType, authType, enabled, iconUrl, isDefault, true, created, updated);
    }
}
