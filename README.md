# Oritwig · 源枝

**Shared open-source capabilities. Independent Android apps.**

Oritwig separates useful capabilities from mature open-source projects and combines them into complete, standalone products. Each app has its own repository, launcher, private data, settings, build and export/backup flows.

## Repositories

| Repository | What it contains |
| --- | --- |
| [oritwig-core](https://github.com/athemeroy/oritwig-core) | Reusable Telegram-derived photo curves, Markor-derived text undo/redo, and original Android image/appearance helpers |
| [oritwig-poster](https://github.com/athemeroy/oritwig-poster) | Pocket Poster: import an image, compose it, save an editable project, and export PNG |
| [oritwig-journal](https://github.com/athemeroy/oritwig-journal) | Field Journal: dated notes and photos, text undo/redo, search/tags, and validated backup/restore |

The app repositories contain a pinned copy of the shared source, with an immutable Core commit and per-file SHA-256 verification. Downloading one app repository is enough to build it. Neither app requires the other, a sibling checkout, Telegram, a server, an account or a paid model service.

## Current development preview

These are development builds, not a production release or a claim of completed device acceptance.

- Core commit [6e6bf168](https://github.com/athemeroy/oritwig-core/commit/6e6bf168a49d4f68645bd55210a59e17f82deef7): standalone AAR, lint and 45 JVM tests passed in [GitHub CI](https://github.com/athemeroy/oritwig-core/actions/runs/36866905623)
- Poster commit [643ed5fa](https://github.com/athemeroy/oritwig-poster/commit/643ed5faa115a4ccc7741ea55ede1ab500dea7b3): build/package job passed, including 45 shared and 11 app JVM tests; [build outputs and reports](https://github.com/athemeroy/oritwig-poster/actions/runs/36869520624)
- Journal commit [e486c4d4](https://github.com/athemeroy/oritwig-journal/commit/e486c4d4b495d96b67606f59777aa2a7f9051969): build/package job passed, including 45 shared tests and two regression wrappers covering 135 store/draft checks; [build outputs and reports](https://github.com/athemeroy/oritwig-journal/actions/runs/36869593935)

The generated APK/source bundles were checked against their exact commits, license notices, embedded Core lock, repository URL and signing state. Debug APKs use development signing; release APKs are intentionally unsigned.

**Native installation, UI workflows and Android 35 runtime acceptance are still pending.** The API 35 jobs stopped at the runner's KVM-access preflight before executing app tests, so their overall workflows are not green. This is not an app-test pass. The catalog includes one manual, bounded API 26 software-emulator experiment; an API 26 result would not establish API 35 coverage.

## Why separate repositories?

- Product navigation, data and releases belong to each app
- Shared capabilities have a clear source and license boundary
- Core updates are explicit, reviewed source changes rather than floating branch dependencies
- Every binary bundle includes its matching complete corresponding source and build inputs

Exact Telegram and Markor source revisions, retained notices and modifications are documented in Core and mirrored into each app. The products are not affiliated with or endorsed by those upstream projects.

## Build or contribute

Use the README and pinned toolchain instructions in the repository you want to build. The app package IDs remain `dev.appmatrix.poster` and `dev.appmatrix.journal` to preserve existing development-install data; the repositories form the Oritwig series.

This repository is the catalog and cross-repository test harness. It does not contain another app or an alternate shared library. The manual runtime workflow requires exact immutable app commits and performs no KVM permission changes.

## License

Catalog text and original helper scripts are [GPL-3.0-or-later](LICENSE). Each product repository contains the full applicable upstream notices and complete corresponding source. Check those notices when reusing or distributing the apps or Core.
