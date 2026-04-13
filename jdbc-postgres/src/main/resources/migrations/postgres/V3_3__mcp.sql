CREATE TABLE IF NOT EXISTS mcp (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "name" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'name') STORED,
    "description" TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    "system_prompt" TEXT GENERATED ALWAYS AS (value ->> 'systemPrompt') STORED,
    "server_type" VARCHAR(50) GENERATED ALWAYS AS (value ->> 'serverType') STORED,
    "auth_type" VARCHAR(50) GENERATED ALWAYS AS (value ->> 'authType') STORED,
    "enabled" BOOLEAN GENERATED ALWAYS AS (CAST(value ->> 'enabled' AS BOOLEAN)) STORED,
    "icon_url" VARCHAR(500) GENERATED ALWAYS AS (value ->> 'iconUrl') STORED,
    "is_default" BOOLEAN GENERATED ALWAYS AS (CAST(value ->> 'isDefault' AS BOOLEAN)) STORED,
    "deleted" BOOLEAN NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOLEAN)) STORED,
    "fulltext" TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'name' AS VARCHAR))
    ) STORED,
    "created" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp__deleted_tenant ON mcp ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__id_deleted_tenant ON mcp ("id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__namespace_deleted_tenant ON mcp ("namespace", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__enabled_deleted_tenant ON mcp ("enabled", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__fulltext ON mcp USING GIN (fulltext);

CREATE OR REPLACE TRIGGER mcp_updated BEFORE UPDATE
    ON mcp FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
