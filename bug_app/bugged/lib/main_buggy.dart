import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';

import 'firebase_options.dart';
import 'app.dart';
import 'config/app_config.dart';
import 'services/crash_reporter.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  CrashReporter.install();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);

  try {
    await FirebaseAuth.instance.signInAnonymously();
  } catch (e) {
    debugPrint("Auth Error: $e");
  }

  runApp(
    QuickCommerceApp(
      config: const AppConfig(
        injectBugs: true,
        appTitle: "PocketQA Testbed (Buggy)",
        child: SizedBox.shrink(), // Dummy child for initial creation
      ),
    ),
  );
}
