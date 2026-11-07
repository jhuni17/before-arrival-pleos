package com.beforearrival.briefing;

import android.content.Context;

final class LiveConnectionFactory {
    static boolean isSdkIncluded() { return true; }

    static RouteConnection create(Context context, NavigationRouteInput route) {
        return new PleosRouteConnection(context.getApplicationContext(), route);
    }
    private LiveConnectionFactory() {}
}
