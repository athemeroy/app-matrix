#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Invoke only after both raw native runners passed in the same dedicated API26 boot.
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd -P)
out=$(realpath -m "${1:?Provide the run artifact directory}")
: "${ANDROID_HOME:?Set the isolated Android SDK}"
mkdir -p "$out/fixtures" "$out/journal-ui" "$out/poster-ui"
python3 "$here/generate_fixture.py" "$out/fixtures" || exit 1
adb="$ANDROID_HOME/platform-tools/adb"
timeout 30 "$adb" -s emulator-5554 shell -n mkdir -p /sdcard/Download || exit 1
timeout 45 "$adb" -s emulator-5554 push "$out/fixtures/synthetic.png" /sdcard/Download/synthetic.png || exit 1
# Journal first: close actual photo/JPEG/backup/restore SAF integration gaps.
# Apps have separate private data; failure in one UI plan does not hide the other.
overall=0
for app in journal poster; do
 budget=600; [[ $app != poster ]] || budget=420
 if timeout "$((budget + 20))" python3 "$here/ui_smoke.py" "$app" --out "$out/$app-ui" --fixtures "$out/fixtures" --budget-seconds "$budget" > "$out/$app-ui/driver.txt" 2>&1; then
  printf 'passed\n' > "$out/$app-ui/EXIT-RESULT.txt"
 else
  code=$?;printf 'failed exit=%s\n' "$code" > "$out/$app-ui/EXIT-RESULT.txt";overall=1
 fi
 timeout 20 "$adb" -s emulator-5554 shell -n dumpsys package "dev.appmatrix.$app" > "$out/$app-ui/installed-package.txt" 2>&1 || true
 timeout 15 "$adb" -s emulator-5554 exec-out screencap -p > "$out/$app-ui/terminal-screen.png" 2>/dev/null || true
 timeout 15 "$adb" -s emulator-5554 shell -n am force-stop "dev.appmatrix.$app" >/dev/null 2>&1 || true
done
printf '%s\n' "$overall" > "$out/UI-EXIT-CODE.txt"
exit "$overall"
