package com.beforearrival.briefing;
import android.content.Context;
final class ComparisonFactory {
    static boolean productionBackend() { return false; }
    static ComparisonSpeech create(Context context, Runnable started, Runnable done, Runnable failed) {
        throw new UnsupportedOperationException("Pleos comparison only");
    }
}
