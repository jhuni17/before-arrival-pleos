package com.beforearrival.briefing;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

final class PleosVerificationTrace {
    private static final String TAG = "PleosValidation";

    static void record(Context context, String event) {
        if (Log.isLoggable(TAG, Log.DEBUG)
                && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && "com.beforearrival.briefing".equals(context.getPackageName())) {
            Log.d(TAG, event);
        }
    }

    private PleosVerificationTrace() {}
}
