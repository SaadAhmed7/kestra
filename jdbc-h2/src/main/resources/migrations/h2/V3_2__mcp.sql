CREATE TABLE IF NOT EXISTS mcp (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "title" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.title')),
    "description" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.description')),
    "enabled" BOOLEAN GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.enabled')),
    "flow_id" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "deleted" BOOLEAN NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.title')),
    "created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp__deleted_tenant ON mcp ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__id_deleted_tenant ON mcp ("id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__namespace_flow_deleted_tenant ON mcp ("namespace", "flow_id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__enabled_deleted_tenant ON mcp ("enabled", "deleted", "tenant_id");
