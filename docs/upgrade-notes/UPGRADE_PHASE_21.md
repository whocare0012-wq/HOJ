# HOJ upgrade phase 21: application compatibility-warning inventory

Phase 21 establishes a whole-application Vue compatibility inventory across representative public, authenticated, group, contest, and admin routes. It also closes the only application-owned development warning reproduced by that inventory and makes every application locale message compile under Vue I18n 11. It does not change a backend service, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- added a repeatable locale compiler audit covering all 10 application locale files and all 5,281 message strings with the installed `@intlify/message-compiler`;
- fixed the `Compile_Tips3` message in English, Japanese, Korean, Simplified Chinese, and Traditional Chinese by expressing the literal C++ braces with Vue I18n literal interpolation syntax;
- retained the rendered text `for(int i=0...){...}` while eliminating Vue I18n error code 2 (`Invalid token in placeholder`) from `/introduction`;
- made the `Introduction` language-array watcher explicitly `deep: true` and locally disabled the `WATCH_ARRAY` compatibility behavior, so it now uses the declared Vue 3 watch contract without relying on Vue 2 array traversal;
- renamed the local highlight plugin's `install` parameter from `Vue` to `app`, accurately documenting that the plugin registers a directive on a Vue 3 application instance and removing the last false-positive legacy global-API match;
- added source checks for `.native`, `.sync`, removed instance event/property APIs, legacy global Vue APIs, renamed lifecycle hooks, functional component options, and component `model` options;
- preserved the intentional global compatibility MODE 2 boundary and the five reviewed MODE 3 boundaries around native Vue 3 dependencies;
- added a phase-21 verifier that inherits every phase-20 check, rejects SQL changes, validates exact Vue compatibility versions, parses every SFC, compiles every locale message, and locks the reviewed inventory counts.

No database migration, SQL edit, production connection, deployment, commit, or push is part of this phase.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage21.ps1

The verifier starts with the complete phase-20 clean dependency, security-audit, test, and production-build gate. It then requires exactly 10 locale files, 5,281 locale strings, five reviewed literal-brace fixes, five MODE 3 boundaries, one explicit `WATCH_ARRAY` Vue 3 boundary, zero locale compiler errors, zero malformed SFCs, and zero reviewed legacy source-API matches. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The read-only compatibility audit can also be run independently:

    node .\scripts\audit-stage21-compat.mjs

## Verification evidence

The final audit compiled all 5,281 application messages without error. The exact five literal-brace findings are the `Compile_Tips3` entries in `en-US`, `ja-JP`, `ko-KR`, `zh-CN`, and `zh-TW`; no other message required an exception. The source inventory reported zero `.native` modifiers, `.sync` bindings, removed instance event APIs, removed instance properties, legacy global Vue APIs, renamed lifecycle hooks, functional component options, component `model` options, and malformed SFC files.

The final isolated headed browser run used the local Vite development server because compatibility warnings are development diagnostics. Every `/api/**` request was fulfilled by browser-local synthetic read-only responses, with no backend, database, production host, or external resource access. The following routes were exercised at 1280 by 900 pixels:

- `/introduction`;
- `/setting`;
- `/group`;
- `/contest`;
- `/admin/problems`.

All five routes retained their requested path, mounted a non-empty application, and produced zero compatibility warnings, ordinary warnings, console errors, page errors, non-GET requests, and external resources. The introduction page rendered the literal `for(int i=0...){...}` text successfully. The admin problem-list snapshot also confirmed the expected authenticated navigation, empty synthetic table, and pagination structure.

The final production build transformed 5,479 modules successfully. The main JavaScript asset is 6,767.42 KB raw/2,118.56 KB gzip, and the main CSS asset is 1,060.05 KB raw/181.44 KB gzip. The inherited clean gate completed successfully; the installed direct tree remains Vue `3.5.39`, `@vue/compat` `3.5.39`, and Vue I18n `11.4.2`, while the npm security baseline remains zero known vulnerabilities.

## Remaining compatibility boundary

`@vue/compat` global MODE 2 remains intentional. The representative route inventory is clean, but it is not proof that every data-dependent dialog and detail route has executed. The next safe phase is a per-component MODE 3 pilot on an application-owned leaf route, followed by the same isolated development-warning matrix before expanding that boundary. Vue's migration-build guidance supports this incremental per-component approach, and Vue I18n requires literal interpolation for reserved brace characters.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
