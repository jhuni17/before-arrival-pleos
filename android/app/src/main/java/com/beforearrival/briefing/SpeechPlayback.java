package com.beforearrival.briefing;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

final class SpeechPlayback {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final Consumer<String> onStateChanged;
    private final BooleanSupplier canPlay;
    private final Runnable budgetExpired = this::stopAtBudget;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private TextToSpeech engine;
    private AudioFocusRequest focus;
    private String currentId;
    private long nextId;
    private boolean closed;
    private boolean ready;
    private String unavailableMessage = "한국어 음성을 준비하고 있어요.";

    SpeechPlayback(Context context, BooleanSupplier canPlay, Consumer<String> onStateChanged) {
        this.audio = context.getSystemService(AudioManager.class);
        this.onStateChanged = onStateChanged;
        this.canPlay = canPlay;
        engine = new TextToSpeech(context, status -> main.post(() -> initialize(status)));
    }

    private void initialize(int status) {
        if (closed) return;
        if (status != TextToSpeech.SUCCESS || !selectOfflineKoreanVoice(engine)) {
            unavailableMessage = "설치된 한국어 음성을 찾지 못했어요. 대본을 읽을 수 있어요.";
            return;
        }

        UtteranceProgressListener progress = new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                main.post(() -> { if (id.equals(currentId) && !canPlay.getAsBoolean()) stop(); });
            }
            @Override public void onDone(String id) { main.post(() -> finish(id, "")); }
            @SuppressWarnings("deprecation")
            @Override public void onError(String id) { reportError(id); }
            @Override public void onError(String id, int errorCode) { reportError(id); }
            @Override public void onStop(String id, boolean interrupted) { main.post(() -> finish(id, "")); }
        };
        ready = engine.setAudioAttributes(attributes) == TextToSpeech.SUCCESS
                && engine.setOnUtteranceProgressListener(progress) == TextToSpeech.SUCCESS;
        unavailableMessage = "한국어 음성을 준비하지 못했어요. 대본을 읽을 수 있어요.";
    }

    static boolean selectOfflineKoreanVoice(TextToSpeech engine) {
        Set<Voice> voices = engine.getVoices();
        Voice korean = voices == null ? null : voices.stream()
                .filter(voice -> Locale.KOREAN.getLanguage().equals(voice.getLocale().getLanguage()))
                .filter(voice -> !voice.isNetworkConnectionRequired())
                .filter(voice -> !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                .min(Comparator.comparing((Voice voice) -> !Locale.KOREA.equals(voice.getLocale()))
                        .thenComparing(Comparator.comparingInt(Voice::getQuality).reversed())
                        .thenComparing(Voice::getName))
                .orElse(null);
        return korean != null && engine.setVoice(korean) == TextToSpeech.SUCCESS;
    }

    boolean isPlaying() {
        return currentId != null;
    }

    void play(String narration, int budgetSeconds) {
        if (closed || !canPlay.getAsBoolean() || narration.isEmpty() || budgetSeconds <= 0) return;
        if (!ready) {
            onStateChanged.accept(unavailableMessage);
            return;
        }
        stop();
        String id = "briefing-" + ++nextId;
        AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0 && id.equals(currentId)) {
                        stop();
                        onStateChanged.accept("다른 오디오가 재생되어 브리핑을 멈췄어요.");
                    }
                }, main)
                .build();
        try {
            if (audio == null || audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                onStateChanged.accept("오디오를 사용할 수 없어요. 대본을 읽을 수 있어요.");
                return;
            }
            focus = request;
            currentId = id;
            if (!canPlay.getAsBoolean()) { stop(); return; }
            main.postDelayed(budgetExpired, budgetSeconds * 1000L);
            // Queue actual silence, rather than delaying speak() on the UI thread.
            if (engine.playSilentUtterance(200, TextToSpeech.QUEUE_FLUSH, id + "-preroll") != TextToSpeech.SUCCESS
                    || engine.speak(narration, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) {
                stop();
                onStateChanged.accept("음성 재생을 시작하지 못했어요. 대본을 읽을 수 있어요.");
                return;
            }
            onStateChanged.accept("");
        } catch (RuntimeException error) {
            stop();
            onStateChanged.accept("음성 재생을 시작하지 못했어요. 대본을 읽을 수 있어요.");
        }
    }

    void stop() {
        if (currentId == null) return;
        currentId = null;
        main.removeCallbacks(budgetExpired);
        try { engine.stop(); }
        catch (RuntimeException ignored) { /* Still release focus when the engine dies. */ }
        finally { releaseFocus(); }
        onStateChanged.accept("");
    }

    private void stopAtBudget() {
        if (currentId == null) return;
        stop();
        onStateChanged.accept("듣기 예산에 맞춰 브리핑을 멈췄어요.");
    }

    void close() {
        stop();
        closed = true;
        main.removeCallbacksAndMessages(null);
        try { engine.shutdown(); }
        catch (RuntimeException ignored) { /* Engine may already be disconnected. */ }
    }

    private void reportError(String id) {
        main.post(() -> {
            if (currentId != null && (id.equals(currentId) || id.equals(currentId + "-preroll"))) {
                finish(currentId, "음성 재생을 마치지 못했어요. 대본을 읽을 수 있어요.");
            }
        });
    }

    private void finish(String id, String message) {
        if (!id.equals(currentId)) return;
        stop();
        onStateChanged.accept(message);
    }

    private void releaseFocus() {
        AudioFocusRequest previous = focus;
        focus = null;
        try { if (previous != null) audio.abandonAudioFocusRequest(previous); }
        catch (RuntimeException ignored) { /* No held request remains in this session. */ }
    }
}
