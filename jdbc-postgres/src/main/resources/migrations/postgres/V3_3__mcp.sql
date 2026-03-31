CREATE TABLE IF NOT EXISTS mcp (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "tenant_id" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "title" VARCHAR(250) GENERATED ALWAYS AS (value ->> 'title') STORED,
    "description" TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    "enabled" BOOLEAN GENERATED ALWAYS AS (CAST(value ->> 'enabled' AS BOOLEAN)) STORED,
    "flow_id" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    "deleted" BOOLEAN NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOLEAN)) STORED,
    "fulltext" TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value ->> 'title' AS VARCHAR))
    ) STORED,
    "created" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp__deleted_tenant ON mcp ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__id_deleted_tenant ON mcp ("id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__namespace_flow_deleted_tenant ON mcp ("namespace", "flow_id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__enabled_deleted_tenant ON mcp ("enabled", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS mcp__fulltext ON mcp USING GIN (fulltext);

CREATE OR REPLACE TRIGGER mcp_updated BEFORE UPDATE
    ON mcp FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
