-- MySQL 8.0. Back up all databases before applying. Run with the HOJ database selected.
-- Re-runnable: historical denominators are captured once, never overwritten on rerun.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='problem_difficulty_config' AND column_name='base_points'), 'SELECT 1', 'ALTER TABLE problem_difficulty_config ADD base_points DECIMAL(10,2) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE problem_difficulty_config SET base_points=CASE difficulty_value WHEN 0 THEN 10 WHEN 1 THEN 20 WHEN 2 THEN 40 ELSE 10 END WHERE base_points IS NULL;
ALTER TABLE problem_difficulty_config MODIFY base_points DECIMAL(10,2) NOT NULL DEFAULT 10;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='judge' AND column_name='score_type'), 'SELECT 1', 'ALTER TABLE judge ADD score_type INT NULL, ADD score_max INT NULL, ADD score_snapshot_estimated TINYINT(1) NOT NULL DEFAULT 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE judge j JOIN problem p ON p.id=j.pid
SET j.score_type=p.type,j.score_max=p.io_score,j.score_snapshot_estimated=1,j.gmt_modified=j.gmt_modified
WHERE j.score_type IS NULL;

-- Cover the normalized progress scan without repeatedly fetching submission code/text rows.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='judge' AND index_name='idx_oj_points'), 'SELECT 1', 'CREATE INDEX idx_oj_points ON judge(cid,gid,uid,pid,status,score_type,score_max,score,score_snapshot_estimated)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS oj_points_change_log (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 operator_uid VARCHAR(32) NULL,
 target_type VARCHAR(20) NOT NULL,
 target_id BIGINT NOT NULL,
 target_name VARCHAR(255) NOT NULL,
 old_value DECIMAL(10,2) NULL,
 new_value DECIMAL(10,2) NULL,
 affected_problems BIGINT NOT NULL DEFAULT 0,
 affected_users BIGINT NOT NULL DEFAULT 0,
 gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_points_log_created (gmt_create)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Only completed, valid results can earn points. Contest submissions never enter this view.
CREATE OR REPLACE VIEW oj_submission_progress AS
SELECT j.uid,j.pid,j.gid,j.status,j.score_snapshot_estimated,
 CASE WHEN j.status=0 THEN CAST(1 AS DECIMAL(30,16))
      WHEN j.score_type=1 AND j.score_max>0 AND j.score IS NOT NULL
       AND j.status IN (-3,-1,1,2,3,8)
      THEN LEAST(1,GREATEST(0,CAST(j.score AS DECIMAL(30,16))/j.score_max))
      ELSE CAST(0 AS DECIMAL(30,16)) END AS completion_ratio
FROM judge j WHERE j.cid=0;

CREATE OR REPLACE VIEW oj_problem_points AS
SELECT s.uid,s.pid,s.gid,s.completion_ratio,s.accepted,
 (s.completion_ratio>s.confirmed_ratio) AS estimated,
 COALESCE(d.base_points,0) AS base_points,
 ROUND(COALESCE(d.base_points,0)*s.completion_ratio,2) AS points
FROM (
 SELECT uid,pid,gid,MAX(completion_ratio) AS completion_ratio,
  MAX(status=0) AS accepted,
  MAX(CASE WHEN score_snapshot_estimated=0 OR status=0 THEN completion_ratio ELSE 0 END) AS confirmed_ratio
 FROM oj_submission_progress GROUP BY uid,pid,gid
) s JOIN problem p ON p.id=s.pid
LEFT JOIN problem_difficulty_config d ON d.difficulty_value=p.difficulty;
