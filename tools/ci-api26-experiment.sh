#!/usr/bin/env bash
# One bounded API26 software-emulator experiment for two exact app checkouts.
# No KVM, group, device-permission, or host security changes are performed.
set -euo pipefail
harness=$(cd "$(dirname "$0")/.." && pwd -P)
poster=$(realpath "${1:?Provide the Poster checkout directory}")
journal=$(realpath "${2:?Provide the Journal checkout directory}")
: "${ANDROID_HOME:?Set the isolated Android SDK}"
: "${RUNNER_TEMP:?Use a disposable CI runner}"
: "${POSTER_COMMIT:?Set the exact source commit}"
: "${JOURNAL_COMMIT:?Set the exact source commit}"
[[ $POSTER_COMMIT =~ ^[0-9a-f]{40}$ && $JOURNAL_COMMIT =~ ^[0-9a-f]{40}$ ]] || exit 2
[[ $(git -C "$poster" rev-parse HEAD) == "$POSTER_COMMIT" ]]
[[ $(git -C "$journal" rev-parse HEAD) == "$JOURNAL_COMMIT" ]]
cd "$harness"
out="$harness/artifacts/api26-experiment"
mkdir -p "$out"
printf 'poster_commit=%s\njournal_commit=%s\nruntime_api=26\nabi=x86\nacceleration=software\ncompile_sdk=35\ntarget_sdk=35\n' "$POSTER_COMMIT" "$JOURNAL_COMMIT" > "$out/EXPERIMENT-INFO.txt"
# Compile first so boot does not compete with Gradle for the runner's CPU/RAM.
for app in poster journal; do
  directory=$poster; [[ $app != journal ]] || directory=$journal
  (cd "$directory"; ./tools/verify-core.py; ./tools/verify-wrapper.sh; ./gradlew --no-daemon --console=plain ":apps:$app:assembleDebug" ":apps:$app:assembleDebugAndroidTest") > "$out/$app-build.txt" 2>&1
 done
./tools/setup-pinned-emulator.sh 26 > "$out/runtime-setup.txt" 2>&1
export ANDROID_USER_HOME="$RUNNER_TEMP/api26-android-user"
export ANDROID_AVD_HOME="$RUNNER_TEMP/api26-avds"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/15859902/bin/avdmanager" create avd --name app-matrix-ci-api26 --package 'system-images;android-26;default;x86' --device pixel_2 --path "$ANDROID_AVD_HOME/app-matrix-ci-api26.avd" > "$out/avd-setup.txt" 2>&1
# This is a new disposable AVD. Keep its data volume small enough for hosted CI.
python3 - "$ANDROID_AVD_HOME/app-matrix-ci-api26.avd/config.ini" <<'AVD'
from pathlib import Path
import sys
p = Path(sys.argv[1])
lines = [line for line in p.read_text().splitlines() if not line.startswith('disk.dataPartition.size=')]
p.write_text('\n'.join(lines + ['disk.dataPartition.size=2G']) + '\n')
AVD
adb="$ANDROID_HOME/platform-tools/adb"
"$adb" start-server
"$ANDROID_HOME/emulator/emulator" -avd app-matrix-ci-api26 -accel off -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader -memory 1536 -cores 2 -skin 480x800 -dpi-device 160 -camera-back none -camera-front none -show-kernel > "$out/emulator.txt" 2>&1 &
emulator_pid=$!
cleanup() {
  status=$?
  timeout 20 "$adb" -s emulator-5554 shell -n getprop > "$out/final-properties.txt" 2>&1 || true
  timeout 30 "$adb" -s emulator-5554 logcat -d > "$out/logcat.txt" 2>&1 || true
  timeout 20 "$adb" -s emulator-5554 exec-out screencap -p > "$out/final-screen.png" 2>/dev/null || true
  timeout 15 "$adb" -s emulator-5554 emu kill >/dev/null 2>&1 || kill "$emulator_pid" 2>/dev/null || true
  printf '%s\n' "$status" > "$out/EXIT-CODE.txt"
  exit "$status"
}
trap cleanup EXIT
booted=false
deadline=$((SECONDS + 600))
while (( SECONDS < deadline )); do
  kill -0 "$emulator_pid" 2>/dev/null || { echo 'Emulator exited before boot.' >&2; exit 1; }
  if [[ $(timeout 15 "$adb" -s emulator-5554 shell -n getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]]; then booted=true; break; fi
  sleep 5
done
[[ $booted == true ]] || { echo 'API26 software boot did not complete within 10 minutes; no retry.' >&2; exit 1; }
[[ $(timeout 20 "$adb" -s emulator-5554 shell -n getprop ro.build.version.sdk | tr -d '\r') == 26 ]]
timeout 30 "$adb" -s emulator-5554 shell -n input keyevent 82
for app in poster journal; do
  directory=$poster; runner=PosterInstrumentation
  if [[ $app == journal ]]; then directory=$journal; runner=JournalInstrumentation; fi
  timeout 90 "$adb" -s emulator-5554 install -r "$directory/apps/$app/build/outputs/apk/debug/$app-debug.apk"
  timeout 90 "$adb" -s emulator-5554 install -r -t "$directory/apps/$app/build/outputs/apk/androidTest/debug/$app-debug-androidTest.apk"
  timeout 240 "$adb" -s emulator-5554 shell -n am instrument -w "dev.appmatrix.$app.test/dev.appmatrix.$app.$runner" | tee "$out/$app-instrumentation.txt"
  python3 - "$out/$app-instrumentation.txt" <<'ASSERT'
from pathlib import Path
import re, sys
text = Path(sys.argv[1]).read_text()
passed = re.search(r'INSTRUMENTATION_RESULT: passed=(\d+)', text)
if 'INSTRUMENTATION_CODE: -1' not in text or not passed or int(passed[1]) < 1:
    raise SystemExit('Native integration assertions did not pass.')
print(f'API26 only: {passed[1]} native assertions passed.')
ASSERT
  timeout 60 "$adb" -s emulator-5554 shell -n am start -W -n "dev.appmatrix.$app/.MainActivity"
  sleep 2
  timeout 30 "$adb" -s emulator-5554 exec-out screencap -p > "$out/$app-launcher.png"
done
printf 'Both native suites passed on API26 software emulation only. API35 runtime is not validated by this experiment.\n' > "$out/RESULT.txt"
