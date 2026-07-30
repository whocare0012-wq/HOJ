# HOJ upgrade phase 13: Muse UI retirement

Phase 13 removes the last reviewed Muse UI boundary and replaces both mobile navigation trees with Vue 3-native Element Plus components. It does not change a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- removed muse-ui and its obsolete Vue 2 peer boundary from package.json and package-lock.json;
- removed the Muse UI stylesheet, plugin registration, all mu-prefixed tags, and the associated global styles;
- rebuilt the frontend mobile header with Element Plus buttons and dropdowns while preserving login, registration, language switching, unread-message, user, administration, and logout entries;
- rebuilt the frontend mobile drawer with Element Plus menu routing for home, problem, training, contest, status, rank, discussion, group, and about routes;
- rebuilt the administrator mobile header and drawer with the same permission-sensitive route groups as the desktop menu;
- changed responsive detection from window.screen.width to the actual browser viewport width;
- made both drawers close after a route selection and kept the current administrator route highlighted;
- migrated the navigation dialog footer to Vue 3 slot syntax;
- migrated Element Plus pagination from the deprecated boolean small API to the size API.

The migration removed 32 element-wrapped legacy slot attributes together with the Muse templates. There are 188 legacy element slot attributes across 64 files remaining for structure-aware migration; legacy slots on template elements remain at zero.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage13.ps1

The stage-13 verifier rejects SQL changes, Muse UI dependencies, lock entries, imports, bootstrap registration, and mu-prefixed source tags. It verifies the responsive Element Plus drawer/menu boundaries and pagination API, then invokes the full stage-12 clean-install, dependency, zero-vulnerability, build, and artifact gate. Use -SkipCleanInstall only after a successful clean run in the same workspace.

## Verification evidence

The 2026-07-16 clean install added 130 packages. The dependency root is valid and the official npm audit reports zero vulnerabilities. The production build transformed 3,462 modules.

Removing Muse UI reduced the main JavaScript asset from 7.23 MB/2.06 MB gzip to 6.98 MB/2.00 MB gzip. The main CSS asset decreased from 1.13 MB/184 KB gzip to 987 KB/166 KB gzip.

An isolated Chromium session at a 390 by 844 viewport verified the frontend home and problem navigation drawer, route selection, and automatic close. A local-only dummy administrator stored only in that isolated browser verified the administrator dashboard drawer, nested general menu, user-management navigation, and automatic close. The local API remained unavailable by design, requests stayed on 127.0.0.1, and no Vue compatibility warning was reported. The pagination deprecation warning was also removed.

## Remaining compatibility boundary

@vue/compat remains intentionally enabled. The next bounded work is the 188 element-wrapped legacy slot attributes, followed by review or replacement of vue-calendar-heatmap, vue-cropper, vue-katex-auto-render, and mavon-editor. The calendar heatmap still brings a Vue 2 peer dependency through vue-resize, and mavon-editor still produces the reviewed build-time eval warning.

A recent production logical database backup or sanitized equivalent is still required as a file for isolated historical-data restoration and compatibility rehearsal. This phase does not authorize connecting to or dumping the production database. No commit, push, deployment, automatic database migration, or production mutation was performed.
