# Android app and capability boundaries

App Matrix is a Java/Android framework monorepo. Each app owns its launcher, product model, navigation, persistence and settings. The shared library owns account-free image operations and small platform helpers.

## Modules

- `:apps:poster` (`dev.appmatrix.poster`): editable image-composition projects, recipe rendering, PNG export
- `:apps:journal` (`dev.appmatrix.journal`): dated observations, tags/search, optional photos, versioned backup/restore
- `:shared:core` (`dev.appmatrix.core`): image decode/orientation, transformations, curves, appearance and license UI

All three use Java 17 language level, Android API 26 minimum and SDK 35. The runtime does not depend on AndroidX, Compose, a server, user accounts or model services. The apps have distinct Android sandboxes and can be installed/run independently.

## Source-backed photo capability

`ToneCurves` holds Telegram-derived five-point curves and sample buffers. `PhotoFilterCurvesControl` is the adapted interactive graph. `CurveMap` translates the relevant HSL/RGB shader math into a deterministic CPU lookup. It is independent of Android and covered by JVM tests. Full attribution and deliberate differences are in [TELEGRAM.md](../third_party/TELEGRAM.md).

`BitmapTransforms.applyCurves` applies `CurveMap` row by row without replacing the caller's source bitmap. Default curves preserve pixels exactly. `PhotoAdjustments.edit` provides an optional self-contained editor dialog; product-specific editors can use the same model, graph and transform directly to preserve an editable recipe.

## Image ownership and privacy

- `MediaFiles.readBitmap` bounds compressed bytes and source pixels, samples before allocating a working bitmap, applies EXIF orientation and returns normalized pixels
- `BitmapTransforms` never recycles its input. An unchanged operation can return the same bitmap; callers must check identity before recycling intermediates
- `MediaFiles.saveBitmap` / `savePng` use app-private atomic writes. System-provider sources are never modified
- `copyTo` / `copyPngTo` write only to a user-selected destination URI. A canceled picker is handled by the product and does not alter state
- Persist required source bytes immediately; do not rely on a transient source-provider URI across restart
- Product backup/restore owns validation, staging and the commit boundary; shared image helpers do not select or mutate a journal

## UI and settings helpers

`Theme` applies a stored system/light/dark preference and provides a compact palette. Apps handle their own edge-to-edge insets and responsive navigation. `Settings` stores appearance under each app's separate sandbox. `About` exposes source attribution, modifications, repository access and complete license text. Product-specific export-size and chronology settings remain product-owned.

## Building and verifying

Use the [pinned build instructions](BUILDING.md). Shared math tests, product domain/persistence tests, lint, APK builds, emulator/device workflows, and exported-byte validation are different checks. Passing one does not imply the others have run.
