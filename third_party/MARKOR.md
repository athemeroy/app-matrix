# Markor-derived, bounded notes undo/redo

Source inspection and modification date: **2026-10-01 (UTC)**.

## Upstream source and exact license choices

Repository: [gsantner/markor](https://github.com/gsantner/markor).
Pinned revision: [`8d657fd20fff71719d782a3bc375c6f982d09b19`](https://github.com/gsantner/markor/tree/8d657fd20fff71719d782a3bc375c6f982d09b19).
No Markor application binary, icon, branding, artwork, sample document or other asset is included.

| Exact source | Upstream notice | Local adaptation |
| --- | --- | --- |
| [`app/src/main/java/net/gsantner/opoc/frontend/textview/TextViewUndoRedo.java`](https://github.com/gsantner/markor/blob/8d657fd20fff71719d782a3bc375c6f982d09b19/app/src/main/java/net/gsantner/opoc/frontend/textview/TextViewUndoRedo.java) | Explicit unrestricted public-domain dedication; the file header names no individual author. Credit: the contributors to this file in Markor | `apps/journal/src/main/java/dev/appmatrix/journal/JournalEditHistory.java` and `JournalUndoController.java` |
| [`app/src/main/java/net/gsantner/markor/frontend/textview/TextViewUtils.java`](https://github.com/gsantner/markor/blob/8d657fd20fff71719d782a3bc375c6f982d09b19/app/src/main/java/net/gsantner/markor/frontend/textview/TextViewUtils.java), only the `findDiff` common-prefix/common-suffix routine | Copyright 2018–2025 Gregor Santner <gsantner AT mailbox DOT org>; SPDX `Unlicense OR CC0-1.0`. **App Matrix chooses the Unlicense option** | `JournalEditHistory.findDiff` |

The per-file notices were read directly at the pinned revision. Markor's repository-level license is not substituted for these explicit file-level grants. In particular, the helper is not labelled Apache-2.0.

Original `TextViewUndoRedo.java` dedication, preserved verbatim:

> THIS CLASS IS PROVIDED TO THE PUBLIC DOMAIN FOR FREE WITHOUT ANY
> RESTRICTIONS OR ANY WARRANTY.

The complete relevant upstream headers, including the Gregor Santner attribution and public-domain dedication, are retained in [Markor-Public-Domain.txt](licenses/Markor-Public-Domain.txt). The [Unlicense](licenses/Markor-Unlicense.txt) is the selected license for the helper; the [CC0 1.0 text](licenses/Markor-CC0-1.0.txt) is also included to preserve the alternate dedication referenced by its upstream header. Standard license texts were obtained from the SPDX license-list-data project's `text/Unlicense.txt` and `text/CC0-1.0.txt` on the inspection date.

All three notice/license files are copied into `shared/core/src/main/assets/licenses/` under the same filenames for offline in-app access. App Matrix's new modifications and original tests are GPL-3.0-or-later; upstream notices remain intact. This does not claim exclusive rights over the upstream public-domain material. The combined app's GPL license and complete-source distribution requirements are described in the root LICENSE/NOTICE.

For reproducible source auditing:

| Source | Git blob SHA-1 at the pinned revision | SHA-256 of exact UTF-8 source bytes |
| --- | --- | --- |
| TextViewUndoRedo.java | `3dad15d67ab35ce58db71ef47aa4ca8b893b67c6` | `2909c768868d3b0b2ef1101f84a6dd73dc8eae4367d072e4b5d778500def5112` |
| TextViewUtils.java | `cef735c5aaee6f68b67650175104fa16d397c9b3` | `13b1278ab9de9e99d19f55d15f1677ae914068ee534addea21cf49b091cde120` |

## What is genuinely adapted

This is an **adaptation**, not an unmodified extraction or a name-only association.

- Markor `EditHistory`'s chronological operation list and undo/redo position cursor become `JournalEditHistory.history` and `position`
- Markor `EditItem`'s start/before/after text replacement and minimal-range diff become `JournalEditHistory.EditItem` and `findDiff`
- Markor's `beforeTextChanged` / `onTextChanged` / `afterTextChanged` range capture and replay guard become the single watcher in `JournalUndoController`
- Markor's adjacent insertion and backward deletion coalescing, five-second time window, and character/space grouping become bounded, code-point-aware coalescing in `EditItem.merge`
- Applying the inverse or forward replacement and restoring the cursor are retained, with both selection endpoints supported

The following are App Matrix modifications dated 2026-10-01:

- A hard cap of **100 operations** and **200,000 UTF-16 code units of retained before/after text**, counting both undo and redo history
- Oldest-first eviction; a single over-budget change clears history rather than allowing replay across an unrecorded gap; merged operations cannot bypass the text cap
- Preserve selection ranges and reversed selections, not only one cursor endpoint; enforce range/length checks before replay and verify that input filters accepted the replacement
- `try/finally` always releases replay suppression, even if a replacement or another watcher throws; invalidate uncertain history after failures without logging note contents
- Do not remove the application's dirty/draft-save watchers during replay
- An injected target for JVM coverage and a small Android bridge, while using the same production history implementation
- Monotonic Android uptime rather than wall-clock time, code-point-aware single-character grouping, surrogate-pair-safe diff boundaries, forward-delete chains, and explicit paste/replace/newline/cursor/time boundaries
- One controller/watcher for the notes editor; explicit `clearHistory`, `dispose`, and exception-safe `suspendRecording` plus automatic disposal on view detach
- Undo/redo controls labelled “Undo notes” / “Redo notes”, with enabled state reflecting actual available operations

The history covers the notes body only. It does not undo title, tags, date, photos, entry deletion or backup restore. It is intentionally session-only: opening a different editor, closing the editor, view detachment, process death or restarting the app clears it. The independent local draft-save feature saves the current text; it does not save undo history.

## Deliberately excluded upstream functionality

No SharedPreferences history persistence, restoration, per-file path-derived preference keys, `File` identity, logging, exception-text toasts, underline-span removal, global editor singleton, or other Markor subsystem is included. No deleted note text is serialized by this feature. Old history strings become eligible for ordinary garbage collection when evicted/cleared; secure memory erasure is not claimed.

The application screens, journal/draft stores, accessible toolbar wiring, limits, failure-handling changes and tests are original App Matrix contributions. Markor is credited for the adapted editing capability; there is no claim of affiliation or endorsement.

## Verification

`JournalEditHistoryTest` has 28 original, deterministic JVM tests against the production history: insert, delete, paste, replace, minimal diff, word/space/time/cursor boundaries, forward/backward delete chains, selection restoration, new-edit redo invalidation, no-op handling, both caps, oversize changes, emoji/surrogate/combining text, 300 seeded random range edits with full reverse/forward replay, dirty notifications, rejected replay, exceptions before/after mutation or during selection, and clearing across entries.

Run the history and journal tests with `./gradlew :apps:journal:testDebugUnitTest`.

`JournalUndoInstrumentationChecks.run(Context)` covers real-EditText Android behavior (watchers and selection, coalescing, programmatic reset, failed reset, Unicode, rejected replay, dispose/new session). `runDetach(Activity)` covers actual view-detach clearing. See [BUILDING.md](../docs/BUILDING.md) for device checks; compiling instrumentation is separate from executing it.
