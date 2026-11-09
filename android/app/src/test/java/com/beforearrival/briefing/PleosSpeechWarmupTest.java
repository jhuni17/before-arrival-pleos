package com.beforearrival.briefing;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Looper;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Ownership tests with doubles; not SDK initialization or audible evidence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PleosSpeechWarmupTest {
    private final Object screen = new Object();
    private final Context context = RuntimeEnvironment.getApplication();
    private Fake engine;
    private int constructions;
    private PleosSpeechWarmup.Lease claimed;
    private static final class Fake implements ComparisonSpeech {
        final Runnable failed;
        int speaks, releases;
        Fake(Runnable failure) { failed = failure; }
        public void start(String text) { speaks++; }
        public void stop(Runnable completion) { completion.run(); }
        public void release() { releases++; }
    }
    @Before public void setup() {
        BriefingMediaService.stop();
        PleosSpeechWarmup.factory = (c, started, done, failed) -> {
            constructions++; engine = new Fake(failed); return engine;
        };
    }
    @After public void cleanup() {
        PleosSpeechWarmup.eligible(context, screen, false);
        if (claimed != null) claimed.release();
        PleosSpeechWarmup.factory = ComparisonFactory::create;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
    }
    private void prepare() { PleosSpeechWarmup.eligible(context, screen, true); }
    @Test public void prewarmIsSilentBoundedAndTransfersExactlyOnce() {
        prepare(); prepare();
        assertEquals(1, constructions); assertEquals(0, engine.speaks);
        claimed = PleosSpeechWarmup.take();
        assertNotNull(claimed); assertNull(PleosSpeechWarmup.take());
        claimed.bind(() -> {}, () -> {}, () -> fail("unexpected failure"));
        PleosSpeechWarmup.eligible(context, screen, false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
        assertEquals(0, engine.releases);
        claimed.start("normal first topic");
        assertEquals(1, constructions); assertEquals(1, engine.speaks);
        claimed.release(); claimed.release(); assertEquals(1, engine.releases);
    }
    @Test public void idleExpiryLeavesColdStartupAndDoesNotRetryUntilNextLifetime() {
        prepare();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15));
        assertEquals(1, engine.releases); assertNull(PleosSpeechWarmup.take());
        prepare(); assertEquals(1, constructions);
        PleosSpeechWarmup.eligible(context, screen, false); prepare();
        assertEquals(2, constructions);
    }
    @Test public void abandonedTransferAlsoExpiresBeforeServiceBinds() {
        prepare(); claimed = PleosSpeechWarmup.take();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15));
        assertEquals(1, engine.releases);
        try { claimed.bind(() -> {}, () -> {}, () -> {}); fail("expired lease"); }
        catch (IllegalStateException expected) { /* service uses ordinary SDK construction */ }
    }
    @Test public void lifecycleOrEligibilityLossReleasesIdleWithoutSpeech() {
        prepare(); PleosSpeechWarmup.eligible(context, screen, false);
        assertEquals(1, engine.releases); assertEquals(0, engine.speaks);
        assertNull(PleosSpeechWarmup.take());
    }
    @Test public void asyncWarmFailureLeavesNoLease() {
        prepare(); engine.failed.run();
        assertNull(PleosSpeechWarmup.take()); assertEquals(1, engine.releases);
    }
    @Test public void synchronousFailureAndConstructionFailureLeaveColdPath() {
        PleosSpeechWarmup.factory = (c, s, d, f) -> {
            Fake value = new Fake(f); engine = value; f.run(); return value;
        };
        prepare(); assertNull(PleosSpeechWarmup.take()); assertEquals(1, engine.releases);
        PleosSpeechWarmup.eligible(context, screen, false);
        PleosSpeechWarmup.factory = (c, s, d, f) -> { throw new IllegalStateException(); };
        prepare(); assertNull(PleosSpeechWarmup.take());
    }
}
