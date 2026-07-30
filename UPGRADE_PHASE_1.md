# HOJ upgrade phase 1 baseline

This phase creates a repeatable safety baseline before any further framework, database-access, or frontend migration.

## Automated gates

- Maven excludes the JUnit `integration` tag by default.
- Safe backend tests and packaging run on Java 8 and Java 17 in GitHub Actions.
- The frontend is installed with `npm ci`, audited for critical production vulnerabilities, and built with Node.js 20.
- Pull requests fail if any tracked SQL file changes.
- CI artifacts contain the Java 8 backend services and the production frontend build. CI does not deploy them.

The production dependency audit baseline recorded on 2026-07-15 is 0 critical, 1 high, 4 moderate, and 9 low findings. Phase 1 rejects critical findings. The remaining Vue 2 and ECharts ecosystem findings require breaking dependency changes and are deferred to the frontend migration phase; do not run `npm audit fix --force` on the deployment branch.

Run the same safe verification locally from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage1.ps1
```

Append `-SkipFrontend` when only the backend needs to be checked. The one-process execution-policy bypass does not change the machine-wide PowerShell policy.

## Tests that are intentionally excluded

Tests tagged `integration` may start a Spring context, connect to MySQL, Redis, or Nacos, access external judge websites, submit remote code, or write records. They must never run against production infrastructure.

Only run them manually against a disposable environment and a restored database copy. To opt in deliberately:

```powershell
mvn -B -ntp test '-DskipTests=false' '-DexcludedTestGroups=' '-Dgroups=integration'
```

## Staging checklist

1. Preserve the previous backend and judge-server JAR files, environment variables, and Nacos data IDs.
2. Create a database backup and restore it to an isolated staging MySQL instance.
3. Keep all migration switches disabled:

   ```text
   STARTUP_DATABASE_INITIALIZATION_ENABLED=false
   PASSWORD_BCRYPT_WRITE_ENABLED=false
   PASSWORD_UPGRADE_ON_LOGIN_ENABLED=false
   ```

4. Keep `SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES=true` during the Spring Boot 2.6 compatibility period.
5. Deploy the judge server first and verify its Nacos registration and health status.
6. Deploy the backend and verify Nacos configuration loading, Redis access, legacy MD5 login, registration, password reset, administration, problem import, submission, judge callback, contest ranking, and scheduled jobs.
7. Review logs for connection retry loops, secret leakage, circular dependency failures, and unexpected startup database writes.

## Rollback boundary

With the three migration switches disabled, rollback only requires restoring the previous JAR files and configuration. No HOJ database rollback or schema downgrade is required.

Do not enable BCrypt writes until every rollback artifact can authenticate both MD5 and BCrypt hashes.
