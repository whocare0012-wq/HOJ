# HOJ upgrade phase 11: Vue 3 compatibility boundary and component ecosystem

Phase 11 moves the frontend runtime from Vue 2.7 to Vue 3.5.39 through the official `@vue/compat` migration build, then upgrades the component ecosystem that blocked the transition. It does not change a backend service, database table, SQL file, production database, production host, or deployed frontend.

## Migration boundary

Vite now resolves `vue` to `@vue/compat` and compiles templates in compatibility mode 2. The application boots with `createApp`, while a temporary bridge exposes the legacy constructor surface needed by the remaining Vue 2 plugins and global filters.

Element Plus and VXE are native Vue 3 libraries. Their globally registered components are explicitly placed in mode 3 after plugin registration so that Vue 3 `v-model` behavior is used without forcing every legacy application component into mode 3 at once. This keeps the migration observable and reversible while the remaining compatibility warnings are retired in the next phase.

## Implemented changes

- upgraded Vue and `@vue/compat` to 3.5.39, Vue Router to 4.6.4, Vuex to 4.1.0, and vue-i18n to 9.14.5 in legacy-composition mode;
- replaced `@vitejs/plugin-vue2` with `@vitejs/plugin-vue` 5.2.4 and retained Vite 6.4.3;
- replaced Element UI with Element Plus 2.14.3 and its reviewed icon package, including icon-property, dialog-model, submenu, dropdown-slot, and size migrations;
- upgraded VXE Table to 4.9.17 with VXE PC UI 4.3.18 and upgraded ECharts/Vue ECharts to 6.1.0/8.0.1;
- converted route and component async loaders to Vue 3-compatible declarations and updated router-view transitions and the admin catch-all route;
- migrated custom directive and component lifecycle hooks that were removed in Vue 3;
- replaced `particles.js` with a project-owned Canvas component and removed the retired runtime file from production output;
- replaced `vue-monoplasty-slide-verify` with a project-owned pointer- and keyboard-accessible slider component;
- fixed the login verification popover's Vue 3 reference slot and its Enter-key visibility path;
- retained the existing local `/api` development proxy and did not point any validation at production.

No automatic database migration was added or executed. `sqlAndsetting` remains unchanged.

## Repeatable verification

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage11.ps1
```

The verifier requires Node.js 20 or newer, rejects SQL changes, pins the reviewed framework and component versions, rejects retired Vue 2 packages, checks the compatibility boundary and critical source migrations, performs a clean `npm ci`, validates the installed dependency tree, requires a zero-finding npm audit, builds the complete frontend, and validates the local JavaScript, CSS, favicon, and gzip artifacts. Use `-SkipCleanInstall` only after a successful clean installation in the same workspace.

## Verification evidence

The official npm registry audit on 2026-07-16 reports:

```text
0 critical, 0 high, 0 moderate, 0 low
```

The production build transformed 3,474 modules successfully. Its largest JavaScript asset is 7.29 MB before gzip and 2.08 MB after gzip; the main CSS asset is 1.13 MB before gzip and 184 KB after gzip. Vite produced 32 JavaScript and 28 CSS files, generated the required precompressed assets, and did not emit `particles.js`.

Isolated browser checks loaded `/home`, `/problem`, `/training`, `/contest`, `/status`, and `/admin/login` with their expected route titles. Login forms were exercised only against the local unavailable API. The local slider was forced into its five-failure path, rendered with an accessible slider role, and its keyboard completion path invoked form validation. A fresh production preview loaded `/home`, `/problem`, and `/admin/login` without runtime exceptions; its only console errors were expected local `/api/*` failures because no backend was attached.

## Known follow-up work

The application still deliberately runs compatibility mode for old plugins, filters, event modifiers, and legacy named-slot syntax. Development mode reports four reviewed compatibility categories: global `extend`, global prototype access, private global utilities, and filters. The build also reports deprecated `>>>`/`/deep/` selectors, `eval` in `mavon-editor`, mixed static/dynamic imports, and a large main bundle.

Phase 12 should remove those compatibility dependencies, migrate scoped styles to `:deep()`, replace or isolate old editors and Vue 2-only plugins, move to native Vue 3 global-property/composable patterns, and finally remove the `@vue/compat` alias. A vue-i18n 11 upgrade should be evaluated together with a reviewed Node.js runtime update rather than forced into this boundary.

The recent production logical database backup or a sanitized equivalent remains the independent blocker for historical-data rehearsal. It must be supplied as a file for an isolated database copy; this phase does not authorize connecting to or dumping the production database. No commit, push, deployment, or production mutation was performed.
