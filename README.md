# PocketQA Accessibility Feasibility Proof

This repository proves one claim only: Android `AccessibilityService` can read the
Semantics nodes produced by a real Flutter grocery app and act on them.

- `bug_app/` is the actual five-bug PocketQA testbed.
- `controller/` is a native Kotlin app that logs the testbed's Semantics tree and
  runs one deterministic, safe script through standard Material widgets.

The script deliberately avoids triggering the planted failures:

1. Find and tap **Shopping cart** on the catalog.
2. Find and tap **ORDER NOW** in the empty cart.
3. Find and tap the first `android.widget.EditText` semantic node (the **Full Name** field)
   to open the keyboard and shrink the checkout viewport.
4. Scroll checkout with node-level `ACTION_SCROLL_FORWARD` when Flutter exposes it,
   otherwise use the service's `dispatchGesture` swipe fallback.

## Build

Prerequisites: Flutter 3.x compatible with Dart 3.13, Android SDK 34+, JDK 17+,
and an Android phone with USB debugging.

```powershell
Push-Location bug_app
flutter pub get
flutter test
flutter build apk --debug -t lib/main_buggy.dart
Pop-Location

.\gradlew.bat :controller:testDebugUnitTest :controller:assembleDebug
```

Or boot an available emulator, build and install both apps, enable the service, and
open the PocketQA controller (the buggy app remains running in the background):

```powershell
.\scripts\start-emulator.ps1
# or on Windows: scripts\start-emulator.bat
# or on macOS/Linux: bash scripts/start-emulator.sh [optional_avd_name]
```

Set `POCKETQA_AVD` to select an AVD; otherwise the first configured AVD is used.
Set `FLUTTER_ROOT` if Flutter is not on `PATH`. The Windows launcher also checks
`D:\Toolchains\flutter`, where Flutter is installed on this development machine.

Install both APKs:

```powershell
adb install -r bug_app\build\app\outputs\flutter-apk\app-debug.apk
adb install -r controller\build\outputs\apk\debug\controller-debug.apk
```

## 30-second recording script

1. Start recording with the phone and laptop terminal both visible.
2. Open **PocketQA Semantics Proof**, tap **Enable Accessibility Service**, and
   enable **PocketQA Semantics Reader**.
3. Run `adb logcat -c`, followed by `adb logcat -s PocketQA`.
4. Launch **PocketQA Testbed (Buggy)**. Do not touch the grocery app.
5. Capture the terminal showing labeled nodes and each `ACTION` line while the
   phone autonomously opens Cart, opens Checkout, focuses Full Name, and scrolls.
6. End on `DEMO COMPLETE: real Flutter Semantics drove all actions`.

To repeat, disable and re-enable the accessibility service, then relaunch the buggy app.

## Intentional scope

There is no model, screenshot/VLM path, crash detector, backend, or patch generation.
The application proof isolates the riskiest premise instead of implying an unfinished
end-to-end product.
