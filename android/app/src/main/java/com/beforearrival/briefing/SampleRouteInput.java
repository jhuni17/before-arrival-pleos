package com.beforearrival.briefing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.OptionalInt;

final class SampleRouteInput implements RouteInput {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DEMO_DAY = LocalDate.of(2026, 11, 5);
    private int demoMinutes = 18;

    static Instant demoTime(int hour, int minute) {
        return DEMO_DAY.atTime(hour, minute).atZone(SEOUL).toInstant();
    }

    void select(int minutes) {
        if (minutes != 18 && minutes != 4) {
            throw new IllegalArgumentException("The demo offers 18 and 4 minutes");
        }
        demoMinutes = minutes;
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(OptionalInt.of(demoMinutes), demoTime(8, demoMinutes == 18 ? 40 : 54),
                "경로 시뮬레이션");
    }
}
