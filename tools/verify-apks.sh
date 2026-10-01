#!/usr/bin/env bash
# Assert product identity, supported API floor, permission-free manifest, and licenses.
set -euo pipefail
cd "$(dirname "$0")/.."
app=${1:?Usage: tools/verify-apks.sh poster|journal}
case "$app" in poster|journal) ;; *) exit 2;; esac
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
tools="$ANDROID_HOME/build-tools/35.0.0"
for variant in debug release; do
  suffix=debug; [[ $variant != release ]] || suffix=release-unsigned
  apk="apps/$app/build/outputs/apk/$variant/$app-$suffix.apk"
  badging=$("$tools/aapt" dump badging "$apk")
  grep -Fq "package: name='dev.appmatrix.$app'" <<< "$badging"
  grep -Fxq "sdkVersion:'26'" <<< "$badging"
  grep -Fxq "targetSdkVersion:'35'" <<< "$badging"
  if "$tools/aapt" dump permissions "$apk" | grep -q 'uses-permission'; then
    echo "Unexpected permission in $apk" >&2; exit 1
  fi
  python3 - "$apk" <<'CHECK'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in ('AndroidManifest.xml', 'classes.dex', 'assets/licenses/GPL-3.0.txt', 'assets/licenses/Telegram-GPL-2.0.txt'):
        if not apk.read(name):
            raise SystemExit(f'Empty required APK content: {name}')
CHECK
  [[ $variant != debug ]] || "$tools/apksigner" verify "$apk"
  echo "Verified $app $variant APK identity, API 26+, no declared permissions, and bundled licenses."
done
