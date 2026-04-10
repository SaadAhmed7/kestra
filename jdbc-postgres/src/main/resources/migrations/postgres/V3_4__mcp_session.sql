CREATE TABLE IF NOT EXISTS mcp_session (
    "key"        VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"      JSONB NOT NULL,
    "tenant_id"  VARCHAR(150) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace"  VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "server_id"  VARCHAR(150) GENERATED ALWAYS AS (value ->> 'serverId') STORED,
    "session_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'sessionId') STORED,
    "sse_node"   VARCHAR(250) GENERATED ALWAYS AS (value ->> 'sseNode') STORED,
    "user_id"    VARCHAR(150) GENERATED ALWAYS AS (value ->> 'userId') STORED
);

CREATE INDEX IF NOT EXISTS mcp_session__tenant_server ON mcp_session ("tenant_id", "namespace", "server_id");
CREATE INDEX IF NOT EXISTS mcp_session__session ON mcp_session ("tenant_id", "session_id");
CREATE INDEX IF NOT EXISTS mcp_session__sse_node ON mcp_session ("sse_node");
