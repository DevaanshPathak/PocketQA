# PocketQA Architecture

## Purpose

PocketQA is a phone-local Android QA controller. It drives an installed target
application through Android accessibility APIs, records a structured run, maps
known findings to source evidence, and exports a reviewed unified diff. The
current demo target is the Flutter application in `bug_app/bugged/`, named
QuickCart.

The architecture is intentionally split into two applications:

```text
Android device
|
|-- PocketQA controller (`com.indium.pocketqa.controller`)
|   |-- MainActivity: configuration, monitor, diagnosis, export UI
|   |-- PocketQaAccessibilityService: observation and input execution
|   |-- LiteRtModelRuntime: local Gemma 4 E4B GPU/VLM calls
|   |-- SessionStore: current run state and findings
|   |-- source, crash, repository, and patch contracts
|
`-- QuickCart target (`com.quickcart.buggyapp`)
    |-- Flutter UI and navigation
    |-- Provider-based application state
    |-- Firebase-backed repositories (optional at demo runtime)
    `-- crash-report ContentProvider and six demo fixtures
```

The older `bug_app/legacy/` tree remains in the repository, but it is not the
current six-finding demo target.

## Deployable Components

### Controller application

The controller is a native Kotlin Android application in `controller/`.

- `MainActivity` owns the visible UI and switches between scanner, monitor,
  diagnosis, and compatibility-preserved analytics rendering paths.
- `PocketQaAccessibilityService` receives target-window accessibility events,
  converts the node tree to a semantic snapshot, selects a target profile, and
  performs bounded actions.
- `TestingOverlay` displays a non-interactive status overlay above the target
  while a run is active.
- `GestureDispatcher` sends coordinate taps and scroll gestures through the
  accessibility gesture API when semantic actions are unavailable.
- `SessionStore` is the thread-safe in-memory observable state boundary between
  the service and `MainActivity`.

The controller manifest declares the accessibility service, a `FileProvider` for
patch sharing, and visibility for the QuickCart package. The application label
and launcher icon are both `PocketQA`.

### QuickCart application

The target is a Flutter application in `bug_app/bugged/` with package
`com.quickcart.buggyapp`.

- `QuickCartApp` composes `AuthProvider`, `ProductProvider`, `CartProvider`,
  and `OrderProvider` with `provider`.
- `MainNavigationShell` exposes Home, Categories, Orders, and Profile views,
  with cart and checkout routes reached from the shell.
- Repositories use Firebase Auth, Firestore, and Storage when configured.
- The local catalog and the `main_demo.dart` entrypoint allow the demo to open
  without waiting for authentication or a Firestore round trip.
- The Android host exposes a read-only crash-report provider and a method
  channel used by `CrashReporter`.

## Runtime Flow

### Startup

Normal builds use `lib/main.dart`. They initialize Firebase defensively and
show the welcome/authentication flow until a user is authenticated.

Demo builds use `lib/main_demo.dart`. They initialize Firebase defensively,
install crash reporting, and run `QuickCartApp(demoMode: true)`. In demo mode,
the main navigation shell is available without an authenticated user.

The launcher script builds the demo entrypoint, builds the controller, installs
both APKs, enables the accessibility service, starts QuickCart, and returns to
the PocketQA activity. It first reuses the first connected ADB device; an AVD is
only started when no Android device is already connected.

### A guided run

```text
User selects Guided Gemma QA
        |
        v
MainActivity -> startTestRun(goal, target package, mode)
        |
        v
AccessibilityService resets state, starts timeout, clears screenshots,
launches QuickCart, and shows the testing overlay
        |
        v
Accessibility events for the target package
        |
        v
SemanticNode snapshot -> TargetProfile dispatch
        |
        +--> QuickCart fixture suite
        |       - seed cart
        |       - one Gemma screen assessment
        |       - deterministic six-fixture trace
        |
        +--> semantic actions, or screenshot/VLM fallback for sparse semantics
        |
        v
SessionStore records actions and findings
        |
        v
Completion routes the controller to the monitor/log view
        |
        v
Diagnosis -> bundled source lookup -> proposed diff -> FileProvider share
```

Only events whose package matches the active target are processed. A run is
bounded by an action budget and a mode-specific timeout. The controller can be
stopped by the user; the service then cancels pending callbacks, hides the
overlay, and records a stopped status.

### Target dispatch

`TargetProfile.forScreen` identifies QuickCart by package name or visible
labels. The current QuickCart profile selects the six-fixture suite for
deterministic and guided modes. The legacy profile retains the original
catalog/cart/checkout trace for the old package name. Unknown packages are sent
through the generic bounded handler.

## Perception and Input

Accessibility semantics are the primary observation and interaction channel.
The service recursively formats node class, label, clickability, and
scrollability into a `SemanticNode` snapshot. Labels are resolved to clickable
descendants so Flutter wrapper nodes can still drive actions.

When a screen has sparse semantics, `ScreenshotCapture` uses the API 30+
accessibility screenshot API and stores a PNG in the controller's private cache.
The local model receives a text description of the screen and recent actions,
then returns one bounded action: a semantic label, an in-bounds coordinate tap,
scroll, or back navigation. `VisualBugHighlighter` can annotate a captured
screenshot with a model-selected bug location for diagnosis.

The screenshot path is best-effort. A capture failure is recorded and the
bounded recovery path is used; it is not treated as proof that a visual issue
does not exist.

## Exploration Modes

`ExplorationMode` exposes three modes:

- `DETERMINISTIC`: runs the verified QuickCart fixture trace without model
  planning.
- `GEMMA_ASSISTED`: performs one local Gemma screen assessment, then runs the
  same verified six-fixture trace for repeatable coverage.
- `GEMMA_AUTONOMOUS`: experimental semantic/screenshot exploration with a
  separate timeout, visited-state graph, model retry limit, and action budget.

The demo presents Guided Gemma QA and Deterministic as the supported choices.
The deterministic trace remains the reliability mechanism even when local
model initialization or screenshot capture is unavailable.

## QuickCart Fixture Contract

The current QuickCart run reports six known findings:

| Finding | Deliberate path exercised | Mapped source |
| --- | --- | --- |
| Rapid cart quantity update race | Burst delayed quantity mutations | `lib/providers/cart_provider.dart` |
| Quantity zero boundary failure | Repeated decrease at the lower bound | `lib/providers/cart_provider.dart` |
| Rapid double save race | Two profile saves before delayed completion | `lib/ui/screens/profile/edit_profile_screen.dart` |
| Cancelled form mutates shared state | Change delivery preference, leave, reopen | `lib/ui/screens/settings/delivery_preferences_screen.dart` |
| Low-semantics visual hitbox mismatch | Open the `ExcludeSemantics` Fresh Picks view | `lib/ui/screens/experimental/low_semantics_screen.dart` |
| Final list item off-by-one | Scroll the extended category grid to its boundary | `lib/ui/screens/category/category_screen.dart` |

The service records each finding with evidence and captures the relevant screen
when possible. The catalog is deliberately deterministic for the demo; it is a
coverage contract, not a claim that every run produces a native crash.

## Model and Cloud Boundaries

`LiteRtModelRuntime` owns one LiteRT-LM engine on a single executor. It selects
the full multimodal Gemma artifact when present and otherwise falls back to the
GPU artifact. The configured backend is GPU; the code does not claim NPU
execution. The runtime supports bounded text prompts, screenshot prompts, and
three-pass GPU benchmark calls.

Cloud escalation is optional. `CloudEscalationConfig` stores the enabled flag
and model name in preferences and stores the OpenRouter key through
`SecretStore`, which encrypts it with an Android Keystore AES-GCM key.
`OpenRouterClient` implements the HTTP request boundary, but the current
six-finding demo path does not invoke it and does not send data to the cloud.

## Source and Patch Pipeline

### Bundled demo corpus

The controller build task copies five approved QuickCart source files into the
APK assets under `sources/`. `SourceCorpusContract` is the allowlist and
`LocalSourceLookup` reads only those assets. This is why the six demo findings
can show real source evidence and a reviewable diff without a GitHub connection
or a live repository clone.

### Optional repository indexing

`RepoCloneManager` supports a larger source-grounded workflow. It accepts only
credential-free HTTPS URLs, validates the target package and subfolder, performs
a shallow JGit clone, and indexes bounded chunks from approved source
extensions. The token is used for the clone operation but is not persisted.
Per-target repository bindings are stored in private app files and can be
cleared without deleting the clone.

The dependency-free MCP server in `scripts/repo_mcp_server.py` is an alternative
for a pre-allowlisted local corpus. It exposes `search_source`, `read_source`,
and `validate_patch` over stdio.

### Diff validation and export

`PatchPolicy` defines rejection rules for oversized diffs, missing unified
headers, traversal paths, absolute paths, and files outside allowed source
keys. It is covered by controller tests. The current built-in diagnosis path
uses catalog-owned diffs and `PatchWriter` writes the selected diagnosis under
the controller's external app-specific Documents directory. The export click
path does not currently call `PatchPolicy`; generated/repository-backed patch
validation is therefore an integration boundary still to wire into a broader
patch workflow. Export starts an Android share chooser. PocketQA does not
apply, commit, or push the patch.

## Crash-Report Boundary

QuickCart's `CrashReporter` converts uncaught Flutter errors into schema-version
1 JSON and sends it over the local method channel to the Android host. The host
stores only the latest report in app-private `SharedPreferences` and notifies
its `CrashReportProvider`.

The provider is read-only and protected by the signature permission
`com.quickcart.buggyapp.permission.READ_CRASH_REPORT`. `CrashReportReader`
implements validation of the schema, package, source-key shape, and optional
line number. The current six-finding UI does not automatically consume crash
reports; non-crash fixture findings are driven by the accessibility trace and
known catalog.

## As-Built Integration Boundaries

The repository, RAG, cloud, and crash contracts are intentionally isolated from
the deterministic demo path. `RepoCloneManager`, `SourceRagIndex`,
`DiagnosisPrompt`, `BugRouter`, `OpenRouterClient`, `CrashReportReader`, and
`PatchPolicy` have focused implementations and unit coverage, but the current
`MainActivity` flow uses `KnownBugCatalog`, `LocalSourceLookup`, and
`PatchWriter` for the six QuickCart findings. Connecting those helpers into a
single generated-patch workflow is separate work and should preserve the same
allowlist, privacy, and human-review boundaries.

## Storage and Lifetime

| Data | Location | Lifetime |
| --- | --- | --- |
| Current actions/findings | Controller process memory (`SessionStore`) | Current process/run |
| Raw and annotated screenshots | Controller cache | Cleared at run start or cache cleanup |
| Gemma model artifacts | Controller private `files/models` | Until uninstalled/removed |
| Cloned repositories and bindings | Controller private `files/repos` | Until cleared or uninstalled |
| BYOK key | Encrypted preferences backed by Android Keystore | Until cleared or uninstalled |
| Exported diffs | Controller external app-specific `Documents/patches` | Until app data/file cleanup |
| Latest QuickCart crash report | QuickCart private preferences | Replaced by the next report |

The current session is not a database-backed history. Recreating the controller
process clears the visible session state, although exported files and model/
repository files have their separate lifetimes.

## Security and Safety Controls

- The accessibility service must be explicitly enabled by the user/device
  before a run can start.
- Event handling is scoped to the selected target package.
- Semantic and visual actions are restricted by action budgets and timeouts.
- Visual coordinates are bounds-checked before gestures are dispatched.
- Repository URLs, package names, and subfolders are validated before cloning.
- Crash reports cross an Android signature-protected provider boundary.
- Patch paths are allowlisted and no patch is automatically applied.
- Cloud escalation is opt-in and requires a BYOK key.

## Build and Device Requirements

The current build expects Flutter/Dart 3.x compatible with Dart 3.13, Android
SDK 36, and JDK 21. The supported demo path is a physical Android device with
USB debugging, but the launch scripts can start an AVD when no device is
connected.

```powershell
Push-Location bug_app\bugged
flutter pub get
flutter test
flutter build apk --debug -t lib/main_demo.dart
Pop-Location

.\gradlew.bat :controller:testDebugUnitTest :controller:assembleDebug
```

The launcher scripts reuse a connected device first and print the selected ADB
serial. The current demo was verified on a Vivo I2501 over ADB.

## References

- [Project README](../README.md)
- [QuickCart bug fixtures](../bug_app/bugged/BUG_FIXTURES.md)
- [Crash-report contract](../bug_app/CRASH_REPORTING.md)
- [Controller manifest](../controller/src/main/AndroidManifest.xml)
- [Controller accessibility service](../controller/src/main/java/com/indium/pocketqa/controller/PocketQaAccessibilityService.kt)
- [Controller activity](../controller/src/main/java/com/indium/pocketqa/controller/MainActivity.kt)
