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
    static ComparisonSpeech create(Context context, Runnable started, Runnable done, Runnable failed) {
        return new Candidate(context, started, done, failed);
    }
    private static final class Candidate implements ComparisonSpeech {
        final Handler main = new Handler(Looper.getMainLooper());
        final TextToSpeech sdk;
        final Runnable started, done, failed;
        boolean initialized, ready, sent, closed, stopping;
        String text;
        Runnable stopped;
        final EventListener events = new EventListener() {
            public void onReady() { main.post(() -> { ready = true; submit(); }); }
            public void onStart() { main.post(() -> { if (!closed && !stopping) started.run(); }); }
            public void onDone() { main.post(() -> { if (!closed && !stopping) done.run(); }); }
            public void onStop() { main.post(() -> { if (!closed && stopping && stopped != null) {
                Runnable next = stopped; stopped = null; release(); next.run();
            } }); }
            public void onError(ErrorType error) { main.post(() -> { if (!closed) failed.run(); }); }
            public void onUpdatedRms(double rms) {}
        };
        Candidate(Context context, Runnable started, Runnable done, Runnable failed) {
            this.started = started; this.done = done; this.failed = failed;
            sdk = new TextToSpeech(context, Mode.ON_DEVICE);
            sdk.addEventListener(events);
            sdk.initialize(new OnInitializeListener() {
                public void onSuccess() { main.post(() -> {
                    if (closed || stopping) return;
                    try {
                    initialized = true;
                    android.util.Log.d("TtsComparison", "elapsedRealtimeMs=" + android.os.SystemClock.elapsedRealtime()
                        + " engine=candidate initialized=ON_DEVICE voice=SDK_default_identity_unverified");
                    sdk.setLocale(Locale.KOREA);
                    sdk.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    submit();
                    } catch (RuntimeException unavailable) { failed.run(); }
                }); }
                public void onFailure(ErrorType error) { main.post(() -> { if (!closed) failed.run(); }); }
            });
        }
        public void start(String value) { text = value; submit(); }
        void submit() {
            if (!closed && !stopping && initialized && ready && !sent && text != null) {
                sent = true;
                android.util.Log.d("TtsComparison", "elapsedRealtimeMs=" + android.os.SystemClock.elapsedRealtime()
                        + " engine=candidate sdk_speak_request isFinal=true");
                try { sdk.speak(text, true); } catch (RuntimeException unavailable) { failed.run(); }
            }
        }
        public void stop(Runnable completion) {
            if (closed) { completion.run(); return; }
            stopping = true; stopped = completion;
            if (!sent) { release(); completion.run(); return; }
            sdk.stop();
            // No stop completion means no next topic: fail closed, never guess a quiet gap.
            main.postDelayed(() -> { if (!closed) failed.run(); }, 5000);
        }
        public void release() {
            if (closed) return;
            closed = true;
            main.removeCallbacksAndMessages(null);
            try { sdk.removeEventListener(events); }
            finally { sdk.release(); }
        }
    }
}
