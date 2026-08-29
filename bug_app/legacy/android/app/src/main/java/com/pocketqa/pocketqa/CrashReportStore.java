package com.pocketqa.pocketqa;

import android.content.Context;

final class CrashReportStore {
    private static final String PREFERENCES = "pocketqa_crash_reports";
    private static final String LATEST_REPORT = "latest_report";

    private CrashReportStore() {}

    static void write(Context context, String reportJson) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(LATEST_REPORT, reportJson)
                .apply();
        context.getContentResolver().notifyChange(CrashReportProvider.LATEST_URI, null);
    }

    static String latest(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(LATEST_REPORT, null);
    }
}
