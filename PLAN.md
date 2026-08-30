# PocketQA build plan — fully local / offline

## Outcome

PocketQA is an Android app that autonomously explores the companion Buggy App,
records its real UI actions, detects a failure, retrieves the relevant local
source, and presents a reproducible diagnosis and patch suggestion. The full
demo works on the phone with no network or laptop runtime dependency.

The repository already contains the highest-risk feasibility proof:
`controller/` has an `AccessibilityService` that reads the real Flutter
Semantics tree in `bug_app/`, performs actions, and reports deterministic
findings. This plan evolves that proof; it does not replace it.

## Scope and non-goals

### In scope

- On-device Android exploration through `AccessibilityService`.
- A native PocketQA UI: goals, live action trace, issue report, diagnosis, and
  save/share patch action.
- Crash ingestion from the Buggy App through a local Android `ContentProvider`.
- Bundled local source corpus and a source-key lookup.
- Deterministic diagnosis/patch templates for every demo bug.
- On-device local inference for diagnosis and the low-semantics visual fallback.
- A rehearsable deterministic fallback trace.

### Explicitly out of scope

- HTTP services, ADB/logcat, laptop pairing, cloud inference, or runtime file
  fetches.
- Editing the Buggy App's installed source code from PocketQA.
- General-purpose fuzzing or arbitrary third-party-app testing.
- Persistent history unless the core loop is complete early.
- Rebuilding PocketQA as Flutter. Keeping `controller/` native preserves the
  existing working accessibility proof and avoids an unnecessary MethodChannel
  integration during the hackathon.

## Architecture

```text
Buggy App (Flutter)                         PocketQA (native Android)
------------------                          -------------------------
Semantics tree ---------------------------> PocketQaAccessibilityService
Crash ContentProvider --------------------> CrashReportReader
Bundled source manifest/assets -----------> LocalSourceLookup
                                               |
                                               v
                                        ExplorationOrchestrator
                                         |        |        |
                                   ActionExecutor Rules  InferenceEngine
                                               |                 |
                                               v                 v
                                           SessionStore <--- ScreenshotCapture
                                               |
                                               v
                                        MainActivity product UI
                                               |
                                               v
                                  PatchWriter + optional Share action
```

`SessionStore` is the single in-memory source of truth for the current run. It
contains run status, action trace, screenshots, issue signals, and diagnosis.
The UI observes it; the service and readers publish into it.

## Locked implementation choices

1. **PocketQA UI stays native.** Extend `controller/src/main/java/.../MainActivity.kt`
   rather than creating a second Flutter app.
2. **Crash transport uses a signature-protected `ContentProvider`.** Do not use
   shared external-storage files or implicit broadcasts.
3. **Rule-based detection and known-good patch templates are mandatory.** Local
   AI enhances the demo; it is not allowed to be its only working path.
4. **Use `AccessibilityService.takeScreenshot()` for visual fallback on the demo
   device.** Set controller `minSdk` to 30 and declare screenshot capability in
   the service metadata. This avoids a `MediaProjection` permission prompt.
5. **Use an `InferenceEngine` interface.** First spike the selected local model
   on the physical phone. MediaPipe LLM Inference is usable, but is currently
   maintenance-only; retain a LiteRT-LM-compatible implementation seam.
6. **The model is installed on-device during setup, not fetched at runtime.**
   This preserves the phone-only/offline runtime claim even if the model is too
   large to package in the APK.

## Inter-app contract (lock before implementation)

### Crash provider

| Field | Value |
| --- | --- |
| Provider authority | `com.pocketqa.pocketqa.crashes` |
| Read URI | `content://com.pocketqa.pocketqa.crashes/latest` |
| Permission | `com.pocketqa.pocketqa.permission.READ_CRASH_REPORT` |
| Permission protection | `signature` |
| Debug signing | Both apps use the same debug signing key |

The Buggy App retains crash reports in its private app storage and exposes the
latest record through this provider. PocketQA registers a `ContentObserver`
and also queries while a run is active. It deduplicates on `id`.

### Crash report JSON

```json
{
  "schemaVersion": 1,
  "id": "uuid",
  "capturedAtMs": 1787970000000,
  "appPackage": "com.pocketqa.pocketqa",
  "fatal": true,
  "exceptionType": "RangeError",
  "message": "index out of range",
  "frames": [
    {
      "sourceKey": "lib/ui/screens/catalog_screen.dart",
      "line": 84,
      "function": "CatalogScreen.build"
    }
  ],
  "triggerHint": "scroll to final item and tap Add"
}
```

The Buggy App must report both uncaught Dart errors (`PlatformDispatcher` and
isolate error handlers) and native uncaught exceptions where applicable.

### Source corpus

PocketQA bundles only the Buggy App files relevant to the demo under
`controller/src/main/assets/sources/`. The accompanying manifest maps stable
source keys to asset paths:

```json
{
  "lib/ui/screens/catalog_screen.dart": "sources/catalog_screen.dart",
  "lib/state/cart_provider.dart": "sources/cart_provider.dart"
}
```

Every planted bug requires a `bugId`, its source key and line range, stable
trigger sequence, expected evidence, and a known-good unified diff.

### Internal event model

All detections become an `IssueSignal`, so crashes and non-crash failures use
the same report screen:

```text
IssueSignal
  id, source(crash | semantic_assertion | timeout | visual_assertion), severity
  title, evidence, sourceKey?, line?, actionTraceIds, screenshotPath?
```

## Repository work map

| Area | Planned files |
| --- | --- |
| UI and state | `controller/.../MainActivity.kt`, new `SessionStore.kt`, `UiState.kt` |
| Exploration | refactor `PocketQaAccessibilityService.kt`; add `ExplorationOrchestrator.kt`, `ActionExecutor.kt`, `RuleDetectors.kt` |
| Local data | add `CrashReportReader.kt`, `LocalSourceLookup.kt`, `PatchWriter.kt`, source assets + manifest |
| Screenshots | add `ScreenshotCapture.kt`; update `accessibility_service_config.xml` |
| On-device model | add `InferenceEngine.kt`, `ModelRuntimeBridge.kt`, `PromptBuilder.kt` |
| Android config | `controller/build.gradle.kts`, `controller/src/main/AndroidManifest.xml` |
| Buggy App teammate | provider, report writer, crash handler, source manifest, documented triggers |
| Presentation | root `README.md`, architecture diagram, demo script and fallback path |

## Ordered delivery plan

### 0. Contract and device spike — hours 0–2

- Agree and commit the provider authority, permission, JSON, source keys, and
  known bug IDs with the Buggy App owner.
- Verify both APKs are signed with compatible debug certificates.
- On the actual demo phone, load the chosen local model and measure cold start,
  time-to-first-token, and a short diagnosis prompt. Do not use an emulator as
  the acceptance environment.
- Decide by hour 2: selected model runtime, model storage path, and whether the
  visual inference path is viable.

**Exit criteria:** PocketQA can query a sample provider response, and the model
can produce one response offline on the physical device.

### 1. Preserve and structure exploration — hours 2–6

- Keep the existing deterministic checks working.
- Extract direct service logic into `ExplorationOrchestrator` and
  `ActionExecutor`; retain its current known-good trace as the fallback.
- Emit an action event for node perception, click, text input, scroll, wait,
  detector result, and completion.
- Add an action budget and per-step timeout so an exploration run ends safely.

**Exit criteria:** Starting a run produces a live structured action trace while
still detecting the current known issues.

### 2. Build the product UI — hours 4–10 (parallel with step 1)

- Replace the proof-only activity with three simple states: Goal Picker, Run,
  and Issue/Diagnosis.
- Render the live action trace from `SessionStore`.
- Add clear loading, model failure, provider failure, and no-issue states.
- Do not add history until the full loop works.

**Exit criteria:** A user can select a goal, start/stop a run, watch real
actions, and open a detected issue.

### 3. Complete local diagnosis/data — hours 6–12

- Implement `CrashReportReader` and its JSON validation/deduplication.
- Bundle source assets and implement lookup by `sourceKey`.
- Implement `PatchWriter` to save a `.diff` to PocketQA's app-specific
  Documents directory; expose a Share action through `FileProvider`.
- Display source filename, source excerpt, trace, and known-good diff for each
  issue.

**Exit criteria:** A real Buggy App crash reaches PocketQA, opens a bundled
source excerpt, and saves a patch offline.

### 4. Guarantee diagnosis before AI — hours 10–14

- Add `KnownBugCatalog`: trigger evidence -> issue title -> explanation ->
  source location -> patch template.
- Cover every demo bug, including semantic and timeout failures that do not
  crash.
- Validate each patch against its expected bug ID; never claim a patch is
  applied to the installed Buggy App.

**Exit criteria:** The complete perceived-action-detect-diagnose-save loop is
demoable with airplane mode enabled.

### 5. Add on-device inference — hours 14–20

- Implement `InferenceEngine` and a local runtime adapter.
- Send a bounded diagnosis prompt: crash/issue signal, 8–12 last actions, and
  a small source excerpt. Require JSON containing summary, likely cause,
  confidence, and suggested diff rationale.
- Schema-validate output; fall back to `KnownBugCatalog` on timeout, parse
  failure, or low confidence.
- Stream partial output to the diagnosis screen only if it is stable; otherwise
  show a simple loading state.

**Exit criteria:** One real crash gets a model-generated local explanation,
with template diagnosis functioning when the model is disabled.

### 6. Visual fallback — hours 18–24

- Add screenshot capability and `ScreenshotCapture`.
- Trigger it only when Semantics is sparse or the known low-semantics screen is
  active.
- Request one screenshot, ask the vision-capable local model for a normalized
  target/action, validate the returned coordinates, and execute by gesture.
- Limit to one or two visual attempts before falling back to the known trace.

**Exit criteria:** The low-semantics scenario visibly captures a screenshot and
either performs the intended gesture or safely explains the fallback.

### 7. Rehearsal and lock — hours 24–30

- Run the product in airplane mode twice from a freshly restarted phone.
- Rehearse autonomous and deterministic fallback paths.
- Ensure README, architecture diagram, source-corpus disclosure, and the
  phone-only runtime statement match the app's actual behavior.
- Build, install, and record the final artifacts before the final buffer.

## Acceptance checks

| Behavior | Evidence |
| --- | --- |
| Real semantics perception and action | Action trace shows actual target labels and executed Android actions |
| Crash ingestion | Provider test payload and a live Buggy App crash both create exactly one issue |
| Local source lookup | Each known issue resolves its expected source file and excerpt without network |
| Offline diagnosis | Airplane-mode run creates an issue report and template diff |
| Patch export | Saved `.diff` exists and Share action can open it |
| AI enhancement | Physical-device test returns schema-valid local output or visibly falls back |
| Visual fallback | Sparse screen creates a screenshot and respects action/timeout limits |
| Safety | Stop cancels pending actions; malformed provider/model data never crashes PocketQA |

## Risks and decisions

| Risk | Mitigation / decision |
| --- | --- |
| Model latency or incompatibility | Time-box device spike; templates remain the demo-critical path |
| MediaPipe API maintenance status | Keep inference behind an interface so LiteRT-LM can replace the adapter |
| Large model cannot fit APK | Preload it to app/device storage during setup; no runtime download |
| Dart errors do not reach Android uncaught handler | Buggy App explicitly forwards `PlatformDispatcher` and isolate errors to its provider |
| Non-crash bugs produce no provider event | Existing semantics rules and timeout detectors create `IssueSignal`s |
| Screenshot capture fails | UI continues with Semantics and known-good trace; screenshot is supplemental |
| UI polish consumes core-loop time | Freeze UI after three functional states; cut history first |
| Contract mismatch between apps | Use the JSON fixture and provider smoke test before integrating live crashes |

## Demo script state machine

1. Select **Checkout and cart quality**.
2. Show PocketQA switching to the Buggy App and logging real actions.
3. Show a detected issue with its evidence and reproduction trace.
4. Return to PocketQA and show source excerpt plus offline patch.
5. Run the low-semantics scenario: screenshot -> visual reasoning -> gesture, or
   explain that PocketQA used its known-good recovery trace.
6. Close with: **"Phone-only at runtime: no network, pairing, or laptop."**
