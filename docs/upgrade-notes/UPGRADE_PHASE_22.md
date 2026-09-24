# HOJ upgrade phase 22: native Vue 3 runtime

## Implemented changes

- Removed the `@vue/compat` dependency and lock entry.
- Removed the Vite `vue -> @vue/compat` alias and MODE 2 compiler configuration.
- Removed `configureCompat`, global component MODE overrides, and the temporary compat runtime used by `md-editor-v3`.
- Removed component-local compatibility flags from the Markdown editor, cropper, calendar heatmap, and introduction route.
- Kept Vue and `@vue/compiler-sfc` pinned together at 3.5.39.

## Repeatable verification

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage22.ps1
```

The gate rejects reintroduction of `@vue/compat`, MODE 2, compatibility runtime files, reviewed Vue 2 source APIs, and retired Vue 2 plugin packages. It then performs a clean dependency install, npm security audit against the official registry, dependency identity check, and Vite production build.

## Verification evidence

- 0 npm audit vulnerabilities.
- 5,481 modules transformed by the native Vue 3 production build.
- No `@vue/compat` dependency, lock entry, alias, compiler mode, runtime import, or component compatibility configuration remains.
- The final browser matrix used the all-container stack restored from the verified production database, uploaded-file, and test-case copies. Public, authenticated, detail, and administrator routes completed with zero HTTP failures, page errors, console errors, or warnings.
- The exercised data-dependent routes included problem and historical-submission details, training and contest problem lists, group announcements, discussion details, user home, settings, introduction, administrator problem editing, and the administrator dashboard.
- Markdown editing and live preview accepted a legacy `NULL` signature after the editor boundary normalized it to an empty string. Avatar selection, cropper initialization, rotation, and crop preview also completed under the native Vue 3 runtime.
- A historical uploaded image was read successfully through `/api/public/file/**`, proving that the copied `/hoj/file` layout remains compatible.
- Real C++ submissions against copied production test cases returned Accepted (`0`), Wrong Answer (`-1`), Compile Error (`-2`), and Time Limit Exceeded (`1`). Compiler diagnostics and 15 judge-case rows were persisted, and JudgeServer task counters returned to zero.
- The compatibility run retained all 48 existing tables and the same information-schema signature before and after judging. It added only the four expected disposable judge rows, their judge-case results, and one Accepted relation; it did not change problem or uploaded-file metadata.
- The final backend build completed 13 tests (11 backend and 2 JudgeServer), including the missing-`onlyMine` group regression. The group endpoint now treats an omitted nullable Boolean as `false` instead of auto-unboxing `null`.

## Repeatable production-copy rehearsal

Run `scripts/start-local-stack.ps1` with explicit database and uploaded-file SHA-256 values plus the copied judge directory, then run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local-stack.ps1
```

The verifier checks container health, legacy login, historical rows, uploaded files, the missing-`onlyMine` regression, all four real judge outcomes, expected database writes, released task counters, and an unchanged database schema. Stop and delete the disposable stack with `scripts/stop-local-stack.ps1` after browser acceptance.

## Remaining production boundary

Local code and compatibility acceptance are complete. Production deployment is still intentionally out of scope: no production file, database, process, container, or service was modified or restarted. Before deployment, take fresh server-side database and `/hoj/file` backups, record the running image/JAR versions, deploy the already-tested artifacts with startup migrations disabled, and retain the baseline backend and JudgeServer JARs for immediate rollback.
