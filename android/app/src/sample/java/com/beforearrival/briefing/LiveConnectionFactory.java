package com.beforearrival.briefing;

import android.content.Context;
import java.time.Instant;

final class LiveConnectionFactory {
    static boolean isSdkIncluded() { return false; }

    static RouteConnection create(Context context, NavigationRouteInput route) {
        return new RouteConnection() {
            @Override public void start() {
                long token = route.start(Instant.now());
                route.invalidate(token, Instant.now(), "이 APK에는 Pleos SDK가 없어요");
            }
            @Override public void close() { route.close(Instant.now()); }
        };
    }
    private LiveConnectionFactory() {}
}
