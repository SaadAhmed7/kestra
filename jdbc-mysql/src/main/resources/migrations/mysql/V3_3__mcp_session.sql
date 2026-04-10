CREATE TABLE IF NOT EXISTS `mcp_session` (
    `key`        VARCHAR(250) NOT NULL PRIMARY KEY,
    `value`      JSON NOT NULL,
    `tenant_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `namespace`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED,
    `server_id`  VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.serverId') STORED,
    `session_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.sessionId') STORED NOT NULL,
    `sse_node`   VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.sseNode') STORED,
    `user_id`    VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.userId') STORED,
    INDEX ix_tenant_server (tenant_id, namespace, server_id),
    INDEX ix_session (tenant_id, session_id),
    INDEX ix_sse_node (sse_node)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
