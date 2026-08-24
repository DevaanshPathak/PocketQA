# PocketQA Accessibility Feasibility Proof

This repository proves one claim only: Android `AccessibilityService` can read the
Semantics nodes produced by a real Flutter grocery app and act on them.

- `bug_app/` is the actual five-bug PocketQA testbed.
- `controller/` is the PocketQA native Kotlin prototype. Its accessibility service
  launches the testbed, executes five deterministic checks, and renders a report.

The **Open buggy app and run 5 tests** button checks:

1. Only two of three product semantics render after the null-text failure.
2. Two decrement taps produce a cart quantity of `-1`.
3. Empty checkout submission produces no validation-error semantics.
4. **Place Order** remains enabled immediately after a valid submission.
5. `FREEZE` + **APPLY** produces no target UI update for at least two seconds.

The freeze check runs last. PocketQA then returns to the foreground and displays
the evidence collected for every detected bug.

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

1. Open PocketQA and enable **PocketQA Semantics Reader** if needed.
2. Tap **Open buggy app and run 5 tests**.
3. Do not touch the emulator while PocketQA drives Catalog, Cart, and Checkout.
4. PocketQA returns automatically after the final freeze check.
5. End on the on-device **5/5 bugs found** report.

To repeat, close the system ANR dialog if it appears, then press the test button again.

## Intentional scope

There is no model, screenshot/VLM path, crash detector, backend, or patch generation.
The application proof isolates the riskiest premise instead of implying an unfinished
end-to-end product.
