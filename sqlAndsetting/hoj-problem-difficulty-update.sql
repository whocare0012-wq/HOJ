CREATE TABLE IF NOT EXISTS `problem_difficulty_config` (
  `difficulty_value` int unsigned NOT NULL,
  `display_text` varchar(20) NOT NULL,
  `border_color` varchar(7) NOT NULL,
  `sort_order` int unsigned NOT NULL DEFAULT 0,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`difficulty_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `problem_difficulty_config`
  (`difficulty_value`, `display_text`, `border_color`, `sort_order`)
VALUES
  (0, '简单', '#19BE6B', 0),
  (1, '中等', '#2D8CF0', 1),
  (2, '困难', '#ED3F14', 2);
