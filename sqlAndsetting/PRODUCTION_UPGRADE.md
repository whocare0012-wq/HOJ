# Production database upgrade

For a fresh, empty database, `hoj.sql` now includes the difficulty configuration
table, default levels, OJ points columns, and points views. It seeds no user
account. The CI compatibility job imports this file into an isolated MySQL 8
instance. Never run this destructive seed against an existing database.

Application startup does not modify the database unless
`STARTUP_DATABASE_INITIALIZATION_ENABLED=true` is set explicitly.

## 2026-11-13: AI management API

`hoj-agent-manage-update.sql` (apply after `hoj-agent-api-update.sql`) adds
the `agent_access_token.scopes` column and the `agent_change_log` table used by
`/api/agent/v1/admin/**`, which lets AI agents create and edit problems, test
data and trainings. It is additive and re-runnable; existing tokens keep
read-only access (`scopes = 'read'`). `hoj.sql` contains the same DDL for fresh
installs. Until the script is applied, every token is treated as read-only and
the admin page cannot create tokens with the management scope; nothing else
changes.

The management API runs as the token's creator (an enabled root account),
supports `dry_run` previews and never deletes problems or trainings. Every write
is recorded in `agent_change_log` with before/after snapshots.

## 2026-11-02: AI data API

`hoj-agent-api-update.sql` adds the `agent_access_token` and `agent_access_log`
tables used by `/api/agent/v1` (read-only submission export for AI agents) and
the `idx_judge_submit_time` index on `judge(submit_time, submit_id)`. It is
additive and re-runnable; existing data is not modified. The index build adds
I/O on a large `judge` table, so run it outside peak hours. `hoj.sql` contains
the same DDL for fresh installs. Until the script is applied, the admin page
and the API return database errors; the rest of HOJ is unaffected.

Only root accounts can create tokens, and a token stops working as soon as its
creator is no longer an enabled root account. Submissions from contests that
have not ended are never returned.

## 2026-09-05: user titles

`hoj-user-title-utf8mb4-update.sql` widens only `user_info.title_name` to
`utf8mb4_unicode_ci`, retaining its length, NULL/default and existing values.
The seed schema has the same explicit column charset. Re-running is safe but
may acquire a table lock; inspect the table size and test on an isolated copy.
Create a new full backup and verify recovery before any production execution.
The application does not apply this migration automatically.

Verify with `information_schema.COLUMNS`, then test a Chinese title and a
four-byte emoji on an isolated database. Do not downgrade this column to `utf8`
after storing four-byte characters: rolling back application code does not
require rolling back the widened column. This change has not been deployed.

Before deploying:

1. Recheck current service health, mounts, networks and active Nginx configuration.
2. Create a new full MySQL backup, verify its checksum and test restoration in an
   isolated environment. Never overwrite an older backup.
3. Apply the required additive scripts exactly once in a staging copy first:
   `hoj-daily-check-in-update.sql`,
   `hoj-daily-fortune-advice-update.sql`,
   `hoj-problem-difficulty-update.sql`,
   `hoj-learning-resource-update.sql`,
   `hoj-ai-assistant-update.sql`,
   `hoj-performance-index-update.sql` (index-only; builds online but adds I/O
   on a large `judge` table, so run it outside peak hours), and
   `hoj-agent-api-update.sql` (two new tables plus one `judge` index; same
   off-peak advice), then `hoj-agent-manage-update.sql` (one column plus one
   new table).
4. If the verified migration requires maintenance, schedule a window for the
   affected HOJ services only. Preserve the shared frontend proxy and unrelated
   services. Never run a production migration or restart from these local tests.
5. Verify schema and service health after the authorized production change.

`hoj-ai-assistant-recover-queue.sql` is an operational recovery command, not
a schema migration. Run it only after confirming that no AI request is still
being processed.
