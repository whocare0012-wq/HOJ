# Production database upgrade

For a fresh, empty database, `hoj.sql` now includes the difficulty configuration
table, default levels, OJ points columns, and points views. It seeds no user
account. The CI compatibility job imports this file into an isolated MySQL 8
instance. Never run this destructive seed against an existing database.

Application startup does not modify the database unless
`STARTUP_DATABASE_INITIALIZATION_ENABLED=true` is set explicitly.

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
   `hoj-learning-resource-update.sql`, and
   `hoj-ai-assistant-update.sql`.
4. If the verified migration requires maintenance, schedule a window for the
   affected HOJ services only. Preserve the shared frontend proxy and unrelated
   services. Never run a production migration or restart from these local tests.
5. Verify schema and service health after the authorized production change.

`hoj-ai-assistant-recover-queue.sql` is an operational recovery command, not
a schema migration. Run it only after confirming that no AI request is still
being processed.
