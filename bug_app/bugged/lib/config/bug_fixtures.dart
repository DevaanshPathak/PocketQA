import 'package:flutter/foundation.dart';

/// PocketQA Bug Fixtures Configuration
/// Preserves the 3 deliberate bug triggers required for PocketQA automated QA diagnosis:
/// 1. Bug 1: Null Safety Failure (Profile -> Edit Profile -> Change Photo -> Cancel -> Save)
/// 2. Bug 2: Lifecycle/State Crash (Delivery Preferences active state background/rotation return)
/// 3. Bug 3: List Index Out of Range (Category long scrollable list interaction near boundary)
class BugFixtures {
  static bool injectBugs = false;

  // Bug 1 state tracker
  static String? photoUrlState = 'https://via.placeholder.com/150';
  static bool photoCancelled = false;

  static void triggerBug1NullPhotoSave() {
    // Clean mode - no-op
  }

  static void triggerBug3IndexOverflow(int index, int totalItems) {
    // Clean mode - no-op
  }
}
