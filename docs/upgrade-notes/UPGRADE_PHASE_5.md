# HOJ upgrade phase 5: persistent local judging

Phase 5 verifies the normal persistent submission path against disposable infrastructure. It extends phase 4 from online test judging to database-backed submissions that read test-case files from shared `/judge` storage and persist the result.

## Repeatable verifier

Run from the repository root on Windows with Docker Desktop using Linux amd64 containers:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage5.ps1
```

The verifier starts uniquely named MySQL 8, Redis 7, Nacos 1.4.2, Go Judge, and JudgeServer containers. The backend remains a hidden host process. Go Judge and JudgeServer mount the same generated directory at `/judge`, which reproduces the filesystem contract used by ordinary HOJ submissions.

The default compiler runtime used by this workstation is `judge0/judge0:latest`. Supply the approved production compiler image before staging acceptance:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage5.ps1 `
  -SandboxRuntimeImage '<approved compiler runtime image>'
```

The script does not accept database, Redis, or Nacos connection strings. It cannot be pointed at production services. All generated data, secrets, logs, containers, the Docker network, and shared test-case files are removed in a `finally` block.

## Backend/JudgeServer response compatibility fix

After the Spring Boot and Jackson upgrade, the backend could serialize `CommonResult` but could no longer deserialize the JSON returned by JudgeServer. A completed judge request was consequently reported as a judge-server connection failure.

`CommonResult` now exposes an explicit Jackson property-based constructor for `status`, `data`, and `msg`. The fields remain immutable, the response JSON shape is unchanged, and a focused unit test covers the JudgeServer response shape. This change does not touch the database.

## Legacy judge-name limit

The existing `judge.judger` column is `varchar(20)`. The disposable JudgeServer therefore uses a unique name shorter than 20 characters. Production `JUDGE_SERVER_NAME` values must also remain within that existing limit; the documented defaults such as `hoj-judger-1` and `judger-1` already comply.

The verifier deliberately preserves this legacy limit instead of widening the column.

## Verified result

The successful 2026-07-15 run exercised this path:

```text
legacy root login
  -> authenticated ordinary submission API
  -> judge row and Redis dispatch
  -> Nacos JudgeServer discovery
  -> containerized upgraded JudgeServer
  -> shared /judge/test_case data
  -> Go Judge compilation and execution
  -> judge/judge_case/user_acproblem persistence
  -> authenticated submission-detail API
```

One seeded C++ problem used input `20 22`, expected output `42`, and a 200 ms time limit. Four independent submissions produced the exact expected terminal statuses:

1. Accepted (`0`);
2. Wrong Answer (`-1`);
3. Compile Error (`-2`), including non-empty compiler diagnostics;
4. Time Limit Exceeded (`1`).

The judge task counter returned to zero. The Accepted relationship referenced the correct user and problem. Submission detail returned the same persisted status as the database.

The verifier also asserted the exact disposable writes:

- one `problem` row;
- one `problem_case` row;
- four `judge` rows;
- three `judge_case` rows (Compile Error has no executed case);
- one `user_acproblem` row;
- one login `session` row;
- one `judge_server` row.

All 48 table definitions remained present. The ordered database column signature was identical before and after all four submissions, the legacy root password hash remained unchanged, and the checked-in `sqlAndsetting/hoj.sql` SHA-256 value did not change.

## Production rollout gate

This phase passes the repository-seed acceptance test, but it is not authorization to deploy directly to production. Before rollout:

1. create and checksum a fresh production database backup without changing production data;
2. copy the production `/judge` data and restore both copies into an isolated staging environment;
3. run the upgraded backend and JudgeServer against that restored staging copy with startup initialization and password-write switches disabled;
4. repeat AC, WA, CE, and TLE submissions with the production compiler image, then exercise historical problem, contest, group, ranking, and file records;
5. preserve the current backend and JudgeServer JAR files and deployment configuration;
6. stop new submissions, deploy the new JAR files, run read-only health checks, then reopen traffic;
7. if acceptance fails, stop the upgraded services and restore the old JAR files and configuration. No database downgrade is expected because phases 1-5 make no schema migration.

Do not test rollback for the first time on production. The old JAR rollback must first succeed against the restored staging copy after the upgraded JARs have run.
