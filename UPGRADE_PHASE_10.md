# HOJ upgrade phase 10: Vue 2.7 to Vite build bridge

Phase 10 replaces the unmaintained Vue CLI/webpack build chain with Vite while deliberately retaining Vue 2.7 runtime semantics. This is a reversible preparation step for Vue 3; it does not change a backend service, database table, SQL file, production host, or deployed frontend.

## Why this bridge comes before Vue 3

The frontend contains 108 Vue single-file components. `vxe-table` is used throughout problem, contest, group, submission, ranking, and administration views, while Element UI and several editor plugins also depend on Vue 2 behavior. Replacing all of them in one unverified change would combine build, framework, component, and security migrations into a single rollback boundary.

The Vite bridge first removes the vulnerable and end-of-life build tooling while keeping the application component contract stable. Vue 3, `vxe-table` 4, Element Plus, ECharts 6, Vue Router 4, Vuex 4 or Pinia, and vue-i18n 11 remain a separate next phase.

## Implemented changes

- replaced Vue CLI 5 scripts with Vite 6.4.3 and `@vitejs/plugin-vue2` 2.3.4;
- removed Vue CLI plugins, `vue-template-compiler`, webpack compression, and webpack analyzer packages;
- replaced the webpack/EJS/CDN entry with a static Vite entry and bundled reviewed dependency versions;
- preserved gzip deployment artifacts with a project-owned post-write hook that runs only after a successful build;
- changed webpack-only asset `require()` calls and dynamic locale `require()` calls to static imports;
- pinned safe Vue-2-line updates for Papa Parse, vue-clipboard2, vue-cropper, vue-i18n, and xe-utils;
- replaced `vue-codemirror-lite` with a local CodeMirror 5 adapter;
- replaced `vue-particles` with a local Vue adapter and loads the exact local particles.js file as a classic script;
- registered the ECharts 4 bar, line, pie, and supporting components that were previously supplied implicitly by production CDN scripts;
- removed an accidental self-import cycle from the group announcement component;
- retained the existing `/api` proxy target on localhost and did not connect the preview to production.

The old `public/index.html`, `vue.config.js`, and `babel.config.js` remain untouched as rollback reference files. Vite uses the new root `index.html` and `vite.config.mjs` instead.

## Repeatable verification

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage10.ps1
```

The verifier requires Node.js 20 or newer, rejects SQL changes, checks the reviewed Vite and Vue versions, rejects removed legacy build packages, performs `npm ci`, freezes the reviewed audit ceiling, builds the complete frontend, and verifies local JavaScript, CSS, favicon, particles, and gzip artifacts. Use `-SkipCleanInstall` only after a successful clean installation in the same workspace.

## Verification evidence

The 2026-07-16 clean installation contained 130 packages and the production build transformed 1,562 modules successfully. The reviewed audit result improved from the phase-one baseline of 1 high, 4 moderate, and 9 low findings to:

```text
0 critical, 1 high, 3 moderate, 9 low
```

The remaining high finding is `vxe-table <= 4.8.10`. The fixed `vxe-table` release requires the Vue 3 component line, so `npm audit fix --force` remains prohibited.

A local Playwright smoke test loaded `/home` and `/admin/login`. Both pages rendered with their expected title and interactive structure. The test found and drove fixes for browser-side CommonJS calls, a self-import cycle, missing ECharts modules, and particles.js strict-mode loading. After the fixes, the only console errors were expected `/api/*` proxy failures because no backend was started for the static preview.

## Known follow-up work

The build still reports that the legacy `mavon-editor` distribution uses `eval`, and several components are imported both statically and dynamically, limiting chunk splitting. The main bundled script is therefore large. These items should be addressed with the Vue 3 editor and routing migration rather than hidden with warning suppression.

The production logical database backup remains the independent blocker for the phase-seven/phase-nine historical-data rehearsal. Do not deploy this frontend or run a production migration without explicit user approval.
