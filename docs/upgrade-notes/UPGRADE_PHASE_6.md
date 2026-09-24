# HOJ upgrade phase 6: isolated deployment rollback rehearsal

Phase 6 proves that the deployment can return to the pre-upgrade backend and JudgeServer without a database downgrade. It runs both service generations in sequence against the same disposable MySQL, Redis, Nacos, Go Judge, and `/judge` test-case data.

## Repeatable verifier

Run from the repository root on Windows with Docker Desktop and Java 8:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage6.ps1
```

The default rollback source is the repository's pre-upgrade commit:

```text
57d8b5280d96c0aa2d836b6dd1c8d8d1cd216962
```

Use `-RollbackCommit` only when the commit is the exact version preserved from the deployed server. The verifier exports that commit into the system temporary directory and never checks it out over the upgraded working tree.

## Rebuilding the rollback baseline

The historical backend and JudgeServer POM files did not declare a Spring Boot Maven plugin version. A current Maven installation therefore selected Spring Boot Maven plugin 4.1.0, which requires Java 17 and cannot produce the Java 8 rollback artifacts.

The verifier applies `scripts/rollback-baseline-build.patch` only to the disposable source export. It pins the historical plugin to `2.2.6.RELEASE`, matching the Spring Boot dependency version used by that commit. No historical Java source, resource, checked-in POM, or database file is modified.

Both rollback JAR files are then built with Java 8. Their SHA-256 values are printed for the individual rehearsal log, and the temporary source tree and JAR files are removed afterward.

## Rollback network topology

The upgraded JudgeServer supports a configurable sandbox URL. The historical JudgeServer uses `localhost:5050`. During phase 6, both JudgeServer containers share the Go Judge container's network namespace in sequence:

```text
host backend -> published JudgeServer port
                    |
             shared network namespace
             +-----------------------+
             | JudgeServer           |
             | Go Judge localhost:5050|
             +-----------------------+
```

Both also mount the same generated directory at `/judge`. This reproduces the historical localhost and filesystem contracts without changing either rollback JAR.

## Verified sequence

The successful 2026-07-15 rehearsal performed these operations:

1. restore the unchanged checked-in schema into isolated MySQL 8;
2. start the upgraded backend, upgraded JudgeServer, Redis 7, Nacos 1.4.2, and Go Judge;
3. submit and persist Accepted, Wrong Answer, Compile Error, and Time Limit Exceeded;
4. stop the upgraded backend and JudgeServer without replacing or restoring the database;
5. start the backend and JudgeServer built from the pre-upgrade commit;
6. log in through the rollback backend with the legacy root password;
7. read all four upgraded submissions through the rollback submission-detail API and verify their exact statuses;
8. submit another Accepted solution through the rollback backend and rollback JudgeServer;
9. verify all persisted row counts, task counters, schema signature, password hash, and checked-in SQL hash.

The exact disposable writes were:

- one `problem` row;
- one `problem_case` row;
- five `judge` rows;
- four `judge_case` rows;
- two `user_acproblem` rows, one for each Accepted submission;
- two login `session` rows;
- one `judge_server` row, replaced in place when the rollback JudgeServer started.

The judge task counter returned to zero. All 48 tables and their ordered column-definition signature remained unchanged. The legacy root password hash did not change, and `sqlAndsetting/hoj.sql` remained byte-for-byte unchanged.

Phase 5 was rerun after the phase-six network changes and passed again, proving that the normal upgraded-only topology was not regressed.

## Safety and remaining acceptance boundary

The script accepts image names and a Git rollback commit, but no MySQL, Redis, Nacos, or production service address. It creates unique container, network, port, secret, and temporary-path values and removes all of them in `finally` blocks.

This proves code-level rollback against the repository seed. The remaining pre-production gate requires user-supplied copies of production data:

1. a fresh, checksummed production database backup restored into an isolated staging database;
2. a copied production `/judge` directory mounted only in staging;
3. the exact compiler image and service configuration used by production;
4. preserved SHA-256 values and copies of the currently deployed backend and JudgeServer JAR files;
5. the same upgrade-then-rollback sequence against representative historical users, problems, contests, groups, files, and submissions.

Do not point this verifier at production and do not treat the seed-data rehearsal as a substitute for a restored-backup rehearsal.
