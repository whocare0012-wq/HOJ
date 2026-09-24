# HOJ upgrade phase 14: Vue 3 slot syntax

Phase 14 completes the structure-aware migration of the remaining Element UI-style `slot="..."` attributes to Vue 3 named-slot syntax. It does not change a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- added an AST-based single-file-component migration utility backed by the installed Vue compiler packages;
- audited and migrated 188 legacy slot attributes across 64 Vue files;
- wrapped 183 direct component children with explicit `<template #name>` slot boundaries;
- promoted the conditional JudgeCase card header to `<template v-if="!isSubtask" #header>`;
- removed four ineffective `slot="header"` attributes whose direct parents were ordinary HTML elements, preserving their existing content position;
- retained all conditions, loops, bindings, events, and child content while changing only slot ownership and indentation;
- covered the `header`, `label`, `content`, `reference`, `footer`, `append`, `trigger`, and `error` named slots;
- made the migration idempotent: a post-migration dry run reports zero legacy slots and zero changed files.

The migration utility defaults to a dry run. The `--expect=N` guard prevents a write when the audited legacy-slot count does not match the expected count, and `--write` must be supplied explicitly to edit files.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage14.ps1

The stage-14 verifier rejects SQL changes, verifies representative Vue 3 boundaries for all eight reviewed slot names, checks the conditional JudgeCase header, invokes the complete stage-13 dependency/audit/build gate, and then runs the AST migration utility with `--expect=0`. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The migration utility can also be run independently without editing files:

    node .\scripts\migrate-stage14-slots.mjs --expect=0

## Verification evidence

The production build completed after migrating all 188 attributes and transformed 3,462 modules. The main JavaScript asset remains about 6.98 MB/2.00 MB gzip and the main CSS asset remains about 987 KB/166 KB gzip. The reviewed mavon-editor `eval` warning and mixed static/dynamic import notices remain unchanged.

An isolated headed Chromium session against `127.0.0.1` verified public home, problem, status, group-detail, and account-setting views plus the administrator import view. Card headers, image fallbacks, group tab labels, popover references, the email input append button, and all four upload trigger buttons rendered in their expected component regions. A dummy administrator identity existed only in that isolated browser storage and was removed with the browser profile. Local API proxy failures were expected because no backend or database was started. No slot-migration or `GLOBAL_EXTEND` compatibility warning appeared.

The browser run also exposed separate, pre-existing upgrade boundaries: router views placed directly inside transitions, Element Plus radio `label` values and boolean link `underline`, and mavon-editor's Vue 2 render/v-model lifecycle compatibility warnings. These are intentionally left for the next bounded phase rather than mixed into the slot migration.

## Remaining compatibility boundary

`@vue/compat` remains intentionally enabled. The next local-only work is to migrate router-view transition boundaries and current Element Plus deprecated APIs, then review or replace vue-calendar-heatmap, vue-cropper, vue-katex-auto-render, and mavon-editor. The calendar heatmap still brings a Vue 2 peer dependency through vue-resize, while mavon-editor remains the largest third-party Vue 2 compatibility boundary.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
