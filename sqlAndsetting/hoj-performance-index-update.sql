-- MySQL 8.0. Back up all databases before applying. Run with the HOJ database selected.
-- Re-runnable: each index is created only when missing. InnoDB builds them online,
-- but on a large judge table expect extra I/O; run outside peak hours.

-- Submission status list: WHERE cid=0 AND cpid=0 AND gid IS NULL|=? ... ORDER BY submit_time DESC, submit_id DESC.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='judge' AND index_name='idx_judge_list'), 'SELECT 1', 'CREATE INDEX idx_judge_list ON judge(cid,cpid,gid,submit_time,submit_id)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Home page weekly statistics and today's submission count range scans.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='judge' AND index_name='idx_judge_gmt_create'), 'SELECT 1', 'CREATE INDEX idx_judge_gmt_create ON judge(gmt_create)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Scheduled rejudge of submissions stuck in Pending.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='judge' AND index_name='idx_judge_status_modified'), 'SELECT 1', 'CREATE INDEX idx_judge_status_modified ON judge(status,gmt_modified)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Scheduled cleanup of sessions older than six months.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='session' AND index_name='idx_session_gmt_create'), 'SELECT 1', 'CREATE INDEX idx_session_gmt_create ON session(gmt_create)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
