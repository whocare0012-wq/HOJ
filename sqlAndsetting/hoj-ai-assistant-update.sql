-- AI 助手配置、API Key 池与请求队列。
-- 生产环境通过此文件显式升级；应用启动默认不执行数据库变更。

CREATE TABLE IF NOT EXISTS `ai_assistant_config` (
  `id` tinyint unsigned NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `base_url` varchar(255) NOT NULL DEFAULT 'https://api.openai.com/v1',
  `model` varchar(100) NOT NULL DEFAULT 'gpt-4o-mini',
  `daily_limit` int unsigned NOT NULL DEFAULT 12,
  `request_interval_seconds` int unsigned NOT NULL DEFAULT 10,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `ai_assistant_config`
  (`id`, `enabled`, `base_url`, `model`, `daily_limit`, `request_interval_seconds`)
VALUES
  (1, 0, 'https://api.openai.com/v1', 'gpt-4o-mini', 12, 10);

CREATE TABLE IF NOT EXISTS `ai_assistant_api_key` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `key_name` varchar(80) NOT NULL,
  `api_key` varchar(1024) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `last_used_at` datetime DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_key_available` (`enabled`, `last_used_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `ai_assistant_api_key`
  MODIFY COLUMN `api_key` varchar(1024) NOT NULL;

CREATE TABLE IF NOT EXISTS `ai_assistant_request` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `uid` varchar(32) NOT NULL,
  `username` varchar(64) NOT NULL,
  `problem_id` bigint unsigned NOT NULL,
  `problem_display_id` varchar(255) NOT NULL,
  `problem_title` varchar(255) NOT NULL,
  `training_id` bigint DEFAULT NULL,
  `source_type` varchar(20) NOT NULL,
  `request_type` varchar(20) NOT NULL,
  `language` varchar(50) DEFAULT NULL,
  `request_content` longtext NOT NULL,
  `response_content` longtext DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'QUEUED',
  `error_message` varchar(1000) DEFAULT NULL,
  `api_key_id` bigint unsigned DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_request_queue` (`status`, `gmt_create`, `id`),
  KEY `idx_ai_request_user_daily` (`uid`, `gmt_create`, `id`),
  KEY `idx_ai_request_problem` (`uid`, `problem_id`, `source_type`, `gmt_create`, `id`),
  CONSTRAINT `fk_ai_request_key`
    FOREIGN KEY (`api_key_id`) REFERENCES `ai_assistant_api_key` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
