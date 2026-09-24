# HOJ upgrade phase 4: isolated sandbox judge flow

Phase 4 extends the disposable phase-three environment with the checked-in Go Judge sandbox binary. It verifies an authenticated C++ online-judge request across the complete service integration path without connecting to production infrastructure.

## Repeatable verifier

Run from the repository root on Windows with Docker Desktop using Linux amd64 containers:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage4.ps1
```

The script reuses all phase-three checks, then starts `sandbox/Sandbox-amd64-v1.8.0` in a uniquely named privileged container. The default compiler runtime image on this workstation is `judge0/judge0:latest`; a production-like runtime can be selected explicitly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage4.ps1 `
  -SandboxRuntimeImage '<approved compiler runtime image>' `
  -SandboxPort 15050
```

The verifier refuses to use a sandbox port that already has a listener. It removes only its uniquely named processes and containers in a `finally` block.

## Sandbox address compatibility

The judge server previously hard-coded `http://localhost:5050`. It now retains that address as the default but supports either of these overrides:

```text
SANDBOX_BASE_URL=http://127.0.0.1:15050
-Dhoj.sandbox.base-url=http://127.0.0.1:15050
```

Whitespace and trailing slashes are normalized. Java 8 and Java 17 unit tests cover the legacy default and override normalization.

This is a configuration-only compatibility improvement. It does not change any database table, column, or persisted value.

## Verified request path

The successful 2026-07-15 run exercised this path:

```text
legacy root login
  -> backend authenticated online-judge API
  -> Redis test-judge queue
  -> Nacos judge-server discovery
  -> upgraded judge-server JAR
  -> Go Judge 1.8.0
  -> C++ compile and execution
  -> Redis result
  -> backend result polling API
```

The submitted program read `20 22`, produced `42`, and returned judge status `Accepted`. The judge-server task counter returned to zero after dispatch. The full flow passed once on the legacy port 5050 and again on the non-default port 15050, proving that the packaged judge server honors `SANDBOX_BASE_URL`.

The database column signature and all 48 table definitions remained unchanged. The only disposable database writes were asserted exactly:

1. one temporary public problem used by the online-judge request;
2. one login `session` row;
3. one `judge_server` registration row and its temporary task-counter updates.

Online test judging does not create a persistent `judge` submission row, and the verifier asserts that the `judge` row count does not change. All data disappears when the disposable MySQL container is removed.

## Remaining production-like acceptance boundary

Phase 4 proves service integration, C++ compilation, execution, and result delivery. It does not yet prove compiler-version parity or the normal submission path that reads uploaded test-case files from `/judge/test_case` and persists `judge`, `judge_case`, ranking, and accepted-problem records.

Before deployment, phase 5 should use:

- a recent production database backup restored to isolated MySQL;
- a copy of test-case and special-judge files restored to isolated storage;
- the same compiler/sandbox image used by the server;
- one ordinary accepted submission, one wrong answer, one compile error, and one time-limit case;
- assertions for `judge`, `judge_case`, `user_acproblem`, contest records, task counters, and rollback with the old JAR files.

Do not point the phase-four or future phase-five scripts at production service addresses.
