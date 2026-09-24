# HOJ upgrade phase 16: Vue I18n Composition mode

Phase 16 upgrades Vue I18n from 9.14.5 to the v11 Composition API boundary without changing a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- pinned `vue-i18n` to 11.4.2 in the frontend manifest and lockfile;
- selected 11.4.2 because it supports Node 16 and later, including the repository's Node 20 baseline, while current Vue I18n releases require Node 22;
- changed `createI18n` from legacy mode to `legacy: false` with explicit `globalInjection: true`;
- migrated all 647 component-level `$i18n.t(...)`/`$i18n.$t(...)` calls in 72 files to the injected `$t(...)` Composer API;
- migrated all seven global Composer locale reads/writes to `i18n.locale.value`;
- confirmed that no `$tc`, `v-t`, old `%{...}` interpolation, or unwrapped global locale access remains;
- added a dry-run-by-default migration utility and a read-only source/configuration audit.

The default `i18n` export remains the global Composer instance, so existing API, router, and bootstrap calls to `i18n.t(...)` keep their established contract. Language persistence and the five existing locale message sets are unchanged.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage16.ps1

The verifier rejects SQL changes, requires the exact Vue I18n version and Composition-mode configuration, checks all seven locale Ref accesses, invokes the full stage-15 clean dependency/audit/build gate, requires every audited legacy boundary to remain at zero, confirms the migration is idempotent, and verifies the installed dependency. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The read-only audit can also be run independently:

    node .\scripts\audit-stage16-i18n.mjs

## Verification evidence

The first production build after the migration completed successfully and transformed 3,462 modules. An npm security audit against the official npm registry reported zero vulnerabilities. The configured npm mirror does not implement the audit endpoint, so a mirror-side 404 is not treated as a dependency finding.

The existing mavon-editor `eval` warning and mixed static/dynamic import notices remain unchanged.

An isolated headed Chromium session against `127.0.0.1` rendered the Chinese home page, switched the complete navigation/content surface to English, then switched it to Japanese. `Web_Language` moved from `zh-CN` to `en-US` and then `ja-JP`; a reload retained Japanese and rendered the Japanese navigation again. The complete flow reported zero browser warnings, including zero Vue I18n legacy or compatibility warnings. The nine console errors were expected local `/api` 500 responses because no backend or database was started.

## Remaining compatibility boundary

`@vue/compat` remains intentionally enabled. The next high-value frontend boundary is the remaining Vue 2-oriented dependency set, especially mavon-editor and the Vue 2 peer dependency brought in through vue-calendar-heatmap/vue-resize. Those packages need isolated replacement or compatibility work rather than being mixed into the Vue I18n upgrade.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
