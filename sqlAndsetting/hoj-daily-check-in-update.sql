/*
 * Compatible upgrade for existing HOJ databases.
 * This migration only adds an independent table and does not alter existing data.
 */
CREATE TABLE IF NOT EXISTS `user_daily_check_in` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `uid` varchar(32) NOT NULL COMMENT 'User UUID',
  `check_in_date` date NOT NULL COMMENT 'Check-in date in Asia/Shanghai',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid_check_in_date` (`uid`, `check_in_date`),
  KEY `idx_check_in_date` (`check_in_date`),
  CONSTRAINT `user_daily_check_in_ibfk_1`
    FOREIGN KEY (`uid`) REFERENCES `user_info` (`uuid`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
