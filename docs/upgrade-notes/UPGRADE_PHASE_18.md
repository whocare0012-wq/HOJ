# HOJ upgrade phase 18: native Vue 3 calendar heatmap

Phase 18 retires the remaining Vue 2-only calendar heatmap, tooltip, and resize dependency chain. It does not change a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- replaced the only `vue-calendar-heatmap` 0.8.4 business instance with a repository-owned Vue 3 SVG component;
- removed `vue-calendar-heatmap`, its `v-tooltip` runtime, and the Vue 2-only `vue-resize` peer chain from `package.json`, `package-lock.json`, and the installed dependency tree;
- evaluated the available `vue3-calendar-heatmap` fork but did not add it: its latest npm release is from March 2023 and it introduces a separate Tippy peer dependency for this single fixed use case;
- preserved the existing read-only `/api/get-user-calendar-heatmap` request and its `{ endDate, dataList: [{ date, count }] }` response contract;
- preserved the existing values, end date, localized month/day/legend labels, tooltip unit, five-color range, 53-week layout, and day-click event boundary;
- retained the existing `vch__*` CSS class names so the user-home styling remains compatible;
- replaces the tooltip package with native SVG `title` content and exposes SVG/day accessible labels plus deterministic `data-date` and `data-count` attributes;
- parses backend `yyyy-MM-dd` values as local calendar dates, preventing a timezone conversion from moving a contribution into an adjacent day;
- keeps the 620-pixel calendar readable on narrow screens through component-scoped horizontal scrolling without causing page-level horizontal overflow;
- moved the calendar locale, values, end date, and loaded state to the component's declared root data. They were previously nested under `profile` while being read and assigned as undeclared root properties, which was not a reliable Vue 3 reactive contract;
- centralized locale construction and removed its duplicated language-change implementation.

The pure calendar model lives separately from the SFC so its date range, leap-day behavior, normalization, week padding, month slots, and legacy color thresholds can be tested without a browser or backend.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage18.ps1

The verifier rejects SQL changes, invokes the complete stage-17 clean dependency/audit/build gate, parses every frontend SFC, requires the five retired/unused package boundaries to remain at zero, locks the single native component call site and existing API response contract, and runs the pure calendar-model smoke suite. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The read-only source, dependency, lockfile, and calendar-model audit can also be run independently:

    node .\scripts\audit-stage18-heatmap.mjs

## Verification evidence

The production build completed successfully and transformed 5,478 modules. The main JavaScript asset is 6,780.38 KB raw/2,122.40 KB gzip, 63.33 KB raw/18.34 KB gzip below phase 17. The main CSS asset is 1,056.85 KB/180.61 KB gzip, 2.18 KB raw/0.28 KB gzip below phase 17. The npm audit baseline remains zero critical, high, moderate, low, and total vulnerabilities.

The final isolated headed browser regression used only local production assets and mocked the three read-only requests for website configuration, user profile, and calendar data. It rendered 53 weeks, 371 padded grid slots, 369 visible dates, 12 localized month labels, three weekday labels, and five legend colors. The supplied counts mapped to the expected color levels, the final day exposed the localized native tooltip text, and the browser reported zero console errors and zero warnings.

At a 375-pixel viewport the component retained a 620-pixel internal SVG, scrolled inside its 255-pixel content area, and kept document-level horizontal overflow at zero. The browser resource log contained no external HTTPS/CDN request. No login, form save, API write, database access, production request, or upload was triggered.

## Remaining compatibility boundary

`@vue/compat` remains intentionally enabled. The next dependency boundary is an isolated review of `vue-cropper`, followed by `vue-katex-auto-render`; neither should be changed without preserving their current wrapper and rendered-content contracts.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
