package com.pocketqa.pocketqa;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public final class CrashReportProvider extends ContentProvider {
    static final String AUTHORITY = "com.pocketqa.pocketqa.crashes";
    static final Uri LATEST_URI = Uri.parse("content://" + AUTHORITY + "/latest");
    static final String REPORT_COLUMN = "report_json";

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!LATEST_URI.equals(uri)) throw new IllegalArgumentException("Unknown URI: " + uri);
        MatrixCursor cursor = new MatrixCursor(new String[] { REPORT_COLUMN });
        String latest = CrashReportStore.latest(requireContext());
        if (latest != null) cursor.addRow(new Object[] { latest });
        return cursor;
    }

    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
}
