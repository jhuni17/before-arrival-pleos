package com.beforearrival.briefing;

import java.time.Duration;

/** A debug fixture for the observed Pleos Connect 2.1.0 image and NaviHelper 3.0.5. */
final class NaviTimeContract {
    static final String VERIFIED_IMAGE =
            "Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys";
    private static final int MAX_COMMUTE_SECONDS = 2 * 60 * 60;

    static Duration decode(Integer rawRemainTime, String fingerprint, boolean debug) {
        if (!debug || !VERIFIED_IMAGE.equals(fingerprint)) {
            throw new IllegalStateException("이 환경의 도착 시간 형식을 확인하지 못했어요");
        }
        // App acceptance range for this fixture. Zero was observed after cancelling guidance.
        if (rawRemainTime == null || rawRemainTime <= 0 || rawRemainTime > MAX_COMMUTE_SECONDS) {
            throw new IllegalArgumentException("유효한 도착 시간 없음");
        }
        return Duration.ofSeconds(rawRemainTime);
    }
    private NaviTimeContract() {}
}
