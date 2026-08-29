import 'package:flutter/foundation.dart';

/// PocketQA Bug Fixtures Configuration
/// Preserves the 3 deliberate bug triggers required for PocketQA automated QA diagnosis:
/// 1. Bug 1: Null Safety Failure (Profile -> Edit Profile -> Change Photo -> Cancel -> Save)
/// 2. Bug 2: Lifecycle/State Crash (Delivery Preferences active state background/rotation return)
/// 3. Bug 3: List Index Out of Range (Category long scrollable list interaction near boundary)
class BugFixtures {
  static bool injectBugs = true;

  // Bug 1 state tracker
  static String? photoUrlState = 'https://via.placeholder.com/150';
  static bool photoCancelled = false;

  static void triggerBug1NullPhotoSave() {
    if (injectBugs && photoCancelled) {
      // Force null access to trigger Null-Safety Crash for PocketQA diagnosis
      final String photoPath = photoUrlState!; // Will throw TypeError / Null check operator on null if photoUrlState is null
      debugPrint('Photo saved: $photoPath');
    }
  }

  static void triggerBug3IndexOverflow(int index, int totalItems) {
    if (injectBugs && index >= totalItems - 1) {
      // Intentionally access index + 5 to trigger RangeError (Index out of bounds)
      final List<dynamic> dummyList = List.generate(totalItems, (i) => i);
      final _ = dummyList[index + 5];
    }
  }
}
