package com.beforearrival.briefing;

import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;

import com.beforearrival.briefing.Briefing.Item;

public final class BriefingCheck {
    public static void main(String[] args) {
        SampleRouteInput route = new SampleRouteInput();
        EnumSet<Item> all = EnumSet.allOf(Item.class);

        Briefing.Plan full = Briefing.plan(route.snapshot(), all, Item.GAME);
        check(full.budgetSeconds == 45, "18-minute listening budget");
        check(full.selected.equals(List.of(Item.GAME, Item.SCHEDULE, Item.COMMUNITY)), "18-minute priority");
        check(full.selectedSeconds == 32 && full.deferred.isEmpty(), "18-minute selections fit");
        check(full.narration().startsWith(Item.GAME.narration), "narration follows priority");

        route.select(4);
        Briefing.Plan shortTrip = Briefing.plan(route.snapshot(), all, Item.GAME);
        check(shortTrip.budgetSeconds == 12, "4-minute listening budget");
        check(shortTrip.selected.equals(List.of(Item.SCHEDULE)), "near deadline overrides priority");
        check(shortTrip.deferred.equals(List.of(Item.GAME, Item.COMMUNITY)), "deferred overflow");

        EnumSet<Item> noSchedule = EnumSet.of(Item.GAME, Item.COMMUNITY);
        Briefing.Plan disabled = Briefing.plan(route.snapshot(), noSchedule, Item.COMMUNITY);
        check(disabled.selected.equals(List.of(Item.GAME)), "disabled schedule and oversized priority");
        check(disabled.deferred.equals(List.of(Item.COMMUNITY)), "estimate determines eligibility");

        RouteInput.Snapshot unknown = new RouteInput.Snapshot(OptionalInt.empty(), route.snapshot().observedAt, "테스트");
        Briefing.Plan noEta = Briefing.plan(unknown, all, Item.GAME);
        check(noEta.budgetSeconds == 0 && noEta.selected.isEmpty() && noEta.deferred.size() == 3, "unknown ETA");

        RouteInput.Snapshot afterDeadline = new RouteInput.Snapshot(OptionalInt.of(4),
                SampleRouteInput.demoTime(8, 59), "테스트");
        check(Briefing.plan(afterDeadline, all, Item.GAME).selected.equals(List.of(Item.GAME)), "expired deadline");

        Briefing.Plan none = Briefing.plan(route.snapshot(), EnumSet.noneOf(Item.class), Item.SCHEDULE);
        check(none.narration().isEmpty() && none.deferred.isEmpty(), "all sources disabled");
        System.out.println("Briefing checks passed");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
