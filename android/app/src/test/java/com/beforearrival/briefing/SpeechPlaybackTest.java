package com.beforearrival.briefing;

import static org.junit.Assert.*;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowTextToSpeech;

/** JVM Android doubles; these do not establish Pleos audio policy or audible Korean output. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = SpeechPlaybackTest.ManualTts.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SpeechPlaybackTest {
    @Implements(TextToSpeech.class)
    public static class ManualTts extends ShadowTextToSpeech {
        String id;
        String text;
        long silenceMs;
        int speakResult = TextToSpeech.SUCCESS;
        boolean stopped;

        @Implementation protected int setAudioAttributes(AudioAttributes attributes) { return TextToSpeech.SUCCESS; }
        @Implementation protected int playSilentUtterance(long duration, int mode, String id) {
            silenceMs = duration;
            return TextToSpeech.SUCCESS;
        }
        @Implementation protected int speak(CharSequence text, int mode, Bundle params, String id) {
            this.text = text.toString();
            this.id = id;
            stopped = false;
            return speakResult;
        }
        @Implementation protected int stop() { stopped = true; return TextToSpeech.SUCCESS; }
    }

    private SpeechPlayback playback;
    private ManualTts tts;
    private ShadowAudioManager audio;
    private AtomicBoolean allowed;
    private final List<String> messages = new ArrayList<>();

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        allowed = new AtomicBoolean(true);
        audio = Shadows.shadowOf(context.getSystemService(AudioManager.class));
        audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        playback = new SpeechPlayback(context, allowed::get, messages::add);
        tts = Shadow.extract(ShadowTextToSpeech.getLastTextToSpeechInstance());
        ShadowTextToSpeech.addVoice(new Voice("offline-ko", Locale.KOREA,
                Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, Set.of()));
        tts.getOnInitListener().onInit(TextToSpeech.SUCCESS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @After public void tearDown() { playback.close(); }

    @Test public void focusGrantedThenDoneReleasesTheSameRequest() {
        playback.play("브리핑", 12);
        assertTrue(playback.isPlaying());
        AudioFocusRequest request = audio.getLastAudioFocusRequest().audioFocusRequest;
        assertEquals(AudioAttributes.USAGE_MEDIA, request.getAudioAttributes().getUsage());
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, request.getAudioAttributes().getContentType());
        assertEquals(200, tts.silenceMs);
        tts.getUtteranceProgressListener().onDone(tts.id);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(playback.isPlaying());
        assertSame(request, audio.getLastAbandonedAudioFocusRequest());
    }

    @Test public void deniedFocusDoesNotSpeak() {
        audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        playback.play("브리핑", 12);
        assertFalse(playback.isPlaying());
        assertNull(tts.text);
    }

    @Test public void unknownOrRestrictedUseDoesNotRequestFocus() {
        allowed.set(false);
        playback.play("브리핑", 12);
        assertNull(audio.getLastAudioFocusRequest());
        assertNull(tts.text);
    }

    @Test public void focusLossStopsAndNeverRestartsOnGain() {
        playback.play("브리핑", 12);
        AudioManager.OnAudioFocusChangeListener listener = audio.getLastAudioFocusRequest().listener;
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        assertFalse(playback.isPlaying());
        assertTrue(tts.stopped);
        assertNotNull(audio.getLastAbandonedAudioFocusRequest());
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        assertFalse(playback.isPlaying());
    }

    @Test public void realTimeBudgetStopsLongSpeech() {
        playback.play("길이가 예산을 넘는 브리핑", 12);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(11_999));
        assertTrue(playback.isPlaying());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertFalse(playback.isPlaying());
        assertTrue(tts.stopped);
        assertNotNull(audio.getLastAbandonedAudioFocusRequest());
    }

    @Test public void oldTtsAndFocusCallbacksCannotStopNewPlayback() {
        playback.play("이전 브리핑", 45);
        String oldId = tts.id;
        AudioManager.OnAudioFocusChangeListener oldFocus = audio.getLastAudioFocusRequest().listener;
        playback.stop();
        playback.play("새 브리핑", 12);
        oldFocus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        tts.getUtteranceProgressListener().onDone(oldId);
        tts.getUtteranceProgressListener().onError(oldId);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(playback.isPlaying());
    }

    @Test public void speakFailureAndPrerollErrorReleaseFocus() {
        tts.speakResult = TextToSpeech.ERROR;
        playback.play("실패", 12);
        assertFalse(playback.isPlaying());
        assertNotNull(audio.getLastAbandonedAudioFocusRequest());
        tts.speakResult = TextToSpeech.SUCCESS;
        playback.play("다음 브리핑", 12);
        tts.getUtteranceProgressListener().onError(tts.id + "-preroll");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(playback.isPlaying());
    }
}
