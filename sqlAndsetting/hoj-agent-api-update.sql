-- MySQL 8.0. Back up all databases before applying. Run with the HOJ database selected.
-- Additive and re-runnable: creates the AI data API token/audit tables and one judge index
-- only when missing. The index build adds I/O on a large judge table; run outside peak hours.

-- Access tokens for /api/agent/v1. Only the SHA-256 digest of each token is stored.
CREATE TABLE IF NOT EXISTS `agent_access_token` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `token_name` varchar(80) NOT NULL,
  `token_prefix` varchar(20) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime DEFAULT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT 0,
  `created_by` varchar(32) NOT NULL,
  `last_used_at` datetime DEFAULT NULL,
  `last_used_ip` varchar(64) DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_token_hash` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Audit log: one row per authenticated AI data API request.
CREATE TABLE IF NOT EXISTS `agent_access_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `token_id` bigint unsigned NOT NULL,
  `endpoint` varchar(200) NOT NULL,
  `query_string` varchar(2000) DEFAULT NULL,
  `result_count` int DEFAULT NULL,
  `status_code` int NOT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_log_token` (`token_id`,`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Date-range lookups of the AI data API: resolves the submit_id range of a period.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='judge' AND index_name='idx_judge_submit_time'), 'SELECT 1', 'CREATE INDEX idx_judge_submit_time ON judge(submit_time,submit_id)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
