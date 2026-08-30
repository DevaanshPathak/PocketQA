# PocketQA Decisions

This is an as-built decision log for the current PocketQA demo. It records why
the implementation uses its present boundaries; it is not a list of unimplemented
roadmap items.

## D-001: Keep the controller and target as separate applications

**Decision:** Build PocketQA as a native Android controller and keep QuickCart as
an independently installed Flutter application.

**Why:** Android accessibility services, overlay windows, screenshot capture, and
gesture dispatch are platform concerns. Keeping them in the controller lets the
target remain a realistic app with its own navigation and state management.

**Consequence:** The demo requires two APKs and a package-level boundary, but
PocketQA can observe the same way it would observe another installed app. The
controller filters events by the selected target package.

## D-002: Use Android `AccessibilityService` as the primary control plane

**Decision:** Observe semantic nodes and invoke accessibility actions before
using coordinates.

**Why:** Semantics are more stable and explainable than hard-coded coordinates,
and the service can operate across Flutter screens without modifying the target
application.

**Consequence:** The device must explicitly enable PocketQA Semantics Reader.
The service includes label-to-clickable-descendant resolution because Flutter
often exposes a label on a non-clickable wrapper.

## D-003: Add a screenshot/VLM fallback for sparse semantics

**Decision:** Treat screenshots and coordinate gestures as a bounded fallback,
not the default interaction path.

**Why:** QuickCart's Fresh Picks fixture intentionally uses `ExcludeSemantics`.
A semantics-only controller cannot inspect or activate that screen reliably.

**Consequence:** `ScreenshotCapture` and `VisualFallbackPrompt` add a second
perception path. Coordinates are bounds-checked, attempts are limited, and a
failed visual turn recovers with back navigation rather than looping forever.

## D-004: Make Guided Gemma QA hybrid rather than fully model-driven

**Decision:** In Guided Gemma QA, perform one real local Gemma screen assessment
and then execute the verified deterministic six-fixture trace.

**Why:** The model contributes a genuine on-device inference step while the
demo still reaches every finding repeatably. Model initialization and screenshot
capture can fail on a constrained phone; those failures must not erase the
coverage contract.

**Consequence:** Guided mode is evidence of local model use, not a benchmark of
fully autonomous coverage. Deterministic mode remains available as the fastest
fallback, and autonomous exploration is explicitly experimental.

## D-005: Use a dedicated demo entrypoint for QuickCart

**Decision:** Build the demo APK with `lib/main_demo.dart`, while preserving the
normal `lib/main.dart` authentication flow.

**Why:** The original startup path can wait on authentication and Firebase
state. A demo needs to open the full QuickCart shell immediately, while normal
application behavior should continue to require authentication.

**Consequence:** The build and launcher scripts must pass
`-t lib/main_demo.dart`. The demo still initializes Firebase defensively, so
Firebase-backed providers remain compatible when a connection is available.

## D-006: Bundle a small approved source corpus for the demo

**Decision:** Copy five known QuickCart source files into controller APK assets
and resolve them through an explicit allowlist.

**Why:** The demo needs source-grounded diagnosis and patch export without
depending on network access, GitHub availability, credentials, or a clone that
could change during recording.

**Consequence:** The offline path is limited to those approved files and the
known six findings. `LocalSourceLookup` cannot read arbitrary paths. Larger
projects use the optional repository or MCP indexing paths instead.

## D-007: Keep repository indexing optional and target-specific

**Decision:** Provide shallow HTTPS JGit cloning and lexical chunk-indexing
primitives, but do not require a repository for the QuickCart demo.

**Why:** A real project needs a way to retrieve source beyond the bundled demo
corpus. At the same time, the demo should work with no GitHub connection.

**Consequence:** PocketQA accepts a validated HTTPS URL, ref, and subfolder,
then stores a per-target binding in private app storage. A token may be used for
the clone but is not persisted. The local MCP server provides a dependency-free
alternative for an allowlisted corpus. In the current UI this is an indexed
source/integration surface; the six-finding diagnosis still uses the bundled
catalog and does not automatically generate a repository-backed patch.

## D-008: Make cloud escalation opt-in BYOK

**Decision:** Keep local Gemma as the normal path and make OpenRouter escalation
conditional on an explicitly enabled, user-supplied key.

**Why:** Screenshots, traces, crash reports, and source context can contain
sensitive project data. The demo should remain phone-local unless the user
chooses otherwise.

**Consequence:** The key is encrypted with Android Keystore AES-GCM through
`SecretStore`; an explicitly enabled caller may use `OpenRouterClient` to send
context off-device. The current six-finding demo does not invoke that client, so
a missing key or disabled setting leaves the demonstrated path local-only.

## D-009: Use a process-local observable session store

**Decision:** Keep the active run in a synchronized in-memory `SessionStore` and
publish immutable snapshots to the activity.

**Why:** The monitor and diagnosis screens need low-latency updates while the
accessibility service runs on its own callbacks. A small session boundary is
enough for the current single-run demo and avoids introducing a database.

**Consequence:** Actions are capped to the most recent 80 events and findings
are deduplicated by title. Restarting the controller process clears the visible
session; exported files and installed models have independent lifetimes.

## D-010: Share patches; never apply them automatically

**Decision:** Show a proposed diff, save it, and hand it to Android's share
chooser. Keep validation and human review as explicit boundaries; do not modify,
commit, or push the target repository.

**Why:** A QA controller should keep a human review point between diagnosis and
code changes. Automatic mutation would make a demo result difficult to audit
and would require repository write credentials and rollback behavior.

**Consequence:** `PatchPolicy` defines and tests unified-header, size-limit,
traversal, and allowed-source-path checks. The current built-in catalog export
uses `PatchWriter` to store the selected diff in the controller's app-specific
Documents directory and expose it via `FileProvider`; wiring policy validation
into generated/repository-backed export remains a separate integration step.

## D-011: Use a contract-protected crash-report boundary

**Decision:** QuickCart reports its latest Flutter error to a native Android
`ContentProvider`; a controller reader can validate it through a
signature-protected permission.

**Why:** The two apps need a narrow local handoff without a network service or a
shared database. A provider gives the controller a standard Android read path,
while the signature permission prevents unrelated apps from reading reports.

**Consequence:** Only the latest report is retained. `CrashReportReader` has
schema, package, source-key, and line validation, while the current six-finding
UI uses the accessibility fixture catalog and does not automatically consume
crash reports. Non-crash findings therefore do not require a native exception.

## D-012: Run one LiteRT engine on the GPU

**Decision:** Own a single LiteRT-LM engine on a background executor, prefer the
full multimodal Gemma 4 E4B artifact, and configure the GPU backend.

**Why:** The physical demo phone has a finite memory budget. Running multiple
engines or claiming an unsupported NPU path would make behavior less predictable.
The full artifact is needed for screenshot prompts; the GPU artifact remains a
fallback when the vision artifact is absent.

**Consequence:** Initialization, prompts, and benchmark passes are asynchronous
and bounded. The controller reports missing/failed model states and continues
with deterministic coverage where possible.

## D-013: Bound all exploration

**Decision:** Enforce action budgets, mode-specific timeouts, model retry limits,
visual-attempt limits, and explicit stop handling.

**Why:** Accessibility automation can otherwise repeat an action or remain on a
screen indefinitely. A demo needs predictable termination and a clear failure
state.

**Consequence:** A safety stop records an error or stopped status and hides the
overlay. The autonomous mode also tracks visited labels and state fingerprints;
the deterministic suite has its own bounded fixture sequence.

## D-014: Reuse a connected physical device before starting an emulator

**Decision:** Launcher scripts select the first ADB device in `device` state and
start an AVD only when no device is connected.

**Why:** The product behavior being demonstrated is Android accessibility and
local GPU inference on a real phone. A connected device gives more representative
screen, permission, and model-runtime behavior than an emulator.

**Consequence:** The scripts remain usable in CI or on a workstation with an
AVD, but a physical device is the preferred demo target. The current flow was
verified on a Vivo I2501 over ADB.

## D-015: Keep app and package branding consistent

**Decision:** Use `PocketQA` for the controller label, header, launcher assets,
and overlay text; use `QuickCart` for the target app.

**Why:** The former `PocketQA Proof` and `PocketQA AI` labels described internal
or provisional states rather than the product being demonstrated.

**Consequence:** APK labels, in-app names, and generated density-specific icons
now agree. The package IDs remain stable so the accessibility and crash-report
contracts do not change.

## Verification Evidence

The decisions above are grounded in the current source and test layout:

- Flutter tests cover crash-report serialization and demo startup behavior.
- Controller unit tests cover contracts, target routing, source allowlists,
  report rendering, and patch behavior.
- The demo APK is built with `lib/main_demo.dart` and the controller with the
  Gradle `assembleDebug` task.
- The six-finding Guided Gemma flow was run on a connected physical device;
  diagnosis and `.diff` export were exercised through the Android share sheet.

## References

- [Architecture](architecture.md)
- [Project README](../README.md)
- [QuickCart bug fixtures](../bug_app/bugged/BUG_FIXTURES.md)
- [Crash-report contract](../bug_app/CRASH_REPORTING.md)
- [Controller source](../controller/src/main/java/com/indium/pocketqa/controller/)
