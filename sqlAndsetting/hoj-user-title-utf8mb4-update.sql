-- Run manually against a backed-up database; never run during application startup.
-- Existing NULLs and values are preserved. Re-running produces the same schema.
ALTER TABLE `hoj`.`user_info`
  MODIFY COLUMN `title_name` varchar(255)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NULL DEFAULT NULL COMMENT '头衔、称号';
