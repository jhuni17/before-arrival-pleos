package com.beforearrival.briefing;

import java.time.Duration;

/**
 * The accessible 3.0.5 reference exposes remainTime's name but no unit, sentinel,
 * or range. Do not guess seconds/minutes, or copy the unrelated battery API unit.
 * Replace this guard only with a cited, versioned SDK contract and fixture.
 */
final class NaviTimeContract {
    static Duration decode(Object rawRemainTime) {
        throw new IllegalStateException("NaviHelper 3.0.5 remainTime 단위·유효 범위 확인 필요");
    }
    private NaviTimeContract() {}
}
