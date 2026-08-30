# PocketQA Android MVP

PocketQA is an Android QA controller that drives another installed app through
`AccessibilityService`, records a structured trace, detects known UI failures, and
exports source-grounded patch suggestions. Runtime testing, bundled-source diagnosis,
and local Gemma guidance work without a laptop or network.

- `bug_app/bugged/` is the QuickCart six-fixture testbed.
- `controller/` is the native Kotlin controller and local inference app.

The **Start Exploration** button checks:

1. Rapid cart mutations race through a delayed stale snapshot.
2. Repeated decrement actions exercise the quantity lower-bound failure.
3. Two quick profile saves enter the duplicate-save path.
4. Cancelling delivery preferences retains mutated shared state.
5. The low-semantics Fresh Picks screen exposes a visual hitbox mismatch.
6. The extended category grid reaches its final off-by-one boundary.

Guided mode performs one real on-device Gemma screen assessment, then uses the
verified six-fixture trace for repeatability. If the model or screenshot is unavailable,
the deterministic trace continues and records the fallback.

## Build

Prerequisites: Flutter 3.x compatible with Dart 3.13, Android SDK 36, JDK 21,
and an Android phone with USB debugging.

```powershell
Push-Location bug_app\bugged
flutter pub get
flutter test
flutter build apk --debug -t lib/main_demo.dart
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
adb install -r bug_app\bugged\build\app\outputs\flutter-apk\app-debug.apk
adb install -r controller\build\outputs\apk\debug\controller-debug.apk
```

## Source-grounded patches

The controller APK bundles the five distinct QuickCart source files used by the six
demo findings. Diagnosis therefore shows real source evidence and a reviewable diff
offline, without requiring a repository clone.

In PocketQA's Scanner tab, enter a credential-free HTTPS Git repository URL, a
branch or tag, and the relative subfolder containing the target app (for example,
`apps/mobile`). For a private repository, enter a token in the separate masked field.
The token is used for that clone only and is not persisted. The shallow clone,
selected subfolder, and lexical RAG index stay in PocketQA's private app storage.
The last successful selection is restored when the app restarts.

Repository indexing is optional. It supports larger source-grounded workflows where
PocketQA retrieves relevant chunks and either:

- uses the installed Gemma LiteRT model for a small grounded change;
- uses OpenRouter for a larger change only when the user has enabled BYOK; or
- abstains when the evidence or configured runtime is insufficient.

OpenRouter keys are encrypted with Android
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

## Demo recording script

1. Open PocketQA and enable **PocketQA Semantics Reader** if needed.
2. Select **Guided Gemma QA** and tap **Start Exploration**.
3. Do not touch the device while PocketQA drives QuickCart.
4. PocketQA returns automatically after the final category-boundary check.
5. End on the on-device **6/6 findings** report, open one Diagnosis, and tap
   **Export Patch** to show the Android share sheet.

Run this sequence twice before recording. Use deterministic mode as the immediate
fallback if the local model cannot initialize on the demo device.

## Current MVP boundaries

PocketQA exports a reviewed `.diff`; it does not automatically modify, verify, commit,
or push a repository. Guided Gemma navigation remains bounded and
uses deterministic coverage for demo reliability. OpenRouter escalation requires a
network connection and explicit BYOK opt-in; normal test execution and local Gemma
assessment remain on-device.
