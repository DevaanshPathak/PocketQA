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

## Live execution status

Legend: `[x]` verified, `[~]` implemented but awaiting an external/device
verification, `[ ]` not started.

### Phase 0 — contract and device spike

- [x] Lock provider authority, signature permission, v1 JSON schema, source
  keys, and current demo bug IDs in `bug_app/CRASH_REPORTING.md`.
- [x] Implement the Buggy App provider/native bridge and PocketQA reader.
- [x] Verify PocketQA declares the provider read permission and installs on the
  physical iQOO I2501 (Android 16, Snapdragon 8 Gen 2, arm64-v8a, 11 GB RAM).
- [x] Select LiteRT-LM + Gemma 4 E4B GPU artifact:
  `gemma-4-E4B-it-gpu.litertlm`.
- [x] Add model install contract, GPU manifest declarations, background runtime
  initializer, and phone transfer script.
- [x] Download and transfer the 3.0 GB GPU model artifact into PocketQA private
  internal storage through `adb run-as`; Android 16 denied app access to the
  shell-written external copy.
- [x] Upgrade the PocketQA build toolchain for LiteRT-LM (AGP 9.1 / Gradle
  9.3.1 built-in Kotlin); signed debug build and focused unit tests pass.
- [x] Install the model on the physical phone and verify a real offline response
  (932 ms after model load in the latest smoke check). GPU is selected; NPU is
  deliberately not claimed.

### Phase 1 — exploration and live run state

- [x] Existing proof reads Flutter Semantics and executes deterministic actions.
- [x] Add a thread-safe `SessionStore` as the current-run source of truth,
  including unit coverage for start, trace retention, finding de-duplication,
  and stop.
- [x] Publish real observation, tap, input, finding, completion, and stop
  events from the accessibility runner.
- [x] Bound direct service exploration with a 20-action budget and 30-second
  safety timeout; retain the known-good deterministic testbed trace.
- [x] Add a selectable Gemma-assisted exploration mode: the local GPU model
  chooses one currently visible semantic action, PocketQA executes only that
  validated action, then continues with the bounded deterministic safety trace.
- [x] Add a separate Gemma autonomous mode: the model chooses every action from
  the current Semantics tree, with an 8-action/30-second safety bound and no
  deterministic action fallback.

### Phase 2 — product UI

- [x] Replace the proof-only activity with a native app selector and one
  full-app test action.
- [x] Add a live action-log screen, finding summary, and stop action connected
  to the actual accessibility runner.
- [x] Verify the UI in the signed debug APK on the physical iQOO.
- [x] Add issue and diagnosis views, including deterministic and local-Gemma
  modes, source excerpt, recent trace, diff, and local-model failure fallback.

### Phase 3 — local diagnosis and data

- [x] Implement `CrashReportContract` and `CrashReportReader`.
- [x] Implement a narrow source corpus contract and `LocalSourceLookup`.
- [x] Add a Gradle source-corpus sync task for the approved Buggy App files.
- [~] Verify provider delivery from a freshly installed Buggy App APK; this is
  pending a fresh crash-producing build from the Buggy App owner.
- [x] Implement `PatchWriter` and Android Share action.

### Phase 4 — deterministic diagnosis

- [x] Add `KnownBugCatalog` with explanation, reproduction, source excerpt,
  and diff template for the deterministic demo findings.

### Phase 5 — local inference

- [x] Add the LiteRT-LM runtime scaffold and GPU/MTP configuration.
- [x] Add physical-device smoke-test and three-pass GPU thermal benchmark
  controls. They report LiteRT runtime TTFT and decode throughput and enable
  bounded model thinking; they do not falsely claim NPU execution.
- [x] Verify a real offline GPU smoke response on the physical iQOO: “PocketQA
  is running offline on this phone.” generated in 883 ms after model load.
- [x] Validate model initialization and a real short offline prompt on the
  iQOO. Diagnosis prompts use the same GPU runtime, with deterministic fallback
  if inference is unavailable.
- [~] Validate an end-to-end generated diagnosis from a freshly completed
  accessibility run after manually refreshing the system accessibility binding.

### Phase 6 — visual fallback

- [ ] Raise controller `minSdk` to 30 and declare accessibility screenshot
  capability.
- [ ] Add sparse-semantics detection, screenshot capture, visual prompt, and
  validated gesture fallback.

### Phase 7 — rehearsal and lock

- [ ] Complete two offline, freshly restarted phone rehearsals.
- [ ] Record autonomous and deterministic fallback paths.
- [ ] Lock README, architecture diagram, and submission assets.

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
5. **Use LiteRT-LM for local inference.** The selected model is
   `litert-community/gemma-4-E4B-it-litert-lm`, GPU artifact
   `gemma-4-E4B-it-gpu.litertlm`; retain an `InferenceEngine` seam for fallback.
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

## Post-download: Gemma-only visual exploration plan

**Dependency:** install the full multimodal artifact `gemma-4-E4B-it.litertlm`
(not the 2.97 GB `-gpu` artifact). The full artifact is required before
PocketQA may claim Gemma sees screenshots.

### A. Prove image inference on the demo phone

- [ ] Run a dedicated offline image smoke test using a captured Buggy App PNG
  and `Content.ImageFile`; record model load time, vision first response, and
  one grounded action.
- [ ] Fail closed: if the vision encoder is absent or image inference fails,
  show "vision unavailable" and never label the text-only path as visual AI.
- [ ] Confirm LiteRT GPU is active in device logs and test in airplane mode.

**Exit criteria:** Gemma describes a captured Buggy App screen and returns a
valid action derived from pixels, not only Semantics text.

### B. Replace autonomous navigation with a Gemma tool loop

- [ ] In `GEMMA_AUTONOMOUS`, capture a screenshot on every newly observed
  screen/state, then supply it with a compact Semantics summary and recent
  trace.
- [ ] Give Gemma a strict local tool schema: `inspect_screen`, `tap_label`,
  `tap(x,y)`, `scroll(direction)`, `type(text)`, `back`, `wait`, and
  `report_issue`.
- [ ] Validate every tool call locally: target package, visible label, screen
  coordinates, editable target, and one action at a time.
- [ ] Include plus/minus controls as first-class candidates even when their
  accessible labels are empty; use screenshot grounding plus bounds for this
  visual-action path.
- [ ] Re-capture after every action and have Gemma compare expected versus
  observed state before its next tool call.

**Exit criteria:** the Buggy App visibly receives Gemma-selected add, plus,
minus, checkout, scroll, and text-input actions; every trace entry is marked
Gemma-driven.

### C. Replace the fixed action count with safe loop controls

- [ ] Do not use the general 20-action budget for Gemma autonomous runs.
- [ ] Retain Stop and use a configurable session deadline, repeated-screen
  hash limit, repeated-action limit, invalid-tool-call limit, and thermal or
  memory abort instead.
- [ ] Persist screenshot/state hashes and the action trace in the active
  session so loop termination is explainable and reproducible.

**Exit criteria:** autonomous mode can visit all reachable test screens and
stops only with a precise user, model, loop, thermal, or memory reason.

### D. Make reporting and patches source-grounded

- [ ] Require `report_issue` to contain title, visible evidence, trace IDs,
  confidence, and a `sourceKey` selected only from the bundled source manifest.
- [ ] Keep deterministic assertions out of Gemma autonomous mode; they remain
  available only in explicitly selected deterministic/assisted modes.
- [ ] Resolve the validated `sourceKey` through `LocalSourceLookup`; pass that
  local excerpt, screenshot evidence, and trace to Gemma for a unified diff.
- [ ] Validate diff headers and reject paths outside the bundled corpus. If
  source mapping is uncertain, show evidence and say so instead of inventing
  a patch.

**Exit criteria:** every Gemma visual issue opens local source, produces a
scoped diff or honest source-mapping uncertainty, and saves offline.

### E. Use a two-stage local diagnosis and patch pipeline

- [ ] Add a lightweight on-device SLM triage step before patch generation. It
  classifies the evidence as UI, state, data-validation, lifecycle, crash, or
  unknown; assigns severity and confidence; and proposes a compact source/RAG
  query. This stage does not generate code.
- [ ] Send only actionable, sufficiently confident triage results to Gemma 4
  for diagnosis and patch generation. Its context contains the category,
  screenshot evidence, trace, crash report (if present), and only the local
  source chunks retrieved for the issue.
- [ ] If the SLM is uncertain, request more exploration evidence or present an
  issue without a patch. If Gemma cannot map the issue to retrieved source, it
  must abstain rather than inventing a diff.
- [ ] Require Gemma to return a scoped unified diff and cite the `sourceKey`
  and line ranges used. Validate that every changed path is in the local corpus
  manifest, reject traversal or oversized diffs, and never auto-apply a patch.
- [ ] Save and share the validated patch only after presenting it to the user.

**Exit criteria:** each patchable issue has a local triage category, confidence,
retrieval evidence, source citations, and a manifest-scoped diff; uncertain
issues remain evidence-backed reports rather than hallucinated fixes.

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
