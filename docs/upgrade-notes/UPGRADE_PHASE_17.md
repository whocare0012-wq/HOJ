# HOJ upgrade phase 17: native Vue 3 Markdown editor

Phase 17 retires the remaining Vue 2-only `mavon-editor` runtime boundary. It does not change a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- replaced `mavon-editor` 2.10.4 with the Vue 3-native `md-editor-v3` 6.5.3;
- kept all 21 business editor instances behind the existing `components/admin/Editor.vue` wrapper and retained every `v-model:value` call site;
- preserved image upload authorization for administrators and group administrators through `/api/file/upload-md-img`;
- retained cleanup of images uploaded during the current editor session when their Markdown links are removed;
- preserved administrator attachment upload through `/api/file/upload-md-file` and inserts the returned link at the current editor cursor;
- disabled the editor's optional remote Mermaid, KaTeX, highlighting, Prettier, ECharts, and screenfull loaders, using the repository's local KaTeX/highlight.js runtime, page fullscreen, and an exact local `cropperjs` 1.6.2 dependency instead;
- added a dependency-scoped Vue compat runtime: only components defined inside `md-editor-v3` are forced to `MODE: 3`, while the rest of the legacy HOJ application remains in `MODE: 2`;
- uses explicit `modelValue`, `update:modelValue`, and `onChange` bindings so the controlled editor remains reactive across the compat compiler boundary;
- replaced the renderer previously extracted from mavon-editor with an explicit Markdown It 14 renderer;
- retained existing server-content syntax for abbreviations, definition lists, emoji, footnotes, inserted/marked text, subscript, superscript, task lists, and the three alignment containers;
- adds `target="_blank"` plus `rel="noopener noreferrer"` to non-anchor Markdown links;
- evaluated `markdown-it-toc` for old TOC compatibility, then removed it because npm audit reports an unfixed moderate XSS advisory. The new editor's native catalog remains available without that dependency.

The existing `openHtml` wrapper prop remains available for source compatibility. No current caller overrides its established `true` default.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage17.ps1

The verifier rejects SQL changes, invokes the complete stage-16 clean dependency/audit/build gate, checks the exact native editor and Markdown extension versions, requires all legacy/unsafe counts to remain at zero, verifies all 21 model bindings, exercises the actual renderer against legacy syntax, and checks the installed editor dependency tree. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The read-only source, dependency, and renderer audit can also be run independently:

    node .\scripts\audit-stage17-markdown.mjs

## Verification evidence

The final clean production build completed successfully and transformed 5,478 modules. The main JavaScript asset is 6,843.71 KB raw/2,140.74 KB gzip and the main CSS asset is 1,059.03 KB/180.89 KB gzip. The native editor also emits separate lazy CodeMirror language chunks. These changes replace the bundled Vue 2 editor and its runtime `eval` with explicit Vue 3/CodeMirror modules.

An npm audit after removing the unsafe TOC package reports zero vulnerabilities.

The isolated headed browser regression used a synthetic local administrator session on `/setting`; it did not use a production account or backend. The native toolbar inserted bold Markdown, the controlled value count advanced from 0 to 10, and the preview rendered the bold value. A second input rendered `++legacy++`, `==mark==`, subscript, superscript, emoji, and KaTeX with a synchronized value count of 43. The console reported zero warnings and a fresh request log reported zero external HTTPS/CDN requests. The only six console errors were the three expected local read-only bootstrap API failures and their Axios reports because no backend was running. No form save, editor save, image upload, attachment upload, API write, or production request was triggered.

## Remaining compatibility boundary

`@vue/compat` remains intentionally enabled. The next dependency boundary is `vue-calendar-heatmap` and its Vue 2 `vue-resize` peer chain, followed by isolated review of `vue-cropper` and `vue-katex-auto-render`.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
