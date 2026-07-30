/*
 * Compatible upgrade for existing HOJ databases.
 *
 * This migration adds independent fortune and advice configuration. It never
 * drops or truncates existing user and check-in data and is safe to execute
 * more than once.
 */
CREATE TABLE IF NOT EXISTS `daily_fortune_config` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL COMMENT '运势唯一编码',
  `name` varchar(32) NOT NULL COMMENT '运势名称',
  `color` varchar(7) NOT NULL DEFAULT '#409EFF' COMMENT '展示颜色',
  `description` varchar(255) DEFAULT NULL COMMENT '运势说明',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `legacy_score` int(11) NOT NULL DEFAULT '64' COMMENT '兼容旧版运势分数',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_fortune_code` (`code`),
  KEY `idx_daily_fortune_enabled_sort` (`enabled`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `daily_fortune_advice` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `fortune_type` varchar(32) NOT NULL COMMENT '运势类型',
  `advice_type` varchar(16) NOT NULL COMMENT 'recommended 或 avoid',
  `title` varchar(100) NOT NULL COMMENT '宜忌事项',
  `description` varchar(255) DEFAULT NULL COMMENT '事项说明',
  `sort_order` int(11) NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `usage_count` bigint(20) unsigned NOT NULL DEFAULT '0' COMMENT '使用次数',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fortune_advice_type` (`fortune_type`, `advice_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @daily_fortune_has_enabled = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'daily_fortune_advice'
    AND COLUMN_NAME = 'enabled'
);
SET @daily_fortune_enabled_sql = IF(
  @daily_fortune_has_enabled = 0,
  'ALTER TABLE `daily_fortune_advice` ADD COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 AFTER `sort_order`',
  'SELECT 1'
);
PREPARE daily_fortune_enabled_stmt FROM @daily_fortune_enabled_sql;
EXECUTE daily_fortune_enabled_stmt;
DEALLOCATE PREPARE daily_fortune_enabled_stmt;

SET @daily_fortune_has_usage = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'daily_fortune_advice'
    AND COLUMN_NAME = 'usage_count'
);
SET @daily_fortune_usage_sql = IF(
  @daily_fortune_has_usage = 0,
  'ALTER TABLE `daily_fortune_advice` ADD COLUMN `usage_count` bigint(20) unsigned NOT NULL DEFAULT 0 AFTER `enabled`',
  'SELECT 1'
);
PREPARE daily_fortune_usage_stmt FROM @daily_fortune_usage_sql;
EXECUTE daily_fortune_usage_stmt;
DEALLOCATE PREPARE daily_fortune_usage_stmt;

INSERT INTO `daily_fortune_config`
(`code`,`name`,`color`,`description`,`sort_order`,`enabled`,`legacy_score`)
SELECT
  seed.`code`,
  seed.`name`,
  seed.`color`,
  seed.`description`,
  seed.`sort_order`,
  seed.`enabled`,
  seed.`legacy_score`
FROM (
  SELECT 'great_luck' AS `code`, '大吉' AS `name`, '#25B864' AS `color`,
         '运势极佳，万事如意' AS `description`, 1 AS `sort_order`,
         1 AS `enabled`, 100 AS `legacy_score`
  UNION ALL
  SELECT 'medium_luck','中吉','#6FCF97','运势顺遂，稳中有进',2,1,88
  UNION ALL
  SELECT 'small_luck','小吉','#A3D977','运势尚可，小有收获',3,1,76
  UNION ALL
  SELECT 'neutral','中平','#90A4AE','运势平平，稳中求进',4,1,64
  UNION ALL
  SELECT 'bad_luck','凶','#FA8C16','运势欠佳，谨慎行事',5,1,42
  UNION ALL
  SELECT 'great_bad_luck','大凶','#F5222D','运势不佳，宜静不宜动',6,1,20
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `daily_fortune_config`);
