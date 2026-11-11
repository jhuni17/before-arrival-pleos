package com.beforearrival.briefing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Duration;

import org.junit.Test;

public final class NaviTimeContractTest {
    @Test public void observedPleosImageDecodesCapturedPositiveSeconds() {
        assertEquals(Duration.ofMinutes(46), NaviTimeContract.decode(2760, NaviTimeContract.VERIFIED_IMAGE));
        assertEquals(Duration.ofMinutes(5), NaviTimeContract.decode(300, NaviTimeContract.VERIFIED_IMAGE));
    }

    @Test public void unknownImagesDoNotEnableEmulatorTimeDecoding() {
        assertThrows(IllegalStateException.class, () -> NaviTimeContract.decode(300, "other-image"));
    }

    @Test public void cancelledMissingAndOutOfRangeValuesCannotScheduleAudio() {
        for (Integer value : new Integer[] {0, null, -1, 7201}) {
            assertThrows(IllegalArgumentException.class,
                    () -> NaviTimeContract.decode(value, NaviTimeContract.VERIFIED_IMAGE));
        }
    }
}
