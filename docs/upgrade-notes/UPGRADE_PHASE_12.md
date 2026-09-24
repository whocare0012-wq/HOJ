# HOJ upgrade phase 12: Vue 3 compatibility-debt reduction

Phase 12 reduces the Vue 2 compatibility surface left after the framework and component upgrade. It intentionally stops short of removing `@vue/compat`: Muse UI still implements the two mobile navigation trees, and legacy named slots remain in application templates. This phase does not change a backend service, database table, SQL file, production database, production host, or deployed frontend.

## Implemented changes

- migrated 134 `.native` event modifiers to Vue 3 event fallthrough behavior;
- migrated 110 `.sync` bindings to named Vue 3 `v-model` bindings;
- migrated 160 deprecated `/deep/`/`>>>` selectors to scoped-style `:deep()` syntax;
- replaced 89 filter expressions in interpolation and bound attributes with explicit `$filters` function calls;
- removed global and local Vue 2 filter registration while preserving the same formatting functions;
- migrated all 20 legacy `<template slot="...">` declarations to Vue 3 named-slot syntax;
- removed the explicit `Vue.prototype` bridge and changed Axios, Markdown, notification, and message access to native application globals or direct module imports;
- replaced `vue-m-message` with Element Plus `ElMessage` and `ElNotification`;
- replaced `vue-avatar` with a project-owned Vue 3 avatar component that preserves image, initials, size, color, inline, and square variants;
- replaced `vue-clipboard2` with a project-owned Clipboard API adapter, fallback copy implementation, and Vue 3 directive;
- removed redundant global Vue Cropper registration while keeping the two explicit component imports;
- retained all existing backend request contracts and kept browser tests pointed only at the local unavailable API.

The three retired packages and their transitive dependencies were removed from `package-lock.json`. The npm audit remains at zero findings.

## Repeatable verification

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage12.ps1
```

The stage-12 verifier rejects SQL changes, retired compatibility packages and imports, `.native`, `.sync`, old deep selectors, `Vue.prototype`, legacy filter expressions, and legacy slot syntax on `<template>`. It checks the project-owned avatar, clipboard, particle, and slider replacements, then runs the complete stage-11 clean-install, dependency-tree, zero-vulnerability, production-build, and artifact gate. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

## Verification evidence

The 2026-07-16 production build transformed 3,464 modules. The main JavaScript asset decreased from 7.29 MB/2.08 MB gzip in phase 11 to 7.23 MB/2.06 MB gzip; the main CSS asset decreased to 1.13 MB/184 KB gzip. No deprecated deep-selector warnings remain. The build still reports the reviewed `mavon-editor` `eval` warning and mixed static/dynamic import notices.

Isolated browser checks loaded `/home`, `/problem`, `/training`, `/contest`, `/status`, and `/admin/login` with the expected titles and no runtime exception. Both frontend and administrator login forms were filled with local dummy values and submitted with the Enter key; requests reached only the unavailable local `/api/login` and `/api/admin/login` endpoints, confirming that removal of `.native` did not break the keyboard path.

Development mode now reports one reviewed runtime compatibility category instead of the four phase-11 categories: `GLOBAL_EXTEND`. Inspection traces it to `muse-ui` creating its overlay with `Vue.extend()`. No filter or global-prototype warning remains.

## Remaining compatibility boundary

Muse UI renders 178 `mu-*` tags across the frontend and administrator mobile navigation components. Replacing those two navigation trees with Element Plus or project-owned responsive components is the next bounded task. The source also contains 220 legacy slot attributes across 66 files; element-wrapped named slots need structure-aware conversion and route regression rather than blind text replacement.

Other old ecosystem boundaries to review before removing `@vue/compat` are `mavon-editor`, `vue-calendar-heatmap`, `vue-cropper`, and the project Katex directive adapter. After those replacements and the remaining named-slot conversion, the compiler and runtime can be tested in mode 3 before the alias is removed.

The recent production logical database backup or a sanitized equivalent remains the independent blocker for historical-data rehearsal. It must be supplied as a file for an isolated database copy; this phase does not authorize connecting to or dumping the production database. No commit, push, deployment, or production mutation was performed.
