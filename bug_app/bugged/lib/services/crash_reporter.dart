import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Forwards Flutter failures to PocketQA's local Android crash provider.
///
/// The native side persists the JSON before exposing it through the
/// signature-protected ContentProvider. No network transport is involved.
class CrashReporter {
  CrashReporter._();

  static const _channel = MethodChannel('com.quickcart.buggyapp/crash_reports');

  static void install() {
    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      report(details.exception, details.stack ?? StackTrace.empty);
    };
    PlatformDispatcher.instance.onError = (error, stackTrace) {
      report(error, stackTrace);
      // The demo keeps running long enough for PocketQA to read the local
      // report. The error remains visible in the Flutter console.
      return true;
    };
  }

  static Future<void> report(
    Object error,
    StackTrace stackTrace, {
    String? triggerHint,
  }) async {
    final reportJson = jsonEncode(
      buildReport(
        error: error,
        stackTrace: stackTrace,
        triggerHint: triggerHint,
      ),
    );
    try {
      await _channel.invokeMethod<void>('reportCrash', {
        'reportJson': reportJson,
      });
    } on PlatformException catch (exception) {
      debugPrint('PocketQA crash report failed: ${exception.code}');
    }
  }

  static Map<String, Object?> buildReport({
    required Object error,
    required StackTrace stackTrace,
    DateTime? now,
    String? id,
    String? triggerHint,
  }) {
    final capturedAt = now ?? DateTime.now();
    final frames = _dartFrames(stackTrace);
    return {
      'schemaVersion': 1,
      'id': id ?? '${capturedAt.microsecondsSinceEpoch}-${error.runtimeType}',
      'capturedAtMs': capturedAt.millisecondsSinceEpoch,
      'appPackage': 'com.quickcart.buggyapp',
      'fatal': true,
      'exceptionType': error.runtimeType.toString(),
      'message': error.toString(),
      'frames': frames,
      'triggerHint': ?triggerHint,
    };
  }

  static List<Map<String, Object>> _dartFrames(StackTrace stackTrace) {
    final framePattern = RegExp(
      r'^#\d+\s+(.+?)\s+\(package:pocketqa/(.+?):(\d+):\d+\)$',
    );
    return stackTrace
        .toString()
        .split('\n')
        .map(framePattern.firstMatch)
        .whereType<RegExpMatch>()
        .map(
          (match) => <String, Object>{
            'sourceKey': 'lib/${match.group(2)}',
            'line': int.parse(match.group(3)!),
            'function': match.group(1)!,
          },
        )
        .toList(growable: false);
  }
}
