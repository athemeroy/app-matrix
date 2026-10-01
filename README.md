# App Matrix

**Independent Android apps built from carefully separated open-source capabilities.**

> Development preview: both Android app builds and initial automated checks are working. Device-level workflow, accessibility and release validation are still in progress. This repository is not a finished release, and debug APKs are for testing.

App Matrix turns a small reusable capability into useful products rather than a gallery of component demos. The first two apps share a real Telegram-derived tone-curve editor and image-processing core, while owning separate application IDs, screens, storage, settings and end-to-end workflows.

| App | Purpose | Independent package |
| --- | --- | --- |
| [Pocket Poster](apps/poster/README.md) | Compose a local image, save an editable project, export a finished PNG | `dev.appmatrix.poster` |
| [Field Journal](apps/journal/README.md) | Keep dated observations and optional photos, find past entries, back up and restore the journal | `dev.appmatrix.journal` |

Both are account-free and work offline. Neither needs Telegram, the other app, an API key, or a paid model service. App Matrix is not affiliated with or endorsed by Telegram.

## Build and run

Requirements: JDK 17 or newer, Android SDK platform 35 / build tools 35.0.0, and accepted Android SDK terms. The checked-in Gradle wrapper pins Gradle 8.11.1 with a verified distribution checksum. Full setup, build, test and APK instructions are in [BUILDING.md](docs/BUILDING.md).

```sh
./gradlew :apps:poster:assembleDebug
./gradlew :apps:journal:assembleDebug

adb install -r apps/poster/build/outputs/apk/debug/poster-debug.apk
adb shell am start -n dev.appmatrix.poster/.MainActivity

adb install -r apps/journal/build/outputs/apk/debug/journal-debug.apk
adb shell am start -n dev.appmatrix.journal/.MainActivity
```

Android 8.0 / API 26 or newer is required. Build/install each product separately. Debug APKs are development builds; release signing keys are deliberately not included.

## What is actually reused?

The shared Android library contains three explicitly attributed Telegram-derived pieces:

- Five-point luminance/RGB tone-curve interpolation and sample generation
- An adapted interactive curve graph
- A Java CPU port of the shader's HSL luminance / saturation handling followed by RGB curves

These are wired into the apps' photo-adjustment and export flows. Telegram's account, network, messaging, analytics and branding systems are absent. The CPU lookup deliberately uses deterministic nearest-index samples; it is not claimed bit-identical to Telegram's GPU texture sampling.

Field Journal combines that photo capability with **Markor-derived notes undo/redo**: bounded, in-memory edit history, change coalescing and minimal-range replacement, with accessible Undo notes / Redo notes controls. History is scoped to the current editing session and is never logged or saved. [The exact Markor source, selected licenses and changes](third_party/MARKOR.md) are documented separately.

[Exact upstream paths, pinned revision, licenses and changes](third_party/TELEGRAM.md) distinguish adapted code from original product code. Product navigation, storage, backup/restore, image import, crop/rotation, simple named filters, caption layout, settings and exports are new implementations.

## Local data and limits

- Images are chosen through Android's system picker; no broad media/storage or network permission is requested
- Imports are bounded to 25 MB and 40 megapixels, decoded to a product-defined working size, corrected for EXIF orientation, and copied into app-private storage
- Normalized saved/exported image pixels do not retain the original GPS or other EXIF metadata
- Projects and entries survive restart without continued access to the original source file
- Android automatic cloud backup is disabled. Uninstalling an app or losing the device can remove its local data
- Export locations selected in Android may sync to a third-party cloud provider. Journal backups are unencrypted; anyone with a backup can read it
- Deleting local content does not remove prior exported images or backups. The apps do not claim secure erasure or their own encryption

## Architecture

```text
apps/poster       independent image-composition product
apps/journal      independent journal product
shared/core       reusable curves, image IO/transforms, settings and license UI
third_party       upstream provenance and license notices
tools             reproducible setup, build and packaging helpers
```

There is no single feature-toggle application pretending to be multiple products. The shared module cannot access either product's repository or navigation state.

## Development checks

```sh
./gradlew :shared:core:testDebugUnitTest
./gradlew :apps:poster:testDebugUnitTest :apps:journal:testDebugUnitTest
./gradlew :apps:poster:lintDebug :apps:journal:lintDebug
```

CI builds both products independently, runs available automated checks, and packages source alongside build artifacts. Build/unit checks and device acceptance are separate evidence: a passing JVM test or APK build is not a claim that every device workflow has been exercised.

## License

The combined apps and original App Matrix contributions are **GPL-3.0-or-later**. Telegram-derived source retains its original **GPL-2.0-or-later** notices and is combined using the later-version option. Markor-derived editing code retains its public-domain dedication and the selected **Unlicense** notice for its helper. The full [GPLv3 license](LICENSE), [Telegram license](third_party/licenses/Telegram-GPL-2.0.txt), [Markor notices](third_party/MARKOR.md), and [project notices](NOTICE) are included, and app Settings exposes attribution, license text and source access.

If you distribute an APK, provide matching complete corresponding source and build instructions for that exact revision. See [third-party provenance](third_party/TELEGRAM.md) and the source-artifact packaging instructions. Other tools/dependencies retain their own licenses.
