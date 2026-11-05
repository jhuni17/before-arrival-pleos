package com.beforearrival.briefing;

import java.time.Instant;
import java.util.OptionalLong;

interface RouteInput {
    Snapshot snapshot();

    enum Source { SAMPLE, NAVI_HELPER }

    final class Snapshot {
        final OptionalLong remainingSeconds;
        final Instant observedAt;
        final Source source;
        final String sourceLabel;

        Snapshot(OptionalLong seconds, Instant observedAt, Source source, String label) {
            if (seconds.isPresent() && seconds.getAsLong() < 0) {
                throw new IllegalArgumentException("Remaining duration cannot be negative");
            }
            this.remainingSeconds = seconds;
            this.observedAt = observedAt;
            this.source = source;
            this.sourceLabel = label;
        }

        static Snapshot unknown(Instant now, String reason) {
            return new Snapshot(OptionalLong.empty(), now, Source.NAVI_HELPER, "실제 경로 · " + reason);
        }
    }
}
