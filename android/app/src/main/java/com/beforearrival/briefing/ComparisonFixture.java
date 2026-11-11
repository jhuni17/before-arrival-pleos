package com.beforearrival.briefing;

final class ComparisonFixture {
    static boolean enabled(android.content.Context context) {
        return BuildConfig.DEBUG && "com.beforearrival.briefing".equals(context.getPackageName())
                && !"off".equals(BuildConfig.TTS_COMPARISON);
    }
    static final String[] TEXTS = {
                "첫 번째 시험 주제는 날씨입니다. 오늘은 맑고 바람이 약합니다. 기온은 이십삼 도입니다. 외출 전 일기 예보를 확인하세요.",
                "두 번째 시험 주제는 일정입니다. 오전 아홉 시 삼십 분에 회의가 있습니다. 준비물은 노트와 필기구입니다. 약속 시간을 확인하세요.",
                "세 번째 시험 주제는 문화입니다. 도서관에서 한국 현대 문학 전시가 열립니다. 운영 시간은 오전 열 시부터 오후 여섯 시까지입니다."
    };
    static String transcript() {
        return "디버그 고정 음성 비교 · " + BuildConfig.TTS_COMPARISON
                + "\n선택된 기사 대신 시험 문장 v1을 재생합니다. Pleos 재개는 현재 주제를 처음부터 읽습니다.\n\n"
                + String.join("\n\n", TEXTS);
    }
    private ComparisonFixture() {}
}
