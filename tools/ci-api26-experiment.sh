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
values = {'disk.dataPartition.size': '2G', 'hw.lcd.width': '480', 'hw.lcd.height': '800', 'hw.lcd.density': '160'}
lines = [line for line in p.read_text().splitlines() if line.split('=', 1)[0].strip() not in values]
p.write_text('\n'.join(lines + [key+'='+value for key,value in values.items()]) + '\n')
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
timeout 30 "$adb" -s emulator-5554 shell -n wm size > "$out/display-size.txt"
timeout 30 "$adb" -s emulator-5554 shell -n wm density > "$out/display-density.txt"
grep -q 'Physical size: 480x800' "$out/display-size.txt"
grep -q 'Physical density: 160' "$out/display-density.txt"
python3 - "$adb" "$poster" "$journal" "$out" <<'NATIVE'
from pathlib import Path
import json
import re
import subprocess
import sys
adb, poster, journal, destination = sys.argv[1:]
out = Path(destination)
results = []
for app, directory, runner in [('poster', poster, 'PosterInstrumentation'), ('journal', journal, 'JournalInstrumentation')]:
    row = {'app': app, 'runtime_api': 26, 'passed': False, 'assertions': 0}
    log = out / (app + '-instrumentation.txt')
    try:
        for variant, suffix, test in [('debug', 'debug', False), ('androidTest/debug', 'debug-androidTest', True)]:
            apk = str(Path(directory, 'apps', app, 'build/outputs/apk', variant, app + '-' + suffix + '.apk'))
            command = [adb, '-s', 'emulator-5554', 'install', '-r'] + (['-t'] if test else []) + [apk]
            install = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90)
            with (out / (app + '-install.txt')).open('a') as handle:
                handle.write(install.stdout)
            if install.returncode:
                raise RuntimeError('App/test APK installation failed')
        # -r is essential: without it Android formats only the stream value.
        command = [adb, '-s', 'emulator-5554', 'shell', '-n', 'am', 'instrument', '-w', '-r', 'dev.appmatrix.' + app + '.test/dev.appmatrix.' + app + '.' + runner]
        test = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=240)
        log.write_text(test.stdout)
        print(test.stdout, flush=True)
        passed = re.findall(r'^INSTRUMENTATION_RESULT: passed=(\d+)\s*$', test.stdout, re.M)
        codes = re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)\s*$', test.stdout, re.M)
        if test.returncode or len(passed) != 1 or int(passed[0]) < 1 or codes != ['-1']:
            raise RuntimeError('Native runner did not report a successful raw result')
        row.update(passed=True, assertions=int(passed[0]))
    except subprocess.TimeoutExpired as error:
        partial = error.output or b''
        log.write_text(partial.decode(errors='replace') if isinstance(partial, bytes) else partial)
        row['error'] = 'Native command exceeded its bounded timeout'
        try:
            subprocess.run([adb, '-s', 'emulator-5554', 'shell', '-n', 'am', 'force-stop', 'dev.appmatrix.' + app], timeout=20, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass
    except Exception as error:
        row['error'] = str(error)
    # Capture diagnostics for each app, even when its assertions failed.
    try:
        if row['passed']:
            subprocess.run([adb, '-s', 'emulator-5554', 'shell', '-n', 'am', 'start', '-W', '-n', 'dev.appmatrix.' + app + '/.MainActivity'], timeout=60, check=True, stdout=subprocess.DEVNULL)
        screen = subprocess.run([adb, '-s', 'emulator-5554', 'exec-out', 'screencap', '-p'], timeout=30, check=True, stdout=subprocess.PIPE)
        (out / (app + '-final-screen.png')).write_bytes(screen.stdout)
    except Exception as error:
        row['capture_error'] = str(error)
    results.append(row)
    print(json.dumps(row), flush=True)
(out / 'native-results.json').write_text(json.dumps(results, indent=2) + '\n')
if not all(row['passed'] for row in results):
    raise SystemExit('At least one native suite failed; both apps were attempted once.')
NATIVE
printf 'Both native suites passed on API26 software emulation only. API35 runtime is not validated by this experiment.\n' > "$out/NATIVE-RESULT.txt"
# Native failures above stop here. UI/video outcomes remain a separate hard gate.
if bash "$harness/tools/ui-recovery/run_ui_smoke.sh" "$out/ui"; then
  printf 'API26 native and bounded UI/video checks passed for both apps. API35 and physical-device runtime remain untested.\n' > "$out/RESULT.txt"
else
  printf 'API26 native checks passed; at least one bounded UI/video check failed. See per-app UI reports.\n' > "$out/RESULT.txt"
  exit 1
fi
