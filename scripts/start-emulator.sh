#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/Android/Sdk}}"
ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"
AVD_NAME="${1:-${POCKETQA_AVD:-}}"

[[ -x "$ADB" ]] || { echo "adb not found at $ADB. Set ANDROID_SDK_ROOT." >&2; exit 1; }
if ! command -v flutter >/dev/null; then
  for candidate in "${FLUTTER_ROOT:-}" "$HOME/flutter" "/opt/flutter"; do
    if [[ -n "$candidate" && -x "$candidate/bin/flutter" ]]; then
      export PATH="$candidate/bin:$PATH"
      break
    fi
  done
fi
command -v flutter >/dev/null || { echo "Flutter is required; add it to PATH or set FLUTTER_ROOT." >&2; exit 1; }

"$ADB" start-server >/dev/null
SERIAL="$("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1; exit}')"
if [[ -z "$SERIAL" ]]; then
  [[ -x "$EMULATOR" ]] || { echo "Android emulator not found at $EMULATOR. Set ANDROID_SDK_ROOT." >&2; exit 1; }
  if [[ -z "$AVD_NAME" ]]; then
    AVD_NAME="$($EMULATOR -list-avds | head -n 1)"
  fi
  [[ -n "$AVD_NAME" ]] || { echo "No Android device is connected and no AVD exists." >&2; exit 1; }
  echo "Starting emulator: $AVD_NAME"
  "$EMULATOR" -avd "$AVD_NAME" >/tmp/pocketqa-emulator.log 2>&1 &
  "$ADB" wait-for-device
  for _ in {1..120}; do
    SERIAL="$("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1; exit}')"
    [[ -n "$SERIAL" && "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
    sleep 2
  done
fi
[[ -n "$SERIAL" ]] || { echo "Emulator did not finish booting." >&2; exit 1; }

(cd "$REPO_ROOT/bug_app/bugged" && flutter pub get && flutter build apk --debug -t lib/main_demo.dart)
ANDROID_HOME="$ANDROID_SDK" "$REPO_ROOT/gradlew" :controller:assembleDebug

"$ADB" -s "$SERIAL" install -r "$REPO_ROOT/bug_app/bugged/build/app/outputs/flutter-apk/app-debug.apk"
"$ADB" -s "$SERIAL" install -r "$REPO_ROOT/controller/build/outputs/apk/debug/controller-debug.apk"
SERVICE='com.indium.pocketqa.controller/com.indium.pocketqa.controller.PocketQaAccessibilityService'
"$ADB" -s "$SERIAL" shell am force-stop com.indium.pocketqa.controller
"$ADB" -s "$SERIAL" shell am force-stop com.quickcart.buggyapp
"$ADB" -s "$SERIAL" shell am start -n com.indium.pocketqa.controller/.MainActivity >/dev/null
sleep 1
"$ADB" -s "$SERIAL" shell settings put secure accessibility_enabled 0
"$ADB" -s "$SERIAL" shell settings delete secure enabled_accessibility_services >/dev/null
sleep 1
BOUND=false
for _ in {1..30}; do
  # Cold emulators may clear these settings while registering a fresh install.
  "$ADB" -s "$SERIAL" shell settings put secure enabled_accessibility_services "$SERVICE"
  "$ADB" -s "$SERIAL" shell settings put secure accessibility_enabled 1
  sleep 1
  if "$ADB" -s "$SERIAL" shell dumpsys accessibility | grep -q 'Bound services:{Service\[label=PocketQA Semantics Reader'; then
    BOUND=true
    break
  fi
done
[[ "$BOUND" == true ]] || { echo "PocketQA accessibility service did not bind within 30 seconds." >&2; exit 1; }
"$ADB" -s "$SERIAL" shell am start -n com.quickcart.buggyapp/com.pocketqa.pocketqa.MainActivity >/dev/null
sleep 1
"$ADB" -s "$SERIAL" shell am start -n com.indium.pocketqa.controller/.MainActivity >/dev/null

echo "PocketQA controller is open; the buggy app is running in the background on $SERIAL."
echo "Logs: $ADB -s $SERIAL logcat -s PocketQA"
