# HOJ upgrade phase 7: production-copy staging gate

Phase 7 prepares a guarded rehearsal against copies of production data. It never connects to a production database and does not contain a production host, port, account, or service address.

## Current status

The integrity and isolated database-compatibility tooling is complete and has passed its fixture self-test. On 2026-07-15, a read-only production inventory supplied the copied `/judge` inputs, both currently deployed rollback JAR files, and the immutable JudgeServer/compiler image digest. Those inputs are staged outside the Git repository and have passed the input verifier.

The real-data acceptance run is not complete because a recent production MySQL logical backup and its independently recorded source-side SHA-256 have not been supplied. No production database connection, query, export, or migration was performed while collecting the other artifacts.

The JAR files currently under `hoj-springboot/*/target` are upgraded build outputs, not independently copied rollback artifacts. The SQL files under `sqlAndsetting` are repository seed or maintenance files, not production backups.

This distinction is intentional: a seed-data rehearsal cannot prove compatibility with historical production data.

## Safety contract

The phase-seven tools enforce these boundaries:

1. accept only a local `.sql` or `.sql.gz` logical backup;
2. require an expected SHA-256 before a supplied backup can be restored;
3. reject the checked-in `sqlAndsetting/hoj.sql` seed by default;
4. validate the copied `/judge` tree and reject symbolic links or reparse points;
5. validate that both rollback artifacts are executable Spring Boot JAR files with the expected application classes;
6. pin the sandbox runtime by immutable repository digest, with Docker image ID retained as an optional same-engine check;
7. restore into a new disposable MySQL container with a random password and host port;
8. execute only read-only entity-to-column compatibility queries from the upgraded backend;
9. print only aggregate row and password-format counts, never usernames or password hashes;
10. remove the disposable container and temporary decompressed backup in `finally` blocks.

There is no automatic migration, `ALTER TABLE`, or write-back to the backup file. No repository SQL file is modified.

## Step 1: transfer and inventory the copies

Place the production copies in a staging-only directory outside the Git repository. Preserve transfer hashes in an independently controlled manifest. Do not paste production credentials into this repository or chat.

Run the inventory command first:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage7-inputs.ps1 `
  -DatabaseBackup 'D:\hoj-staging-input\production.sql.gz' `
  -JudgeDataDirectory 'D:\hoj-staging-input\judge' `
  -DeployedBackendJar 'D:\hoj-staging-input\rollback\hoj-backend.jar' `
  -DeployedJudgeJar 'D:\hoj-staging-input\rollback\hoj-judgeServer.jar' `
  -SandboxRuntimeImage 'registry.example/hoj_judgeserver@sha256:REPLACE_WITH_PRODUCTION_DIGEST' `
  -InventoryOnly
```

The JSON output records:

- database-backup size and SHA-256;
- deterministic `/judge` file count, total bytes, and tree SHA-256;
- both deployed JAR sizes and SHA-256 values;
- immutable sandbox repository digest and the engine-local image ID.

Compare these values with the transfer manifest or hashes recorded on the source server. Do not use a value produced only after an untrusted transfer as its own proof of identity.

## Step 2: run the verified isolated restore

After the copied artifacts have been independently matched, run the composed gate with all expected identities:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage7.ps1 `
  -DatabaseBackup 'D:\hoj-staging-input\production.sql.gz' `
  -JudgeDataDirectory 'D:\hoj-staging-input\judge' `
  -DeployedBackendJar 'D:\hoj-staging-input\rollback\hoj-backend.jar' `
  -DeployedJudgeJar 'D:\hoj-staging-input\rollback\hoj-judgeServer.jar' `
  -SandboxRuntimeImage 'registry.example/hoj_judgeserver@sha256:REPLACE_WITH_PRODUCTION_DIGEST' `
  -ExpectedDatabaseBackupSha256 'REPLACE_WITH_64_HEX_CHARACTERS' `
  -ExpectedJudgeDataSha256 'REPLACE_WITH_64_HEX_CHARACTERS' `
  -ExpectedBackendJarSha256 'REPLACE_WITH_64_HEX_CHARACTERS' `
  -ExpectedJudgeJarSha256 'REPLACE_WITH_64_HEX_CHARACTERS' `
  -ExpectedSandboxRepositoryDigest 'registry.example/hoj_judgeserver@sha256:REPLACE_WITH_PRODUCTION_DIGEST'
```

The gate stops before restore if any identity differs. If the identities match, it restores the backup into disposable MySQL and checks every persisted upgraded entity mapping with a `SELECT ... WHERE 1=0` query. This detects missing or incompatible columns without reading sensitive row values and without writing data.

## Fixture self-test evidence

The composed gate was self-tested on 2026-07-15 with the repository seed, a generated temporary `/judge/test_case` tree, the upgraded build artifacts, and the locally available sandbox image. The normally forbidden seed input was enabled only with `-AllowRepositorySeedFixture`.

The self-test restored 48 tables. The ordered schema signature was:

```text
f706caf105311fc5abbfa8820ee165a1b99d290579ca55d967d9693e6fcc92e2
```

`DatabaseCompatibilityTest` completed with one test, zero failures, and zero errors. The disposable database and temporary input tree were removed afterward.

`-AllowRepositorySeedFixture` exists only to test the verifier itself. Do not use it for a production-copy acceptance run.

## Remaining runtime acceptance

Passing the isolated mapping gate proves that the upgraded persistence layer can address the restored schema. It does not yet prove end-to-end behavior for historical rows and files.

The remaining run must use an approved staging-only account and representative, non-sensitive identifiers selected from the restored copy. It should verify:

1. legacy password login without silently rewriting the password hash;
2. historical problem, contest, group, file, and submission reads;
3. compilation and judging using copied test cases and the pinned sandbox image;
4. Accepted, Wrong Answer, Compile Error, and Time Limit Exceeded persistence;
5. upgraded-service shutdown followed by startup of the copied rollback JAR files against the same restored database;
6. rollback reads of both historical and newly created staging submissions;
7. unchanged table count, ordered schema signature, and copied `/judge` tree hash after the rehearsal.

Create or approve the staging account inside the isolated restored environment; do not reuse or disclose a production password. The runtime part can continue after the missing production logical backup and its trusted source-side hash are available locally. Representative non-sensitive identifiers can then be selected from the restored copy without querying production.
