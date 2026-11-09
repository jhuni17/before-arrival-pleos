package com.beforearrival.briefing;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Main-thread-only, bounded parked-screen lease. No speak, focus or service here. */
final class PleosSpeechWarmup {
    interface Factory {
        ComparisonSpeech create(Context context, Runnable started, Runnable done, Runnable failed);
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Object owner;
    private static Lease idle;
    private static boolean attempted;
    static Factory factory = ComparisonFactory::create;

    static void eligible(Context context, Object screen, boolean allowed) {
        if (!allowed) {
            if (owner == screen) { discard(); owner = null; attempted = false; }
            return;
        }
        if (owner != screen) { discard(); owner = screen; attempted = false; }
        if (attempted || BriefingMediaService.isActive()) return;
        attempted = true;
        Lease lease = new Lease();
        idle = lease;
        try {
            ComparisonSpeech engine = factory.create(context.getApplicationContext(),
                    lease::started, lease::done, lease::failed);
            lease.engine = engine;
            if (lease.closed) engine.release();
            MAIN.postDelayed(() -> {
                // A claimed but never bound service request is still abandoned preparation.
                if (!lease.bound) lease.release();
                if (idle == lease) idle = null;
            }, 15000);
        } catch (RuntimeException unavailable) { discard(); }
    }

    static Lease take() {
        Lease lease = idle;
        idle = null;
        if (lease == null || lease.closed) return null;
        return lease;
    }

    private static void discard() {
        Lease previous = idle;
        idle = null;
        if (previous != null) previous.release();
    }

    static final class Lease implements ComparisonSpeech {
        private ComparisonSpeech engine;
        private Runnable onStart, onDone, onFailure;
        private boolean closed, bound;
        void bind(Runnable started, Runnable done, Runnable failed) {
            if (closed || bound) throw new IllegalStateException("lease unavailable");
            bound = true; onStart = started; onDone = done; onFailure = failed;
        }
        private void started() { if (!closed && bound) onStart.run(); }
        private void done() { if (!closed && bound) onDone.run(); }
        private void failed() {
            if (closed) return;
            Runnable failure = onFailure;
            release();
            if (failure != null) failure.run();
        }
        public void start(String text) {
            if (closed || !bound) throw new IllegalStateException("lease unavailable");
            engine.start(text);
        }
        public void stop(Runnable completion) {
            if (closed) { completion.run(); return; }
            engine.stop(() -> { release(); completion.run(); });
        }
        public void release() {
            if (closed) return;
            closed = true;
            if (engine != null) engine.release();
            onStart = null; onDone = null; onFailure = null;
        }
    }
    private PleosSpeechWarmup() {}
}
