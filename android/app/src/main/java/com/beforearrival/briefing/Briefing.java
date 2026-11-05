package com.beforearrival.briefing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

final class Briefing {
    enum Item {
        SCHEDULE("일정 변경", "9시 팀 회의가 3층으로 바뀌었어요", "08:58까지 회의 장소 확인",
                "오늘 아홉 시 팀 회의는 삼층 라운지예요. 여덟 시 오십팔 분까지 장소를 확인해 주세요.",
                8, SampleRouteInput.demoTime(8, 58)),
        GAME("게임 패치", "이번 주말, 협동 모드가 열려요", "즐겨찾는 게임 · 패치 노트 예시",
                "즐겨 하는 게임의 새 협동 모드가 이번 주말 열려요. 업데이트 소식은 저장해 두고 도착 후 확인할 수 있어요.",
                11, null),
        COMMUNITY("커뮤니티", "출근길에 듣기 좋은 10분짜리 팟캐스트", "즐겨찾는 게시판 · 인기글 예시",
                "오늘의 인기글은 출근길에 듣기 좋은 십 분짜리 팟캐스트 추천이에요. 짧은 에피소드 세 편을 소개하는 글이에요.",
                13, null);

        final String label;
        final String title;
        final String detail;
        final String narration;
        final int estimatedSeconds;
        final Instant deadline;

        Item(String label, String title, String detail, String narration, int estimatedSeconds,
                Instant deadline) {
            this.label = label;
            this.title = title;
            this.detail = detail;
            this.narration = narration;
            this.estimatedSeconds = estimatedSeconds;
            this.deadline = deadline;
        }

        boolean isUrgent(Instant now) {
            if (deadline == null) return false;
            Duration untilDeadline = Duration.between(now, deadline);
            return !untilDeadline.isNegative() && untilDeadline.compareTo(Duration.ofMinutes(10)) <= 0;
        }
    }

    static final class Plan {
        final int budgetSeconds;
        final int selectedSeconds;
        final List<Item> selected;
        final List<Item> deferred;

        Plan(int budgetSeconds, int selectedSeconds, List<Item> selected, List<Item> deferred) {
            this.budgetSeconds = budgetSeconds;
            this.selectedSeconds = selectedSeconds;
            this.selected = Collections.unmodifiableList(selected);
            this.deferred = Collections.unmodifiableList(deferred);
        }

        String narration() {
            StringJoiner text = new StringJoiner(" ");
            for (Item item : selected) text.add(item.narration);
            return text.toString();
        }
    }

    static Plan plan(RouteInput.Snapshot route, Set<Item> enabled, Item priority) {
        int minutes = route.remainingMinutes.orElse(0);
        int budget = (int) Math.min(45, Math.max(0, minutes * 60L - 180) / 5);
        List<Item> candidates = new ArrayList<>(enabled);
        candidates.sort((left, right) -> {
            int urgency = Boolean.compare(right.isUrgent(route.observedAt), left.isUrgent(route.observedAt));
            if (urgency != 0) return urgency;
            int preference = Boolean.compare(right == priority, left == priority);
            return preference != 0 ? preference : left.compareTo(right);
        });

        List<Item> selected = new ArrayList<>();
        List<Item> deferred = new ArrayList<>();
        int selectedSeconds = 0;
        for (Item item : candidates) {
            if (item.estimatedSeconds <= budget - selectedSeconds) {
                selected.add(item);
                selectedSeconds += item.estimatedSeconds;
            } else {
                deferred.add(item);
            }
        }
        return new Plan(budget, selectedSeconds, selected, deferred);
    }

    private Briefing() {}
}
