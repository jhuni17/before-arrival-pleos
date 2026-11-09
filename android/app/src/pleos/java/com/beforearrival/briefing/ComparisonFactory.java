package com.beforearrival.briefing;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;
import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.constant.Mode;
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.listener.OnInitializeListener;

final class ComparisonFactory {
    static boolean productionBackend() { return true; }
    static ComparisonSpeech create(Context context, Runnable started, Runnable done, Runnable failed) {
        return new Candidate(context, started, done, failed);
    }
    private static final class Candidate implements ComparisonSpeech {
        final Handler main = new Handler(Looper.getMainLooper());
        final TextToSpeech sdk;
        final Runnable started, done, failed;
        boolean initialized, sent, closed, stopping, listenerRegistered;
        String text;
        Runnable stopped;
        final EventListener events = new EventListener() {
            public void onReady() { main.post(() -> {
                if (closed || stopping) return;
                if (BuildConfig.DEBUG && !"off".equals(BuildConfig.TTS_COMPARISON)) android.util.Log.d("TtsComparison", "elapsedRealtimeMs="
                        + android.os.SystemClock.elapsedRealtime() + " engine=candidate event=onReady");
            }); }
            public void onStart() { main.post(() -> { if (!closed && !stopping) started.run(); }); }
            public void onDone() { main.post(() -> { if (!closed && !stopping && sent) { sent = false; done.run(); } }); }
            public void onStop() { main.post(() -> { if (!closed && stopping && stopped != null) {
                Runnable next = stopped; stopped = null; release(); next.run();
            } else if (!closed && !stopping) failed.run(); }); }
            public void onError(ErrorType error) { main.post(() -> {
                if (!closed) { diagnostic("onError", error); failed.run(); }
            }); }
            public void onUpdatedRms(double rms) {}
        };
        Candidate(Context context, Runnable started, Runnable done, Runnable failed) {
            this.started = started; this.done = done; this.failed = failed;
            try { sdk = new TextToSpeech(context, Mode.ON_DEVICE); }
            catch (RuntimeException error) { diagnostic("construct", error.getClass().getName()); throw error; }
            try {
                sdk.initialize(new OnInitializeListener() {
                    public void onSuccess() { main.post(() -> {
                        if (closed || stopping) return;
                        try {
                            sdk.setLocale(Locale.KOREA);
                            sdk.setSpeakerType("seoyoon");
                            sdk.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                            if (BuildConfig.DEBUG && !"off".equals(BuildConfig.TTS_COMPARISON)) android.util.Log.d("TtsComparison", "elapsedRealtimeMs=" + android.os.SystemClock.elapsedRealtime()
                                + " engine=candidate initialized=ON_DEVICE voice=seoyoon");
                            initialized = true;
                            submit();
                        } catch (RuntimeException unavailable) {
                            diagnostic("configure", unavailable.getClass().getName()); failed.run();
                        }
                    }); }
                    public void onFailure(ErrorType error) { main.post(() -> {
                        if (!closed) { diagnostic("initialize", error); failed.run(); }
                    }); }
                });
                // initialize creates the SDK's listener storage. Register only after it returns;
                // Configuration and listener registration gate the first request. onReady is
                // diagnostic only: waiting for it here can block the first speak indefinitely.
                sdk.addEventListener(events);
                listenerRegistered = true;
                submit();
            } catch (RuntimeException unavailable) {
                diagnostic("initialize_or_register", unavailable.getClass().getName());
                try { release(); } catch (RuntimeException cleanup) {
                    diagnostic("release", cleanup.getClass().getName());
                }
                throw unavailable;
            }
        }
        static void diagnostic(String stage, Object detail) {
            if (BuildConfig.DEBUG && !"off".equals(BuildConfig.TTS_COMPARISON)) android.util.Log.d("TtsComparison", "elapsedRealtimeMs="
                    + android.os.SystemClock.elapsedRealtime() + " engine=candidate stage=" + stage
                    + " error=" + detail);
        }
        public void start(String value) { text = value; submit(); }
        void submit() {
            if (!closed && !stopping && initialized && listenerRegistered && !sent && text != null) {
                sent = true;
                if (BuildConfig.DEBUG && !"off".equals(BuildConfig.TTS_COMPARISON)) android.util.Log.d("TtsComparison", "elapsedRealtimeMs=" + android.os.SystemClock.elapsedRealtime()
                        + " engine=candidate sdk_speak_request isFinal=true");
                try { sdk.speak(text, true); } catch (RuntimeException unavailable) {
                    diagnostic("speak", unavailable.getClass().getName()); failed.run();
                }
            }
        }
        public void stop(Runnable completion) {
            if (closed) { completion.run(); return; }
            stopping = true; stopped = completion;
            if (!sent) { release(); completion.run(); return; }
            try { sdk.stop(); } catch (RuntimeException unavailable) {
                diagnostic("stop", unavailable.getClass().getName()); throw unavailable;
            }
            // No stop completion means no next topic: fail closed, never guess a quiet gap.
            main.postDelayed(() -> { if (!closed) failed.run(); }, 5000);
        }
        public void release() {
            if (closed) return;
            closed = true;
            main.removeCallbacksAndMessages(null);
            try {
                if (listenerRegistered) {
                    listenerRegistered = false;
                    sdk.removeEventListener(events);
                }
            } catch (RuntimeException unavailable) {
                diagnostic("remove_listener", unavailable.getClass().getName());
            } finally {
                try { sdk.release(); } catch (RuntimeException unavailable) {
                    diagnostic("release", unavailable.getClass().getName());
                }
            }
        }
    }
}
