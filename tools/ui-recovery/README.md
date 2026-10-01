# Bounded API 26 UI recovery: actual SAF paths and honest visual evidence

Original test harness, not a production-app change. UI outcomes require actual
execution on the exact candidate commits. Host-only helper tests are separate.

The optional host WebM recorder uses the [official emulator console recording
command](https://developer.android.com/studio/run/emulator-record-screen), capped
at 180 seconds per segment, 480 × 800 pixels, 10 fps and 750 kbps. It bypasses the
API 26 guest encoder, which is unsupported on this image. WebM files receive
header/size checks and still require independent playback review. Video errors
are reported separately from UI outcomes. A PNG-to-video walkthrough must never
be called a continuous screen recording.

Call `bash tools/ui-recovery/run_ui_smoke.sh artifacts/api26-experiment/ui`
after BOTH native runners pass their raw `am instrument -w -r` checks in the same
boot. Exact guard: emulator-5554, app-matrix-ci-api26, qemu=1, boot_completed=1,
API 26. Either observed AVD property spelling is allowed, but every nonempty name
must match. Fresh-AVD display must be 480 × 800 at 160 dpi. No personal data, network
services, account, app data clearing, or KVM changes are used.

No pip or external fixture dependencies: a deterministic 640 × 480 original PNG is
generated using the Python standard library and pushed to Download. Journal gets
600 seconds and Poster 420 seconds, plus 20 seconds bounded cleanup each. With the
measured ~5-minute setup/native path, this fits the 30-minute job budget. Separate
per-app results preserve partial evidence; any UI failure fails the UI section.

## Evidence-based correction

The second run had two visible Downloads labels in the create-document picker.
The corrected locator requires observed text Downloads, resource-id
android:id/title, and package com.android.documentsui. Replaying both captured
ambiguous XML snapshots selected exactly the intended drawer row. It never picks
an arbitrary first match or bypasses SAF via an internal content URI.

## Journal focused pass

The unchanged exact app revision already passed real-UI Undo/Redo, save/reopen,
dirty recovery and Back/keep-draft in run 36876022798. This pass closes remaining
photo/SAF/backup gaps instead of repeating the whole notes-only path:

- Create a synthetic observation, close keyboard, scroll to top and require a
  complete visible editor heading; preserve a full-form PNG
- Import synthetic.png through actual DocumentsUI; apply light curves and rotate
- Export real JPEG through SAF; verify 480 × 640 structure and no EXIF/IPTC/comments
- Save one photo observation; export unencrypted backup through actual SAF
- Independently validate ZIP CRCs, committed title/notes/count, both source and
  edited media revisions, normalized JPEG headers and every media SHA256
- Add a temporary second observation; preview/cancel restore and require both to
  remain; confirm replacement and require the original single observation
- Force-stop/reopen, open restored photo, SAF-export again and compare JPEG bytes
  with the pre-backup export; capture restored state and settings

## Poster pass

Actual system-picker Cancel/import; actual SAF PNG exports; staged curve Cancel
with exact output equality; Apply/save/force-stop/reopen and pixel-changing PNG
export; settings. Exported PNGs are independently decoded with CRC/filter checks.
Unedited pixels must equal the synthetic source; dimensions remain 640 × 480;
canceled output bytes stay identical and saved/reopened curves alter real pixels.

## Media and limitations

Meaningful states have original full-screen PNGs. Every hierarchy and capture
sequence/timestamp is saved. Optional video segments must be independently
inspected; a generated file or header is not proof of complete video coverage.
Host recorder is optional; truthful timestamped screenshot-sequence demos are
permitted and must be labeled accordingly. Failed or incomplete steps must not
appear as completed in README media.

Still outside this bounded pass: EXIF 5/7 and oversized picker input tests,
landscape/large-font/TalkBack listening, API 35 runtime and physical phones.
Native framework coverage must be reported separately from visible UI coverage.
