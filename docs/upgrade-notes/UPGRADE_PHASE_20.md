# HOJ upgrade phase 20: native Vue 3 KaTeX auto-render

Phase 20 removes the unused Vue 2 `vue-katex-auto-render` package boundary and formalizes the repository-owned Vue 3 directive around KaTeX's framework-independent auto-render extension. It does not change a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- removed `vue-katex-auto-render` `0.2.0`, which was still declared and locked even though no application source imported it;
- added KaTeX as an exact direct dependency and aligned the existing npm override and lockfile on `0.17.0`, while preserving `@iktakahiro/markdown-it-katex` `4.0.1` for Markdown token rendering;
- retained one centralized stylesheet and `katex/contrib/auto-render` import rather than introducing a framework wrapper;
- converted the local directive to the native Vue 3 `mounted` and `updated` lifecycle hooks and retained one global `app.use(Katex)` registration;
- preserved all seven existing `v-katex` sites across the global footer, Markdown previews, announcement views, and rank signatures;
- corrected delimiter priority so `$$...$$` is evaluated before `$...$`, followed by `\(...\)` and `\[...\]`;
- added `pre` to the existing ignored text tags and skips already-rendered `.katex` and `.katex-display` subtrees on later Vue updates;
- moved directive option construction into a separately testable module with frozen defaults and copied delimiter, tag, and class collections, preventing directive renders from mutating either defaults or caller-owned binding values;
- accepts both direct binding options and the previous local `{ options: ... }` shape, keeps non-throwing render behavior by default, and preserves caller-provided delimiters, ignored tags/classes, error callbacks, and `throwOnError` choices;
- locks `trust: false` after custom option merging because all current directive sites can contain user- or administrator-controlled text.

No database migration, SQL edit, production connection, deployment, commit, or push is part of this phase.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage20.ps1

The verifier rejects SQL changes, invokes the complete stage-19 clean dependency/audit/build gate, parses every frontend SFC, requires exactly seven directive sites and one centralized import/CSS/plugin registration, rejects the legacy package and directive hooks, verifies exact dependency and lockfile versions, checks delimiter and ignored-text boundaries, and runs option-copy, direct/nested binding, trust, installed-version, MathML, and HTML-output smoke tests. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The read-only KaTeX-specific audit can also be run independently:

    node .\scripts\audit-stage20-katex.mjs

## Verification evidence

The dependency tree resolves both the direct application import and `@iktakahiro/markdown-it-katex` to one KaTeX `0.17.0` installation; `vue-katex-auto-render` has no declaration, lock entry, or source usage. The npm audit baseline remains zero critical, high, moderate, low, and total vulnerabilities.

The production build completed successfully and transformed 5,479 modules. The main JavaScript asset is 6,767.33 KB raw/2,118.52 KB gzip, 2.08 KB raw/0.41 KB gzip below phase 19. The main CSS asset remains 1,060.05 KB raw/181.44 KB gzip.

The final isolated headed browser regression used only the local production build and browser-intercepted read-only API responses on a minimal local 404 route that retains the global footer. An 800-millisecond synthetic website-configuration delay proved the lifecycle boundary: the footer started with zero formulas and an empty heading, then its `updated` hook rendered two formulas and changed the heading to `Stage 20 Local`.

The resulting DOM contained two KaTeX roots, one display formula, two MathML trees, and two HTML presentation trees. Literal `$ignored$` and `$also_ignored$` text inside `code` and `pre` remained unrendered. At a 375-pixel viewport the description width was 269 pixels and document-level horizontal overflow was zero. The clean browser session reported zero console errors, warnings, and page errors; resource inspection found no external URL, and the request log contained no non-GET request.

## Remaining compatibility boundary

`@vue/compat` remains intentionally enabled. The next stage is a new whole-application compatibility-warning inventory across representative public, authenticated, group, contest, and admin routes. That inventory should classify remaining warnings by application code versus third-party ownership before selecting the next removable boundary or attempting compatibility mode 3 more broadly.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
