# Oritwig · 源枝

**Mature open-source engines. Complete, independent Android apps.**

Each Oritwig app is built around one substantial mature engine, with Telegram-derived modules preferred where they fit. The engine remains the source of the product's core behavior. New code supplies a focused Android shell, platform adapters and clearly explained gaps.

No Telegram account, login, API or server is required. Reusable engines have their own boundaries; an app can be downloaded, built and run independently.

## Products

| Product | Primary engine | Status |
| --- | --- | --- |
| [Oritwig Codes](https://github.com/athemeroy/oritwig-codes) | ZXing 3.5.4, the third-party barcode library Telegram uses | Developer preview verified on Android 26. Read codes from images, inspect/copy/share text, generate QR and export PNG. See its README for tests and real demonstrations |
| Oritwig Voice | RNNoise, retained from the version vendored by Telegram | App validation and media in progress; not a published release |
| Oritwig Photo | Telegram's real GLES photo filters, native enhancement and crop geometry | App validation and media in progress; not a published release |

The eventual goal is **20 genuinely useful complete apps**, not 20 placeholders or renamed copies.

ZXing and RNNoise are **not Telegram-authored**. Their original authors and licenses remain visible. Engines are not counted as apps, and cosmetic variants do not count as separate products.

## What each repository explains

- Exact primary upstream revision, retained core, adaptations and capability differences
- Why the standalone app is useful, plus every necessary newly written component
- Independent build/install instructions, complete corresponding source and licenses
- Actual screenshots and demo video, with tested and untested behavior distinguished

Device evidence is reported precisely. An Android 26 emulator result does not establish Android 35 or physical-device coverage. Development-signed APKs are labeled accordingly.

## Reusable modules

The photo and RNNoise modules are being separated into focused engine repositories. They retain independent license boundaries; a ZXing-only app does not acquire an unrelated GPL photo dependency. Consumers pin exact engine source and include the applicable notices and build inputs.

## Superseded previews

[oritwig-core](https://github.com/athemeroy/oritwig-core), [oritwig-poster](https://github.com/athemeroy/oritwig-poster) and [oritwig-journal](https://github.com/athemeroy/oritwig-journal) are earlier development previews retained for source history. They are not the current upstream-first product releases. The older manual test harness in this catalog applies only to those previews.

## License

Catalog text and original catalog helpers are [GPL-3.0-or-later](LICENSE). Each app and engine has its own applicable license and attribution. No upstream affiliation or endorsement is implied.
