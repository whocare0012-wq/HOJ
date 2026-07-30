# HOJ upgrade phase 9: production-copy intake and portable identity gates

Phase 9 closes every production-copy prerequisite that can be completed without connecting to the production database. It does not deploy code, restart a production service, change a database table, execute a migration, or copy production credentials into this repository.

## Safety boundary

The production host was used only for read-only operating-system, Docker metadata, file identity, and bind-mount inventory. The currently deployed backend and JudgeServer JAR files and the persistent static judge inputs were copied to `D:\hoj-staging-input`, outside this Git repository. Temporary JAR export files on the server were removed after their local hashes matched.

No production MySQL connection, query, logical export, configuration-secret read, container restart, deployment, commit, or push was performed.

## Staged production-copy inputs

The external source manifest is `D:\hoj-staging-input\SOURCE_MANIFEST.md`. It contains no password, database credential, application secret, username, or password hash.

| Input | Local identity |
| --- | --- |
| Persistent judge inputs | `37,376` files, `2,672,583,265` bytes, tree SHA-256 `A727F7A6F90EEE6F942F357C324108C1B1E2F38BED9D706282AF35AAE324CE5B` |
| Deployed backend JAR | `125,250,161` bytes, SHA-256 `BA422E5919005AAEA85F7F750CE0C9548D5D65FA7DC704A4A212E468EC03725F` |
| Deployed JudgeServer JAR | `65,752,592` bytes, SHA-256 `93B82FEBF183F2791FBD9A847F43581F455FF23668C13CA3A215DB27ED368220` |
| JudgeServer/compiler image | `registry.cn-shenzhen.aliyuncs.com/hcode/hoj_judgeserver@sha256:6c85513645079eb48b3650dd5675ac307c6f813a63b4030bf0dcc032a18b3c6a` |

The judge tree reconstructs only the stable bind-mounted inputs: `test_case`, `spj`, and the empty `interactive` directory. Mutable `run` and log directories are intentionally excluded. The source and local trees contain no symbolic links or reparse points.

The immutable image was pulled locally by repository digest. The production Docker engine reports configuration image ID `sha256:53e9688cba9813d9630158682779660b9c7b4411f0c648511f381d08b27bdadc`, while Docker Desktop with the containerd image store reports the manifest digest as its local ID. Because engine-local IDs are not portable, the verifier now treats the immutable repository digest as the primary identity and retains image-ID validation as an optional same-engine check.

## Verifier corrections

`scripts/verify-stage7-inputs.ps1` now:

1. sorts judge-data manifest lines with explicit ordinal semantics so Windows and Linux produce the same tree hash;
2. accepts `-ExpectedSandboxRepositoryDigest` in immutable `repository@sha256:<64 hex>` form;
3. still accepts `-ExpectedSandboxImageId` for backward-compatible same-engine checks;
4. requires at least one independently recorded sandbox identity outside inventory mode.

`scripts/verify-stage7.ps1` passes both optional sandbox identities through to the input verifier.

## Isolated verification result

The real judge tree, deployed rollback JAR files, and exact production JudgeServer/compiler image passed the strict input gate. The repository seed was used only as an explicitly labelled database fixture because the production logical backup is still unavailable.

The composed fixture run restored 48 tables into a disposable MySQL container and completed `DatabaseCompatibilityTest` with one test, zero failures, and zero errors. Its ordered schema signature remained:

```text
f706caf105311fc5abbfa8820ee165a1b99d290579ca55d967d9693e6fcc92e2
```

The disposable database was removed afterward. This proves the corrected verifier works with the real file and image inputs; it does not claim compatibility with historical production rows.

## Remaining blocker and next gate

The only missing production-copy artifact is a recent logical backup supplied by an authorized database operator as `.sql` or `.sql.gz`, together with a SHA-256 independently calculated on the source side. It should be placed outside this repository, for example at `D:\hoj-staging-input\production.sql.gz`.

After that copy arrives, the remaining isolated work is:

1. verify the backup hash and reject repository seed data;
2. restore it into a newly created disposable MySQL container;
3. run read-only entity-to-column compatibility checks and record aggregate schema evidence only;
4. select representative non-sensitive historical identifiers from the restored copy;
5. create or approve a staging-only account inside the disposable environment;
6. run upgraded Accepted, Wrong Answer, Compile Error, and Time Limit Exceeded judging against the copied test cases and pinned image;
7. start the copied production rollback JAR files against the same disposable database and confirm historical and newly created staging reads;
8. recheck the database schema and judge tree identities.

Do not connect these verifiers to production. Do not proceed to deployment until the restored-production-copy upgrade and rollback rehearsal passes and the user explicitly approves deployment.

While the production database backup remains unavailable, the independent frontend build-chain work continued in `UPGRADE_PHASE_10.md` without connecting to production.
