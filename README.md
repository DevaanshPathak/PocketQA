# PocketQA Android MVP

PocketQA is an Android QA controller that drives another installed app through
`AccessibilityService`, records a structured trace, detects known UI failures, and
generates source-grounded patches with an on-device Gemma model. Runtime testing and
local patching work without a laptop or network.

- `bug_app/` is the actual five-bug PocketQA testbed.
- `controller/` is the native Kotlin controller and local inference app.

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

## Repository-grounded patches

In PocketQA's Scanner tab, enter a credential-free HTTPS Git repository URL, a
branch or tag, and the relative subfolder containing the target app (for example,
`apps/mobile`). For a private repository, enter a token in the separate masked field.
The token is used for that clone only and is not persisted. The shallow clone,
selected subfolder, and lexical RAG index stay in PocketQA's private app storage.
The last successful selection is restored when the app restarts.

From a finding, open Diagnosis and choose **Generate patch from indexed repo**.
PocketQA retrieves relevant source chunks, classifies prompt size, and either:

- uses the installed Gemma LiteRT model for a small grounded change;
- uses OpenRouter for a larger change only when the user has enabled BYOK; or
- abstains when the evidence or configured runtime is insufficient.

Generated text is exposed for save/share only after unified-diff paths are checked
against the indexed source manifest. OpenRouter keys are encrypted with Android
Keystore and can be removed with **Clear BYOK key**. Cloud escalation is optional;
when enabled, the finding, trace, and retrieved source context leave the device.

## Custom MCP source tools

Create an allowlisted corpus, then run the dependency-free stdio MCP server:

```powershell
.\scripts\prepare-repo-corpus.ps1 -Repo D:\path\to\repo -OutputDir D:\PocketQA-corpus
python .\scripts\repo_mcp_server.py --corpus D:\PocketQA-corpus
```

The server exposes `search_source`, `read_source`, and `validate_patch`. It only
reads files listed in the generated manifest and rejects path traversal. Configure
your MCP client to launch the command above over stdio.

## 30-second recording script

1. Open PocketQA and enable **PocketQA Semantics Reader** if needed.
2. Tap **Open buggy app and run 5 tests**.
3. Do not touch the emulator while PocketQA drives Catalog, Cart, and Checkout.
4. PocketQA returns automatically after the final freeze check.
5. End on the on-device **5/5 bugs found** report.

To repeat, close the system ANR dialog if it appears, then press the test button again.

## Current MVP boundaries

PocketQA generates and exports a reviewed `.diff`; it does not automatically modify,
commit, or push the cloned repository. Guided Gemma navigation remains bounded and
uses deterministic coverage for demo reliability. OpenRouter escalation requires a
network connection and explicit BYOK opt-in; normal test execution and local Gemma
diagnosis remain on-device.
