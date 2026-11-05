package com.beforearrival.briefing;

import java.time.Instant;
import java.util.OptionalInt;

interface RouteInput {
    Snapshot snapshot();

    final class Snapshot {
        final OptionalInt remainingMinutes;
        final Instant observedAt;
        final String sourceLabel;

        Snapshot(OptionalInt remainingMinutes, Instant observedAt, String sourceLabel) {
            this.remainingMinutes = remainingMinutes;
            this.observedAt = observedAt;
            this.sourceLabel = sourceLabel;
        }
    }
}
