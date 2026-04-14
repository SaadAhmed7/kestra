package io.kestra.webserver.models.api;

import io.kestra.core.mcp.models.Mcp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * API DTO for MCP server creation, update, and retrieval.
 * <p>
 * Decouples the public API contract from the internal {@link Mcp} domain object.
 * Read-only fields ({@code id}, {@code isDefault}, {@code created}, {@code updated}) are
 * populated in responses and ignored in request bodies.
 */
public record ApiMcp(
    @Schema(description = "Unique identifier of the MCP server, derived from its name.", accessMode = Schema.AccessMode.READ_ONLY)
    String id,

    @Schema(description = "Namespace the MCP server belongs to.")
    @NotNull @NotBlank
    String namespace,

    @Schema(description = "Unique name of the MCP server within its namespace.")
    @NotNull @NotBlank
    String name,

    @Schema(description = "Human-readable description of the MCP server.")
    String description,

    @Schema(description = "System prompt sent to the AI model when using this server.")
    String systemPrompt,

    @Schema(description = "Visibility of the server.")
    Mcp.ServerType serverType,

    @Schema(description = "Authentication type for private servers.")
    Mcp.AuthType authType,

    @Schema(description = "Whether the MCP server is enabled.")
    boolean enabled,

    @Schema(description = "URL to the server icon.")
    String iconUrl,

    @Schema(description = "Whether this is the default MCP server, auto-provisioned per tenant.", accessMode = Schema.AccessMode.READ_ONLY)
    boolean isDefault,

    @Schema(description = "Timestamp when the server was created.", accessMode = Schema.AccessMode.READ_ONLY)
    Instant created,

    @Schema(description = "Timestamp when the server was last updated.", accessMode = Schema.AccessMode.READ_ONLY)
    Instant updated
) {
    /**
     * Creates an {@link ApiMcp} response DTO from a domain {@link Mcp}.
     *
     * @param mcp the domain object
     * @return the corresponding API DTO
     */
    public static ApiMcp from(final Mcp mcp) {
        return new ApiMcp(
            mcp.id(),
            mcp.namespace(),
            mcp.name(),
            mcp.description(),
            mcp.systemPrompt(),
            mcp.serverType(),
            mcp.authType(),
            mcp.enabled(),
            mcp.iconUrl(),
            mcp.isDefault(),
            mcp.created(),
            mcp.updated()
        );
    }
}
