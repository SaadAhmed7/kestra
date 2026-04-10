CREATE TABLE IF NOT EXISTS mcp_session (
    "key"        VARCHAR(250) NOT NULL PRIMARY KEY,
    "value"      TEXT NOT NULL,
    "tenant_id"  VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace"  VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "server_id"  VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.serverId')),
    "session_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.sessionId')),
    "sse_node"   VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.sseNode')),
    "user_id"    VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.userId'))
);

CREATE INDEX IF NOT EXISTS mcp_session__tenant_server ON mcp_session ("tenant_id", "namespace", "server_id");
CREATE INDEX IF NOT EXISTS mcp_session__session ON mcp_session ("tenant_id", "session_id");
CREATE INDEX IF NOT EXISTS mcp_session__sse_node ON mcp_session ("sse_node");
