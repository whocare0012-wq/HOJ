/*
 * Operational recovery only.
 *
 * Run this manually after confirming that no AI worker is still processing
 * requests. This is intentionally separate from the schema upgrade.
 */
UPDATE `ai_assistant_request`
SET `status` = 'QUEUED', `started_at` = NULL
WHERE `status` = 'PROCESSING';
