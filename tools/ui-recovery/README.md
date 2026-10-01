# One bounded API26 UI recovery pass

Prepared harness, not a passed UI result. This directory is original QA code.
Copy these four files together: ui_smoke.py, generate_fixture.py,
run_ui_smoke.sh, README.md. No third-party Python packages or fixture downloads.

Call `bash tools/ui-recovery/run_ui_smoke.sh artifacts/api26-experiment/ui`
after BOTH native runners pass their raw `am instrument -w -r` checks in the
same boot. Required dedicated emulator identity: emulator-5554,
app-matrix-ci-api26, qemu=1, boot_completed=1, API26. The known API26 property
ro.kernel.qemu.avd_name is supported alongside ro.boot.qemu.avd_name; all
nonempty names must match. Set actual display480x800 density160 in fresh AVD.

The script creates/pushes only an original synthetic PNG, then gives each app a
360-second UI budget and a 375-second outer timeout. Journal runs first.
Individual UI failures remain recorded and do not hide the other app. Any UI
or video failure returns nonzero; native failures must remain hard failures too.
The total UI section is bounded to about12.5 minutes plus diagnostic commands.

Journal: actual field entry, keyboard, Undo/Redo, save, force-stop/reopen, dirty
force-stop recovery, Back/keep-draft, settings. Poster: actual system-picker
Cancel and import, actual SAF PNG export, staged curve Cancel with exact export
equality, Apply/save/force-stop/reopen and pixel-changing PNG export, settings.
Native accessibility tests leave neither real library populated; no pm clear or
user-data deletion occurs. Each tap resolves from fresh UI XML, including
labeled fields and actual slider bounds. Missing/ambiguous controls fail rather
than using app internals or direct content-URI substitutes.

Key states have raw full-screen PNGs; every queried hierarchy is saved. Native
screenrecord MP4 segments begin only once an app is visible, stop before each
force-stop, and resume after reopen. Each segment has a180-second limit, so
review its actual duration/content; do not assume every workflow step is filmed.
Recordings may include the real Android Files picker, which is part of SAF.
Videos are required separately from UI assertions and errors remain visible.

Poster outputs are independently decoded with a standard-library PNG CRC/filter
reader. Checks include640x480 dimensions, exact unedited input pixels, actual
curve changes, unchanged canceled exports, and absence of EXIF/text chunks.
The decoder was cross-checked locally against Pillow RGB/RGBA encodings. Label
selectors were host-tested on synthetic XML. These are helper tests only.

Still excluded: Journal photo/backup UI, JPEG export, EXIF5/7 and oversized UI
imports, full landscape/font/TalkBack checks, API35 runtime and physical phones.
Native runners may cover some of these programmatically; report that separately.
