# HOJ upgrade phase 19: native Vue 3 avatar cropper

Phase 19 retires the Vue 2 branch of `vue-cropper` from the two avatar workflows. It does not change a backend service, upload endpoint, database table, SQL file, production database, production host, or deployment.

## Implemented changes

- upgraded `vue-cropper` from the Vue 2 branch `0.5.11` to the exact official Vue 3 branch `1.1.4`;
- evaluated the newer official `cropper-next-vue` package but did not add it: version `0.3.1` requires Node 22 or newer, while this repository deliberately supports and verifies Node 20.9 or newer;
- added one local adapter that imports the package stylesheet once and opts the native component into Vue 3 semantics through `compatConfig.MODE = 3`;
- routed both existing consumers through that adapter and changed the template boundary to explicit Vue 3 kebab-case props and the package's `real-time` event;
- preserved the personal-avatar and group-avatar 200-by-200 fixed crop, 0.8 PNG output, realtime preview, left/right rotation, Base64 confirmation preview, and Blob generation contracts;
- preserved the existing multipart field names and endpoints: personal avatars still post `image` to `/api/file/upload-avatar`, while group avatars still post `image` plus the route `gid` to `/api/file/upload-group-avatar`;
- preserved the existing image-extension allowlist, two-megabyte size limit, FileReader data-URL boundary, generated `avatar.png` filename, and response-store updates;
- kept `cropperjs` `1.6.2` as a separate direct dependency for the phase-17 Markdown editor image workflow;
- constrained both cropper containers to `min(400px, 100%)`, retaining the desktop size while preventing page-level overflow on narrow screens.

No automatic upload was added. A user must still select a file, review the crop, and explicitly press the final upload button.

## Repeatable verification

Run from the repository root:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stage19.ps1

The verifier rejects SQL changes, invokes the complete stage-18 clean dependency/audit/build gate, parses every frontend SFC, requires exactly two valid cropper instances and one centralized package/CSS import, locks all eight cropper method calls, locks both multipart endpoint contracts, verifies the exact package and lockfile versions, and preserves the independent Markdown `cropperjs` boundary. Use `-SkipCleanInstall` only after a successful clean run in the same workspace.

The read-only source, dependency, lockfile, template, and upload-contract audit can also be run independently:

    node .\scripts\audit-stage19-cropper.mjs

## Verification evidence

The production build completed successfully and transformed 5,478 modules. The main JavaScript asset is 6,769.41 KB raw/2,118.93 KB gzip, 10.97 KB raw/3.47 KB gzip below phase 18. The main CSS asset is 1,060.05 KB raw/181.45 KB gzip; the 3.20 KB raw/0.84 KB gzip increase is the centralized Vue 3 cropper stylesheet. The npm audit baseline remains zero critical, high, moderate, low, and total vulnerabilities.

The final isolated headed browser regression used only the local production build, repository image assets, a synthetic local session, and browser-intercepted read-only API responses. Both the personal and group settings routes rendered a 400-by-300 cropper with a 200-by-200 fixed crop box and a live preview. The personal flow accepted left/right rotation, produced a 200-by-200 `data:image/png;base64` confirmation preview, and generated a non-empty `image/png` Blob. The group flow independently generated the same non-empty PNG Blob contract.

At a 375-pixel viewport the cropper contracted to 297 pixels while retaining the 200-by-200 crop box, and document-level horizontal overflow remained zero. The clean browser session reported zero console errors and zero warnings. The resource check found no external URL, and the request log contained no non-GET request: the final upload buttons were not pressed and neither upload endpoint was called.

## Remaining compatibility boundary

`@vue/compat` remains intentionally enabled. The next isolated dependency boundary is `vue-katex-auto-render`, followed by a new whole-application compatibility-warning inventory before attempting to disable compatibility mode.

Production compatibility rehearsal still requires user-provided offline materials: a recent logical database backup or sanitized equivalent, a `/judge` directory copy, the deployed backend and JudgeServer JAR files, and the exact sandbox image names/digests plus runtime configuration. This phase does not authorize connecting to, dumping, or changing the production database or host. No commit, push, deployment, automatic database migration, or production mutation was performed.
