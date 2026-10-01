# Oritwig · 源枝

**Mature open-source engines. Complete, independent Android apps.**

Each Oritwig app is built around one substantial mature engine, with Telegram-derived modules preferred where they fit. The engine remains the source of the product's core behavior. New code supplies a focused Android shell, platform adapters and clearly explained gaps.

No Telegram account, login, API or server is required. Reusable engines have their own boundaries; an app can be downloaded, built and run independently.

## Products

| Product | Primary engine | Status |
| --- | --- | --- |
| [Oritwig Codes](https://github.com/athemeroy/oritwig-codes) | ZXing 3.5.4, the third-party barcode library Telegram uses | Developer preview verified on Android 8 / API 26. Read codes from images, inspect/copy/share text, generate QR and export PNG. See its README for tests and real demonstrations |
| [Oritwig Voice](https://github.com/athemeroy/oritwig-voice) | RNNoise, retained from the version vendored by Telegram | Developer preview verified on Android 8 / API 26. Record or import supported WAV, clean speech locally, compare and export. Source, screenshots and a silent workflow video are published; the pinned source build passes CI |
| [Oritwig Photo](https://github.com/athemeroy/oritwig-photo) | Telegram's real GLES photo filters, native enhancement and crop geometry | Developer preview verified on Android 8 / API 26. Import, adjust, crop/rotate/mirror, export PNG and resume. Its README includes 14 actual screenshots and a 52-second walkthrough |

The eventual goal is **20 genuinely useful complete apps**, not 20 placeholders or renamed copies. Oritwig Player (local Media3 playback) and Oritwig Motion (vector-animation inspection/export) are being validated. A Telegram-based video-preparation module is in proof development. These are not yet accepted products.

ZXing and RNNoise are **not Telegram-authored**. Their original authors and licenses remain visible. Engines are not counted as apps, and cosmetic variants do not count as separate products.

## What each repository explains

- Exact primary upstream revision, retained core, adaptations and capability differences
- Why the standalone app is useful, plus every necessary newly written component
- Independent build/install instructions, complete corresponding source and licenses
- Actual screenshots and demo video, with tested and untested behavior distinguished

Device evidence is reported precisely. An Android API 26 emulator result does not establish API 35 or physical-device coverage. Development-signed APKs are labeled accordingly.

Local and CI debug builds can have different signing keys, so an in-place update is not guaranteed. Export wanted files before uninstalling a preview: uninstalling removes its private session/data. No production signing keys are distributed.

## Reusable modules

[oritwig-photo-engine](https://github.com/athemeroy/oritwig-photo-engine) is independently buildable and verified in a separate consumer. [oritwig-rnnoise](https://github.com/athemeroy/oritwig-rnnoise) publishes the independently tested RNNoise extraction and unchanged core/model source. Voice pins its exact public source revision. Each retains its own license boundary, so a ZXing-only app does not acquire an unrelated GPL photo dependency. Consumers pin exact engine source and include applicable notices and build inputs.

## Superseded previews

[oritwig-core](https://github.com/athemeroy/oritwig-core), [oritwig-poster](https://github.com/athemeroy/oritwig-poster) and [oritwig-journal](https://github.com/athemeroy/oritwig-journal) are earlier development previews retained for source history. They are not the current upstream-first product releases. The older manual test harness in this catalog applies only to those previews.

## License

Catalog text and original catalog helpers are [GPL-3.0-or-later](LICENSE). Each app and engine has its own applicable license and attribution. No upstream affiliation or endorsement is implied.
