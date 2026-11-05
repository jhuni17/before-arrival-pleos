package com.beforearrival.briefing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.OptionalLong;

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
        return new Snapshot(OptionalLong.of(demoMinutes * 60L), demoTime(8, demoMinutes == 18 ? 40 : 54),
                Source.SAMPLE, "샘플 경로 · 예시 시각");
    }
}
