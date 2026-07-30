# HOJ upgrade phase 3: isolated runtime smoke verification

Phase 3 starts the upgraded backend and judge-server JAR files against disposable infrastructure. It verifies the runtime wiring that a mapper-only database test cannot cover, while remaining completely separate from production MySQL, Redis, and Nacos.

## Repeatable verifier

From the repository root on Windows, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage3.ps1
```

The verifier uses uniquely named containers and random localhost ports for:

- MySQL 8, restored only from `sqlAndsetting/hoj.sql`;
- Redis 7 with a generated password;
- Nacos 1.4.2 in standalone embedded-storage mode with authentication disabled only inside the disposable container.

It launches the already-built Java 8 backend and judge-server JAR files in hidden local processes. `-Dfile.encoding=UTF-8` matches the Linux deployment encoding and avoids Windows Java 8's platform-default encoding from corrupting non-ASCII Nacos configuration comments.

All generated passwords, ports, logs, processes, and containers are scoped to one run. A `finally` block stops only the processes and uniquely named containers created by that run, restores the caller's environment variables, and removes the verified temporary directory.

## Safety settings

The backend is always launched with these upgrade switches disabled:

```text
STARTUP_DATABASE_INITIALIZATION_ENABLED=false
PASSWORD_BCRYPT_WRITE_ENABLED=false
PASSWORD_UPGRADE_ON_LOGIN_ENABLED=false
FORCED_UPDATE_REMOTE_JUDGE_ACCOUNT=false
OPEN_REMOTE_JUDGE=false
```

The judge server is also launched with remote judging disabled. The script never accepts a production connection string and never starts an SQL migration.

## Verified result

The isolated run completed successfully on 2026-07-15 with the Java 8 artifacts produced by phase 1:

- all 48 existing tables remained present;
- the ordered column-definition signature was identical before and after startup;
- the checked-in `hoj.sql` SHA-256 value was unchanged;
- `/api/get-website-config`, `/api/languages?all=true`, and the common-announcement endpoint returned successful results;
- 61 seeded language records were readable;
- the legacy `root` account logged in with `hoj123456` and returned an authorization token;
- the legacy password hash remained unchanged because password migration was disabled;
- the judge-server health and version endpoints succeeded;
- Nacos exposed a healthy judge instance under `hoj-judgeserver`, the service name used by the backend.

Two writes are intentional in the disposable database and are asserted exactly:

1. Login creates one `session` row.
2. Judge startup creates one `judge_server` registration row.

No table creation, alteration, or deletion occurs.

## Checks still requiring a restored production backup

The checked-in seed database proves structural and baseline runtime compatibility, but it cannot represent every production data shape. Before deployment, restore a recent production backup into an isolated staging environment and repeat the following with the three migration switches disabled:

1. normal user and administrator login, logout, registration, and password reset;
2. problem viewing, search, import/export, test-case download, and file access;
3. one local submission through sandbox execution, result callback, and status display;
4. contest registration, submission, ranking, and award calculation using copied historical data;
5. group permissions, discussions, announcements, and scheduled jobs;
6. judge restart and backend restart, confirming only expected operational rows change;
7. rollback to the preserved old JAR files without restoring or downgrading the database.

Actual judging requires the production-like sandbox and compiler filesystem, so it is deliberately outside this local smoke script. Do not use production service addresses merely to complete that check.
