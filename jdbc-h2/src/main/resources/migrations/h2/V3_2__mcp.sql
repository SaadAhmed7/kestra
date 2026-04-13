CREATE TABLE IF NOT EXISTS mcp (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "name" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "description" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.description')),
    "system_prompt" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.systemPrompt')),
    "server_type" VARCHAR(50) GENERATED ALWAYS AS (JQ_STRING("value", '.serverType')),
    "auth_type" VARCHAR(50) GENERATED ALWAYS AS (JQ_STRING("value", '.authType')),
    "enabled" BOOLEAN GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.enabled')),
    "icon_url" VARCHAR(500) GENERATED ALWAYS AS (JQ_STRING("value", '.iconUrl')),
    "is_default" BOOLEAN GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.isDefault')),
    "deleted" BOOLEAN NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp__deleted_tenant ON mcp ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__id_deleted_tenant ON mcp ("id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__namespace_deleted_tenant ON mcp ("namespace", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__enabled_deleted_tenant ON mcp ("enabled", "deleted", "tenant_id");
