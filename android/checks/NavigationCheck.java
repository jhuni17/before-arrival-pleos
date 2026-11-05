package com.beforearrival.briefing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.beforearrival.briefing.Briefing.Item;
import com.beforearrival.briefing.AccessPolicy.State;

public final class NavigationCheck {
    public static void main(String[] args) {
        List<Briefing.Plan> changes = new ArrayList<>();
        NavigationRouteInput live = new NavigationRouteInput(snapshot ->
                changes.add(Briefing.plan(snapshot, EnumSet.allOf(Item.class), Item.GAME)));
        SampleRouteInput sample = new SampleRouteInput();
        Instant longTrip = SampleRouteInput.demoTime(8, 40);
        Instant shortTrip = SampleRouteInput.demoTime(8, 54);
        check(live.snapshot().remainingSeconds.isEmpty(), "live input starts empty");
        long first = live.start(longTrip);
        live.destination(first, Duration.ofMinutes(18), longTrip);
        check(latest(changes).selected.get(0) == Item.GAME, "normalized live duration refreshes long plan");
        live.destination(first, Duration.ofMinutes(4), shortTrip);
        check(latest(changes).budgetSeconds == 12, "normalized live duration refreshes budget");
        check(latest(changes).selected.equals(List.of(Item.SCHEDULE)), "normalized live clock changes urgency");
        check(live.snapshot().source == RouteInput.Source.NAVI_HELPER, "live provenance");
        check(sample.snapshot().remainingSeconds.getAsLong() == 1080, "live update never changes sample");
        live.destination(first, Duration.ofMinutes(18), longTrip);
        check(live.snapshot().remainingSeconds.getAsLong() == 240, "old observation ignored");
        live.invalidate(first, shortTrip, "경로 취소");
        int count = changes.size();
        live.destination(first, Duration.ofMinutes(18), shortTrip.plusSeconds(1));
        check(changes.size() == count && live.snapshot().remainingSeconds.isEmpty(), "late callback after cancellation");
        long second = live.start(shortTrip.plusSeconds(2));
        live.destination(first, Duration.ofMinutes(18), shortTrip.plusSeconds(3));
        check(live.snapshot().remainingSeconds.isEmpty(), "old connection cannot populate new session");
        live.destination(second, Duration.ofSeconds(239), shortTrip.plusSeconds(4));
        check(latest(changes).budgetSeconds == 11, "whole seconds retained");
        live.invalidate(second, shortTrip.plusSeconds(5), "권한 없음");
        check(latest(changes).selected.isEmpty(), "permission loss clears playback plan");
        long third = live.start(shortTrip.plusSeconds(6));
        live.destination(third, Duration.ofSeconds(-1), shortTrip.plusSeconds(7));
        check(live.snapshot().remainingSeconds.isEmpty() && !live.isCurrent(third), "invalid duration terminates session");
        long fourth = live.start(shortTrip.plusSeconds(8));
        live.close(shortTrip.plusSeconds(9));
        live.destination(fourth, Duration.ofMinutes(18), shortTrip.plusSeconds(10));
        check(live.snapshot().remainingSeconds.isEmpty(), "callback after release ignored");
        for (State state : State.values()) {
            check(!AccessPolicy.canInteract(false, state), "background activity is blocked");
        }
        check(!AccessPolicy.canInteract(true, State.UNKNOWN), "unknown blocks UI and speech");
        check(!AccessPolicy.canInteract(true, State.RESTRICTED), "restriction blocks UI and speech");
        check(AccessPolicy.canInteract(true, State.PARKED_USE_ALLOWED), "only foreground parked use allowed");
        for (Object raw : new Object[] {null, -1, 0, 4, 240, 1080, Long.MAX_VALUE, "08:58"}) {
            try {
                NaviTimeContract.decode(raw);
                throw new AssertionError("unverified SDK unit must not authorize playback");
            } catch (IllegalStateException expected) { /* No guessed unit. */ }
        }
        System.out.println("Navigation and access checks passed (normalized fixtures; not SDK callbacks)");
    }

    private static Briefing.Plan latest(List<Briefing.Plan> changes) { return changes.get(changes.size() - 1); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
