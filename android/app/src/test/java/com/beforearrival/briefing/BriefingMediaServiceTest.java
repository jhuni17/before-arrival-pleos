package com.beforearrival.briefing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Looper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Real service decisions with fake speech/vehicle callbacks; no Pleos acoustic evidence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BriefingMediaServiceTest {
    private BriefingMediaService service;
    private final List<FakeSpeech> voices = new ArrayList<>();
    private String message;
    private final Object warmScreen = new Object();
    private static final class FakeSpeech implements ComparisonSpeech {
        final Runnable started, done, failed;
        Runnable stopped;
        String text;
        boolean released;
        FakeSpeech(Runnable s, Runnable d, Runnable f) { started = s; done = d; failed = f; }
        public void start(String value) { text = value; }
        public void stop(Runnable completion) { stopped = completion; }
        public void release() { released = true; }
        void acknowledge() { released = true; Runnable callback = stopped; stopped = null; callback.run(); }
    }
    @Before public void setup() throws Exception {
        assumeTrue(ComparisonFactory.productionBackend());
        assumeTrue("off".equals(BuildConfig.TTS_COMPARISON));
        service = Robolectric.buildService(BriefingMediaService.class).create().get();
        service.speechFactory = (context, start, done, fail) -> {
            FakeSpeech voice = new FakeSpeech(start, done, fail); voices.add(voice); return voice;
        };
        Shadows.shadowOf(service.getSystemService(AudioManager.class))
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        BriefingMediaService.observe(value -> { if (!value.isEmpty()) message = value; });
        startPlan();
    }
    private void startPlan() throws Exception {
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) items.add(new ContentCatalog.Item(new JSONObject()
                .put("id", "ordinary-" + i).put("sourceId", "sample-schedule")
                .put("title", "실제 편성 " + i).put("summary", "편성 대본 " + i).put("estimatedSeconds", 8)));
        BriefingMediaService.play(service, new ContentCatalog.Plan(45, 24, items, new ArrayList<>()),
                new SampleRouteInput().snapshot());
        service.onStartCommand(new Intent(), 0, 1);
        CarRestrictions restrictions = ReflectionHelpers.getField(service, "restrictions");
        Consumer<AccessPolicy.State> callback = ReflectionHelpers.getField(restrictions, "onChange");
        callback.accept(AccessPolicy.State.PARKED_USE_ALLOWED);
    }
    @After public void cleanup() {
        PleosSpeechWarmup.eligible(service, warmScreen, false);
        PleosSpeechWarmup.factory = ComparisonFactory::create;
        BriefingMediaService.observe(null);
        if (service != null) service.onDestroy();
        BriefingMediaService.stop();
    }
    @Test public void normalPlayTakesSilentPreparationWithoutSecondConstruction() throws Exception {
        BriefingMediaService.stop(); voices.clear();
        PleosSpeechWarmup.factory = (context, start, done, fail) -> {
            FakeSpeech voice = new FakeSpeech(start, done, fail); voices.add(voice); return voice;
        };
        PleosSpeechWarmup.eligible(service, warmScreen, true);
        assertEquals(1, voices.size()); assertNull(voices.get(0).text);
        startPlan();
        assertEquals(1, voices.size()); assertEquals("편성 대본 0", voices.get(0).text);
        PleosSpeechWarmup.eligible(service, warmScreen, false);
        assertFalse(voices.get(0).released);
        voices.get(0).started.run(); voices.get(0).done.run();
        assertTrue(voices.get(0).released);
        assertEquals(2, voices.size()); // Deliberately fresh engine at uncorrelated callback boundary.
    }
    @Test public void unavailablePreparationUsesOrdinarySameBackendStartup() throws Exception {
        BriefingMediaService.stop(); voices.clear();
        PleosSpeechWarmup.factory = (context, start, done, fail) -> { throw new IllegalStateException(); };
        PleosSpeechWarmup.eligible(service, warmScreen, true);
        startPlan();
        assertEquals(1, voices.size()); assertEquals("편성 대본 0", voices.get(0).text);
    }
    @Test public void defaultDispatchUsesOrdinaryPlanAndCompletesWithoutFixture() {
        assertEquals(1, voices.size());
        assertEquals("편성 대본 0", voices.get(0).text);
        for (int i = 0; i < 3; i++) { voices.get(i).started.run(); voices.get(i).done.run(); }
        assertEquals(3, voices.size());
        assertFalse(BriefingMediaService.isActive());
    }
    @Test public void rapidSkipWaitsForStopAndIgnoresOldCallbacks() {
        FakeSpeech old = voices.get(0);
        service.nextTopic(); service.nextTopic();
        assertEquals(1, voices.size());
        old.done.run(); old.started.run();
        assertEquals(1, voices.size());
        old.acknowledge();
        assertEquals("편성 대본 2", voices.get(1).text);
        old.failed.run(); old.done.run();
        assertTrue(BriefingMediaService.isActive());
        assertEquals(2, voices.size());
    }
    @Test public void pauseResumeWaitsForStopAndRestartsCurrentTopic() {
        assertTrue(service.pauseBriefing());
        assertTrue(service.resumeBriefing());
        assertEquals(1, voices.size());
        voices.get(0).acknowledge();
        assertEquals(2, voices.size());
        assertEquals("편성 대본 0", voices.get(1).text);
    }
    @Test public void errorIsReadableAndNeverDispatchesFallback() {
        voices.get(0).failed.run();
        assertTrue(message.contains("Pleos 음성을 사용할 수 없어요"));
        assertFalse(BriefingMediaService.isActive());
        assertEquals(1, voices.size());
    }
    @Test public void unknownAndLateCallbacksCannotResurrectQueue() {
        CarRestrictions restrictions = ReflectionHelpers.getField(service, "restrictions");
        Consumer<AccessPolicy.State> callback = ReflectionHelpers.getField(restrictions, "onChange");
        callback.accept(AccessPolicy.State.UNKNOWN);
        FakeSpeech old = voices.get(0);
        old.started.run(); old.done.run(); old.failed.run(); old.acknowledge();
        assertFalse(service.resumeBriefing());
        assertFalse(BriefingMediaService.isActive());
        assertEquals(1, voices.size());
    }
    @Test public void focusLossWaitsForAcknowledgementAndPermanentLossStops() {
        AudioManager.OnAudioFocusChangeListener listener = Shadows.shadowOf(
                service.getSystemService(AudioManager.class)).getLastAudioFocusRequest().listener;
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        assertEquals(1, voices.size());
        voices.get(0).acknowledge();
        assertEquals(2, voices.size());
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        voices.get(1).done.run();
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        assertFalse(BriefingMediaService.isActive());
        assertEquals(2, voices.size());
    }
    @Test public void deniedFocusOnResumeDoesNotStartAnotherStream() {
        service.pauseBriefing();
        voices.get(0).acknowledge();
        Shadows.shadowOf(service.getSystemService(AudioManager.class))
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        service.resumeBriefing();
        assertEquals(1, voices.size());
        assertFalse(BriefingMediaService.isActive());
    }
    @Test public void deadlineAndShutdownRejectLateCompletion() {
        FakeSpeech old = voices.get(0);
        old.started.run();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46));
        old.done.run(); old.started.run();
        assertFalse(BriefingMediaService.isActive());
        assertEquals(1, voices.size());
    }

    @Test public void routeCancellationAndDeadlineStopPlayback() {
        ReflectionHelpers.callInstanceMethod(service, "onLiveRoute", ReflectionHelpers.ClassParameter.from(
                RouteInput.Snapshot.class, RouteInput.Snapshot.unknown(Instant.now(), "테스트 취소")));
        voices.get(0).done.run();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46));
        assertFalse(BriefingMediaService.isActive());
        assertEquals(1, voices.size());
    }
}
