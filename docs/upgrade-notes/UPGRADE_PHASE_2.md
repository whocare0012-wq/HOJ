# HOJ upgrade phase 2: existing database compatibility

Phase 2 verifies that the upgraded application remains compatible with the existing HOJ database contract. It does not modify `hoj.sql`, run `hoj-update.sql`, or connect to production infrastructure.

## Automated compatibility gate

`DatabaseCompatibilityTest` discovers every entity that is actually persisted through a MyBatis-Plus `BaseMapper`. For each mapping it asks MySQL to parse the mapped table name and complete mapped column list with a read-only statement:

```sql
SELECT <all mapped columns> FROM <mapped table> WHERE 1 = 0;
```

The statement reads no rows and performs no DDL or DML. The test fails when an upgraded entity expects a table or column that the existing schema does not provide.

The local verifier creates a uniquely named disposable MySQL 8 container, restores only the checked-in `sqlAndsetting/hoj.sql`, runs the compatibility test, and removes the container in a `finally` block:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-db-compatibility.ps1
```

The same check runs in GitHub Actions on Java 8. It is separate from normal unit tests because it requires an isolated MySQL instance.

## Existing schema result

The checked-in full schema restores 48 base tables. All 48 persisted entity mappings are compatible with those tables and their existing columns under MySQL 8.

The legacy `ProblemCount` type is an aggregate query result, not a persisted table. Dead judge-server CRUD code incorrectly treated it as a `problem_count` table even though neither checked-in SQL file creates that table. Phase 2 removes that unused judge-server service and changes the backend aggregate mapper to custom-query-only, eliminating the implicit table dependency without adding or altering a database table.

## Verify a restored production backup

Restore a recent production backup into an isolated MySQL instance. Create or use an account restricted to `SELECT` on that restored `hoj` database, then set these variables only in the current shell:

```powershell
$env:HOJ_DB_COMPAT_URL = 'jdbc:mysql://127.0.0.1:3306/hoj?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:HOJ_DB_COMPAT_USERNAME = 'hoj_compat_readonly'
$env:HOJ_DB_COMPAT_PASSWORD = '<temporary password>'

Set-Location .\hoj-springboot
mvn -B -ntp -pl DataBackup -am test '-DskipTests=false' '-DexcludedTestGroups=' '-Dgroups=database-compatibility'
```

Do not point these variables at production. Although the test only issues `SELECT ... WHERE 1 = 0`, the isolated backup is also needed for later functional and rollback testing.

## Data compatibility boundary

- No SQL file or database schema is changed by this phase.
- Existing MD5 password hashes remain readable; BCrypt writes and login-time password upgrades remain disabled by default.
- Structural compatibility is automated. Business-flow checks such as login, submissions, judging, ranking, and scheduled jobs still belong in the isolated staging deployment.
