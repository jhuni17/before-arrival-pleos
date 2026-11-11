package com.beforearrival.briefing;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.function.Consumer;

/** Serialized on the UI thread. A session belongs to one SDK connection and route. */
final class NavigationRouteInput implements RouteInput {
    private final Consumer<Snapshot> onChange;
    private Snapshot current = Snapshot.unknown(Instant.EPOCH, "연결 전");
    private long generation;
    private boolean accepting;

    NavigationRouteInput(Consumer<Snapshot> onChange) {
        this.onChange = onChange;
    }

    long start(Instant now) {
        accepting = true;
        generation++;
        publish(Snapshot.unknown(now, "도착 시간 확인 중"));
        return generation;
    }

    boolean isCurrent(long token) {
        return accepting && token == generation;
    }

    // Accept normalized durations only, after validating the SDK values at the connection boundary.
    void destination(long token, Duration remaining, Instant observedAt) {
        if (!isCurrent(token)) return;
        if (remaining == null || remaining.isNegative()) {
            invalidate(token, observedAt, "유효한 도착 시간 없음");
            return;
        }
        if (observedAt.isBefore(current.observedAt)) return;
        long seconds;
        try {
            seconds = remaining.getSeconds();
        } catch (ArithmeticException error) {
            invalidate(token, observedAt, "도착 시간 범위 확인 필요");
            return;
        }
        publish(new Snapshot(OptionalLong.of(seconds), observedAt, Source.NAVI_HELPER,
                "실제 경로 · NaviHelper 3.0.5"));
    }

    void invalidate(long token, Instant now, String reason) {
        if (!isCurrent(token)) return;
        accepting = false;
        generation++;
        publish(Snapshot.unknown(now, reason));
    }

    void close(Instant now) {
        accepting = false;
        generation++;
        publish(Snapshot.unknown(now, "연결 해제"));
    }

    @Override public Snapshot snapshot() { return current; }

    private void publish(Snapshot snapshot) {
        current = snapshot;
        onChange.accept(snapshot);
    }
}
