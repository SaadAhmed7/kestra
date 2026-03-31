CREATE TABLE IF NOT EXISTS `mcp` (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED,
    `tenant_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED,
    `title` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.title') STORED,
    `description` TEXT GENERATED ALWAYS AS (value ->> '$.description') STORED,
    `enabled` BOOL GENERATED ALWAYS AS (value ->> '$.enabled' = 'true') STORED,
    `flow_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.flowId') STORED,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_deleted_tenant (deleted, tenant_id),
    INDEX ix_id_deleted_tenant (id, deleted, tenant_id),
    INDEX ix_namespace_flow_deleted_tenant (namespace, flow_id, deleted, tenant_id),
    INDEX ix_enabled_deleted_tenant (enabled, deleted, tenant_id),
    FULLTEXT ix_fulltext (title)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
