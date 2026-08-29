# QuickCart — PocketQA Planted Bug Fixtures Documentation

This document describes the three planted deterministic bugs and the low-semantics VLM fallback screen in the **QuickCart** test target application for PocketQA autonomous QA & debugging evaluation.

---

## Bug 1: Null Safety Failure
- **Name**: Photo Selection Cancelled Null Dereference
- **Location**: `lib/ui/screens/profile/edit_profile_screen.dart`
- **Preconditions**: User is on Edit Profile screen.
- **Trigger Sequence**:
  1. Open Profile tab → Tap `Edit Profile`.
  2. Tap `Change Photo` button to open the photo selector.
  3. Tap `Cancel Selection` (leaves `_selectedPhotoPath` as `null`).
  4. Tap `Save Changes`.
- **Expected Exception**: `TypeError` / `Null check operator used on a null value` (`Null check operator used on a null value`).
- **Source Line**: `lib/ui/screens/profile/edit_profile_screen.dart:104` (`final String photoPath = _selectedPhotoPath!;`)
- **Expected Diagnosis**: The application attempts to unconditionally dereference `_selectedPhotoPath!` after user cancellation set it to `null`.
- **Expected Fix**: Add a null guard before saving, e.g., `if (_selectedPhotoPath != null) { ... }`.

---

## Bug 2: Lifecycle / State Failure
- **Name**: Disposed State Controller Access on Lifecycle Transition
- **Location**: `lib/ui/screens/settings/delivery_preferences_screen.dart`
- **Preconditions**: User is on Delivery Preferences screen.
- **Trigger Sequence**:
  1. Open Profile / Settings → Tap `Delivery Preferences`.
  2. Modify any preference value (e.g., select a new delivery time slot).
  3. Transition app state (e.g., background/rotate app or trigger lifecycle pause/resume).
  4. Tap `Save Preferences`.
- **Expected Exception**: `StateError` (`Bad state: Cannot access disposed state controller after lifecycle state change.`).
- **Source Line**: `lib/ui/screens/settings/delivery_preferences_screen.dart:52` (`_stateController.updateState('SAVED');`)
- **Expected Diagnosis**: The screen's state controller is disposed when the app lifecycle changes or state is updated, but subsequent save actions attempt to access the stale/disposed instance.
- **Expected Fix**: Check `!_stateController.isDisposed` or re-initialize controller upon lifecycle resume.

---

## Bug 3: List Index Out of Range Exception
- **Name**: Category Boundary Item Index Overflow
- **Location**: `lib/ui/screens/category/category_screen.dart`
- **Preconditions**: User is on Category / Catalogue screen with 20+ products.
- **Trigger Sequence**:
  1. Open Categories tab.
  2. Scroll down to the bottom of the long product list.
  3. Tap on one of the final boundary items (index >= length - 2).
- **Expected Exception**: `RangeError` / `IndexError` (`RangeError (index): Invalid value: Not in inclusive range 0..23: 27`).
- **Source Line**: `lib/ui/screens/category/category_screen.dart:94` (`BugFixtures.triggerBug3IndexOverflow(index, extendedProducts.length);`)
- **Expected Diagnosis**: Item tap handler calculates index relative to `index + 5`, exceeding the bounds of the product array.
- **Expected Fix**: Ensure tap handler accesses `extendedProducts[index]` directly without adding static offsets.

---

## Low-Semantics VLM Screen
- **Name**: Fresh Picks VLM Test Screen
- **Location**: `lib/ui/screens/experimental/low_semantics_screen.dart`
- **Purpose**: Exercises PocketQA's visual perception & VLM fallback pipeline.
- **Characteristics**:
  - Visually looks like a normal, high-quality grocery product grid.
  - Wrapped inside `ExcludeSemantics`, stripping Flutter's accessibility node tree.
  - PocketQA's accessibility inspection will find minimal semantics nodes, forcing the agent to take a screenshot, use VLM visual grounding, and calculate physical tap coordinates to perform actions.
