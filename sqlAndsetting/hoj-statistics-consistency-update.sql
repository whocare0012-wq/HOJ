/*
 * Repair cached statistics and add the uniqueness guarantees required by the
 * application. Back up the affected tables before applying this migration.
 */
USE `hoj`;

START TRANSACTION;

/* A user can like the same discussion/comment only once. */
DELETE duplicate_like
FROM discussion_like duplicate_like
INNER JOIN discussion_like retained_like
        ON retained_like.uid = duplicate_like.uid
       AND retained_like.did = duplicate_like.did
       AND retained_like.id < duplicate_like.id;

DELETE duplicate_like
FROM comment_like duplicate_like
INNER JOIN comment_like retained_like
        ON retained_like.uid = duplicate_like.uid
       AND retained_like.cid = duplicate_like.cid
       AND retained_like.id < duplicate_like.id;

UPDATE discussion d
SET d.like_num = (
    SELECT COUNT(*)
    FROM discussion_like dl
    WHERE dl.did = d.id
);

UPDATE comment c
SET c.like_num = (
    SELECT COUNT(*)
    FROM comment_like cl
    WHERE cl.cid = c.id
);

/* Discussion comment_num includes visible top-level comments and replies. */
UPDATE discussion d
SET d.comment_num = (
    SELECT COUNT(*)
    FROM comment c
    WHERE c.did = d.id
      AND c.status = 0
) + (
    SELECT COUNT(*)
    FROM reply r
    INNER JOIN comment c ON c.id = r.comment_id
    WHERE c.did = d.id
      AND c.status = 0
      AND r.status = 0
);

/*
 * Rebuild the solved-problem cache from accepted public submissions.
 * MIN(submit_id) retains the first accepted submission for each user/problem.
 */
DROP TEMPORARY TABLE IF EXISTS canonical_user_acproblem;
CREATE TEMPORARY TABLE canonical_user_acproblem (
    uid varchar(32) NOT NULL,
    pid bigint(20) unsigned NOT NULL,
    submit_id bigint(20) unsigned NOT NULL,
    PRIMARY KEY (uid, pid)
) ENGINE=InnoDB;

INSERT INTO canonical_user_acproblem (uid, pid, submit_id)
SELECT uid, pid, MIN(submit_id)
FROM judge
WHERE status = 0
  AND cid = 0
  AND gid IS NULL
GROUP BY uid, pid;

DELETE FROM user_acproblem;

INSERT INTO user_acproblem (uid, pid, submit_id, gmt_create, gmt_modified)
SELECT cache.uid,
       cache.pid,
       cache.submit_id,
       judge.gmt_create,
       CURRENT_TIMESTAMP
FROM canonical_user_acproblem cache
INNER JOIN judge ON judge.submit_id = cache.submit_id;

DROP TEMPORARY TABLE canonical_user_acproblem;

COMMIT;

/* Add indexes only when upgrading a database that does not already have them. */
DROP PROCEDURE IF EXISTS add_statistics_unique_indexes;
DELIMITER $$
CREATE PROCEDURE add_statistics_unique_indexes()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'discussion_like'
          AND index_name = 'uk_discussion_like_uid_did'
    ) THEN
        ALTER TABLE discussion_like
            ADD UNIQUE KEY uk_discussion_like_uid_did (uid, did);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'comment_like'
          AND index_name = 'uk_comment_like_uid_cid'
    ) THEN
        ALTER TABLE comment_like
            ADD UNIQUE KEY uk_comment_like_uid_cid (uid, cid);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'user_acproblem'
          AND index_name = 'uk_user_acproblem_uid_pid'
    ) THEN
        ALTER TABLE user_acproblem
            ADD UNIQUE KEY uk_user_acproblem_uid_pid (uid, pid);
    END IF;
END$$
DELIMITER ;

CALL add_statistics_unique_indexes();
DROP PROCEDURE add_statistics_unique_indexes;
