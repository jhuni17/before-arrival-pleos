package com.beforearrival.briefing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Duration;

import org.junit.Test;

public final class NaviTimeContractTest {
    @Test public void observedPleosImageDecodesCapturedPositiveSeconds() {
        assertEquals(Duration.ofMinutes(28), NaviTimeContract.decode(1680, NaviTimeContract.VERIFIED_IMAGE, true));
        assertEquals(Duration.ofMinutes(32), NaviTimeContract.decode(1920, NaviTimeContract.VERIFIED_IMAGE, true));
    }

    @Test public void releaseOrUnknownImagesDoNotEnableAnEmulatorFixture() {
        assertThrows(IllegalStateException.class, () -> NaviTimeContract.decode(1680, NaviTimeContract.VERIFIED_IMAGE, false));
        assertThrows(IllegalStateException.class, () -> NaviTimeContract.decode(1680, "other-image", true));
    }

    @Test public void cancelledMissingAndOutOfRangeValuesCannotScheduleAudio() {
        for (Integer value : new Integer[] {0, null, -1, 7201}) {
            assertThrows(IllegalArgumentException.class,
                    () -> NaviTimeContract.decode(value, NaviTimeContract.VERIFIED_IMAGE, true));
        }
    }
}
