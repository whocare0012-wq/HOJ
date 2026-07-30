# HOJ upgrade phase 15: Router transitions and Element Plus values

Phase 15 removes the Vue Router and Element Plus deprecations exposed by the phase-14 browser regression. It does not change a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- audited all Vue templates with the installed Vue compiler rather than multiline regular expressions;
- migrated 19 RouterView instances in four views from direct transition children to the Vue Router 4 `v-slot="{ Component }"` pattern;
- kept every existing route-name condition and transition name/mode while rendering the routed component through `<component :is="Component">`;
- migrated all 48 Element Plus radio value definitions from the deprecated `label` value API to `value` across eight views/components;
- migrated nine boolean link `:underline="false"` bindings to `underline="never"` across five views;
- added a guarded, dry-run-by-default AST migration utility for the radio and link changes;
- added a read-only AST audit that requires all three deprecated boundaries to remain at zero.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage15.ps1

The stage-15 verifier rejects SQL changes, checks representative RouterView, radio, and link syntax, invokes the full stage-14 dependency/audit/build gate, requires the three AST counts to be zero, and confirms the Element Plus migration is idempotent. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The read-only audit can also be run independently:

    node .\scripts\audit-stage15-deprecations.mjs

## Verification evidence

The production build completed after the migration and transformed 3,462 modules. The main JavaScript asset is about 6.98 MB/2.00 MB gzip and the main CSS asset remains about 987 KB/166 KB gzip. The small raw JavaScript increase comes from explicit routed-component render boundaries. The reviewed mavon-editor `eval` warning and mixed static/dynamic import notices remain unchanged.

An isolated headed Chromium session against `127.0.0.1` reproduced the phase-14 checks. The group-detail view fell from two warnings to zero while retaining its tab and overview content. The message center loaded and switched tabs with zero warnings. The account profile rendered the male, female, and private radio choices without Element Plus warnings, and selecting the female option updated its checked state without saving or issuing a mutation request. The three removed account warnings were replaced by no new warnings; the six remaining warnings on that page all originate from mavon-editor's Vue 2 component model, render functions, and lifecycle hooks. Local API proxy failures remained expected because no backend or database was started.

## Remaining compatibility boundary

`@vue/compat` remains intentionally enabled. The most visible remaining runtime compatibility boundary is mavon-editor. The clean install also reports that Vue I18n 9 is out of support, so a bounded Vue I18n 11 upgrade should precede or accompany review of vue-calendar-heatmap, vue-cropper, vue-katex-auto-render, and mavon-editor. The calendar heatmap still brings a Vue 2 peer dependency through vue-resize.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
