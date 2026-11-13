-- MySQL 8.0. Back up all databases before applying. Run with the HOJ database selected.
-- Requires hoj-agent-api-update.sql to have been applied first.
-- Additive and re-runnable: enables the AI management API (/api/agent/v1/admin/**) for problems and trainings.

-- Token scopes: "read" (submission data API) and/or "manage" (problem & training management).
-- Existing tokens keep read-only access.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='agent_access_token' AND column_name='scopes'), 'SELECT 1', 'ALTER TABLE agent_access_token ADD COLUMN `scopes` varchar(100) NOT NULL DEFAULT ''read'' AFTER `token_hash`');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Change audit: one row per write made through the management API, with before/after snapshots.
CREATE TABLE IF NOT EXISTS `agent_change_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `token_id` bigint unsigned NOT NULL,
  `operator_uid` varchar(32) DEFAULT NULL,
  `operator_username` varchar(100) DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `target_type` varchar(20) NOT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `before_json` mediumtext,
  `after_json` mediumtext,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_change_target` (`target_type`,`target_id`),
  KEY `idx_agent_change_token` (`token_id`,`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
