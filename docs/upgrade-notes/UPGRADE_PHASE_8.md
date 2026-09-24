# HOJ upgrade phase 8: final Java 8 framework line

Phase 8 moves the already verified compatibility bridge to the final Spring Boot line that still supports Java 8. It changes framework dependencies and build artifacts only; it does not change a database table, SQL file, entity mapping, or persisted value.

## Version boundary

The backend and JudgeServer now use:

- Spring Boot `2.7.18`;
- Spring Cloud `2021.0.9`;
- Spring Cloud Alibaba `2021.0.6.0`;
- the explicitly pinned Nacos client `1.4.2`.

Spring Boot 2.7.18 requires Java 8 and is compatible through Java 21. It is the last open-source Spring Boot 2.x release. The project remains compiled for Java 8 so that the existing server runtime and rollback boundary are preserved.

The Nacos client pin is deliberate. Both service JAR files continue to package `nacos-client-1.4.2.jar`, and the isolated runtime verifier still starts a Nacos 1.4.2 server. Replacing the production Nacos server or changing its stored configuration is not part of this phase.

Official compatibility references:

- <https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html#getting-started.system-requirements>
- <https://github.com/spring-cloud/spring-cloud-release/releases/tag/v2021.0.9>
- <https://github.com/alibaba/spring-cloud-alibaba/releases/tag/2021.0.6.0>

## Repeatable verifier

Run from the repository root with Docker Desktop, MySQL client tools, Maven, and the existing Java 8 installation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage8.ps1
```

The verifier:

1. rejects tracked or untracked SQL changes;
2. asserts the three framework versions and the Nacos 1.4.2 pin directly from the POM files;
3. forces Maven to run with Java 8;
4. runs all safe unit tests and packages both services;
5. inspects the executable JAR contents for the expected Spring Boot, Spring Cloud Alibaba, and Nacos client artifacts;
6. runs the phase-three smoke test against uniquely named disposable MySQL 8, Redis 7, and Nacos 1.4.2 containers;
7. verifies that the checked-in `hoj.sql` hash did not change.

Use `-SkipIsolatedRuntime` only for a build-only check. It does not replace the default isolated runtime gate.

## Verified result

The successful 2026-07-15 run used Java 8u202 and completed 11 safe unit tests with zero failures or errors. Both executable service JAR files contained Spring Boot 2.7.18, Spring Cloud Alibaba 2021.0.6.0, and only Nacos client 1.4.2.

The same safe build and 11 tests also passed on Java 17, matching the existing CI matrix. A separate Java 8 database-compatibility run restored all 48 seed tables and completed `DatabaseCompatibilityTest` with one test, zero failures, and zero errors. Its ordered schema signature remained `f706caf105311fc5abbfa8820ee165a1b99d290579ca55d967d9693e6fcc92e2`.

The isolated runtime smoke test then verified:

- all 48 existing tables and the ordered column signature remained unchanged;
- the legacy MD5 root account logged in without rewriting its password hash;
- 61 seeded languages and the public configuration endpoints remained readable;
- the upgraded JudgeServer health and version endpoints succeeded;
- Nacos 1.4.2 registered a healthy `hoj-judgeserver` instance;
- only the expected disposable login `session` and `judge_server` registration rows were written.

No automatic database migration ran, and no production service or database address was used.

## Rollback and remaining acceptance boundary

After the phase-eight versions were fixed in the POM, phase 6 was rerun from the pre-upgrade commit. The Spring Boot 2.7.18 services persisted Accepted, Wrong Answer, Compile Error, and Time Limit Exceeded results. The rollback Spring Boot 2.2.6 services then started against the same disposable database, read all four results, and persisted another Accepted submission. The table count, ordered schema signature, legacy password hash, checked-in SQL hash, and judge task counter all remained unchanged or returned to their expected values. No database downgrade was needed.

Repeat the extended isolated gates when changing any backend dependency:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-db-compatibility.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage6.ps1
```

These commands use the repository seed and the locally selected sandbox image. They are regression gates, not substitutes for the phase-seven restored-production-copy rehearsal.

Before deployment, the phase-seven production-copy gate still requires:

1. a recent production database logical backup and independently recorded SHA-256;
2. a copied production `/judge` tree and trusted tree hash;
3. independently copied backend and JudgeServer JAR files currently deployed in production, with trusted SHA-256 values;
4. the exact production sandbox/compiler image reference and Docker image ID;
5. an approved staging-only account and representative historical problem, contest, group, file, and submission identifiers.

Use these materials only in isolated staging. Do not connect a verifier to production, do not enable startup initialization or password-write switches, and do not deploy until the restored-copy upgrade and rollback rehearsal passes.

The later production-copy intake status is recorded in `UPGRADE_PHASE_9.md`: the judge tree, deployed rollback JAR files, and exact compiler image are now staged and verified; the production logical database backup remains unavailable.
