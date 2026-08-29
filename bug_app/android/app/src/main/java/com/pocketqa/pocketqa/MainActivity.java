package com.pocketqa.pocketqa;

import androidx.annotation.NonNull;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    private static final String CRASH_REPORT_CHANNEL = "com.pocketqa.pocketqa/crash_reports";

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CRASH_REPORT_CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if (!"reportCrash".equals(call.method)) {
                        result.notImplemented();
                        return;
                    }
                    String reportJson = call.argument("reportJson");
                    if (reportJson == null || reportJson.isBlank()) {
                        result.error("INVALID_REPORT", "reportJson is required", null);
                        return;
                    }
                    CrashReportStore.write(getApplicationContext(), reportJson);
                    result.success(null);
                });
    }
}
