import 'package:flutter_test/flutter_test.dart';
import 'package:pocketqa/services/crash_reporter.dart';

void main() {
  test('creates a version one report with a local Dart source key', () {
    final report = CrashReporter.buildReport(
      error: RangeError.index(3, const ['a']),
      stackTrace: StackTrace.fromString(
        '#0 CatalogScreen.build (package:pocketqa/ui/screens/catalog_screen.dart:84:9)',
      ),
      now: DateTime.fromMillisecondsSinceEpoch(1787970000000),
      id: 'report-42',
    );

    expect(report['schemaVersion'], 1);
    expect(report['id'], 'report-42');
    expect(report['appPackage'], 'com.pocketqa.pocketqa');
    expect(report['frames'], [
      {
        'sourceKey': 'lib/ui/screens/catalog_screen.dart',
        'line': 84,
        'function': 'CatalogScreen.build',
      },
    ]);
  });
}
