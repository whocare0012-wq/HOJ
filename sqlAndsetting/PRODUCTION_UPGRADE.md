# Production database upgrade

Application startup does not modify the database unless
`STARTUP_DATABASE_INITIALIZATION_ENABLED=true` is set explicitly.

Before deploying:

1. Stop all backend and judge workers.
2. Create and verify a restorable database backup.
3. Apply the required additive scripts exactly once in a staging copy first:
   `hoj-daily-check-in-update.sql`,
   `hoj-daily-fortune-advice-update.sql`,
   `hoj-problem-difficulty-update.sql`,
   `hoj-learning-resource-update.sql`, and
   `hoj-ai-assistant-update.sql`.
4. Start one backend instance and run the smoke tests before scaling out.

`hoj-ai-assistant-recover-queue.sql` is an operational recovery command, not
a schema migration. Run it only after confirming that no AI request is still
being processed.
