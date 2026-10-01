# Field Journal

A complete, standalone, private Android field notebook. Field Journal has its own launcher,
application ID (`dev.appmatrix.journal`), storage sandbox and APK. It requires no other app
in this repository, Telegram account, server, model key, subscription or network access.
Android 8.0 / API 26 and newer; framework Java views, no AndroidX runtime dependency.

## Use it

1. **New observation** opens a dated, recoverable draft. Add a title, notes and comma-separated
   tags. A photo is optional; text-only observations are first-class.
2. **Add photo** uses Android's system document picker, makes a private bounded copy and leaves
   the gallery original untouched. **Photo tools** provides shared light/RGB curves, clockwise
   rotation, reset to the imported source, JPEG export and explicit photo removal.
3. **Undo notes / Redo notes** provide bounded, session-only edit history adapted from Markor.
   Edits replay through the ordinary dirty-state and draft-saving listeners. History is cleared
   when the editor closes; private text is never logged or sent anywhere.
4. **Save observation** commits an atomic snapshot. Search title, notes, tags or ISO date text;
   combine a tag filter with newest/oldest chronology. All content renders as literal text.
5. **Back** offers save, keep draft or explicitly discard changes. A kept draft survives app
   restart and cannot be silently overwritten by opening a different observation. Entry deletion
   and reset/removal of photos are explicit, confirmed actions.
6. **More → Settings** provides system/light/dark appearance and 80/90/100% JPEG export quality.
   Settings/About includes source attribution and full open-source license information.

## Backup and restore

**More → Export backup** writes a version-1 `.fjbackup` ZIP through the document picker.
The app warns before export that this contains private, **unencrypted** notes and images.
A cloud document provider may upload the explicitly exported archive. Drafts are excluded.

The archive contains an independently parseable Java-properties manifest (`journal.properties`)
and referenced `media/<UUID>.jpg` files. Metadata includes format/version, journal identity,
settings, all entry fields and per-image SHA-256 checksums. Properties escapes preserve Unicode,
newlines and literal markup. Source and edited photos are both retained, deduplicated when equal.
“Source” means the unedited normalized working copy: upright JPEG, at most 2048 px on either
side, private EXIF/GPS/IPTC/comment metadata removed. Original gallery files are never modified.

Restore first copies the archive into bounded private staging, then checks the ZIP central
index and CRCs, rejects duplicate/unsafe/unexpected paths or properties, validates schema,
versions, IDs, date/tag/text limits and settings, validates every media hash and complete JPEG
structure, and decodes every bounded image using Android. Unexpected files, EXIF/IPTC/comments,
trailing JPEG payloads and incomplete JPEG endings are rejected. No active data changes during
preview. A count/settings preview explicitly asks to **replace** the journal (not merge).
After confirmation it revalidates staging and atomically swaps the active-generation pointer.
A new persistent journal identity invalidates old drafts even if the process dies after commit.

One prior complete snapshot remains privately available for recovery if the current index or
snapshot becomes unreadable. Deleted entries are absent from the active journal and future
backups; they may remain in the one recovery generation until another save and in any previously
exported backup. This is not a secure-erasure feature. Failed saves/restores preserve the active
snapshot. Crash-leftover staging is cleaned once per process at first opening; cancelled previews
are removed immediately. No automatic cloud backup or device transfer is enabled.

### Explicit bounds

- 1,000 observations; title 160 characters; notes 50,000 characters per observation
- Up to 20 tags, 32 characters each
- Shared importer: 25 MB / 40 MP input; working image maximum dimension 2048 px
- Backup: 128 MiB compressed or expanded; 8 MiB manifest; 12 MiB per media file;
  120 MiB referenced media total; maximum 2,000 media files
- Source and edited revisions are copied into private snapshots, so committing a full journal
  needs additional temporary disk space; insufficient space is reported without replacing it

## Build and verify

From repository root, after the documented root toolchain setup:

```sh
./gradlew :apps:journal:assembleDebug :apps:journal:lintDebug :apps:journal:testDebugUnitTest
./gradlew :apps:journal:assembleRelease :apps:journal:assembleDebugAndroidTest
# Independent host domain/draft suites (only a JDK required):
JAVA_HOME=/path/to/jdk apps/journal/test-domain.sh
# Use the repository's device runner with an API 35 emulator/device:
tools/run-device-tests.sh
```

APK: `apps/journal/build/outputs/apk/debug/journal-debug.apk`.
Native instrumentation runner: `dev.appmatrix.journal.JournalInstrumentation`.

## Verification status

- The initial production Android sources compiled and passed `lintDebug` with the provisioned
  API 35 toolchain. Subsequent hardening/undo integration is submitted to the build owner for
  a fresh aggregate compile, lint and test run; consult root validation artifacts for final status
- Standalone JVM suites currently pass **122 journal/store checks + 13 draft checks**. They cover
  entry validation/search/order, restart, exact source/render backup round-trip, settings,
  preview/cancel/replace, malformed ZIPs/truncation/traversal/duplicates, quotas, image hashes,
  malicious JPEG metadata/trailing content, failed writes, explicit recovery, missing indexes,
  abandoned staging, memory-failure cleanup and malformed drafts
- Gradle's JUnit wrapper runs these same suites. Markor history has its own additional JUnit suite
- The included native runner exercises actual Android JPEG decode/encode, pixel-changing curves,
  rotation, persistence/restart, backups, source preservation, normalized export, corrupt/truncated
  inputs, draft identity and real EditText undo/redo behavior in an isolated private test directory
- Full native runner execution, visual screenshots and hands-on SAF/cancel/dirty-state lifecycle
  acceptance are **pending device QA**, not implied by host tests
- Physical devices, older Android releases, unusual OEM document providers, low-storage hardware
  and accessibility services have not yet been exercised

All product code is original except the separately documented Markor text-history integration
and shared Telegram-derived curves. See root `third_party/` provenance and GPL-3.0-or-later license.
