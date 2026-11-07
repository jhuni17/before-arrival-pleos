package com.beforearrival.briefing;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.time.Instant;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/** SDK-dependent candidate; must be compiled and exercised against the real 3.0.5 AAR. */
final class PleosRouteConnection implements RouteConnection {
    private static final String PERMISSION = "pleos.car.permission.NAVI_ROUTE";
    private final Context context;
    private final NavigationRouteInput route;
    private final Handler main = new Handler(Looper.getMainLooper());
    private NaviHelper helper;
    private NaviHelperEventListener listener;
    private long token;
    private boolean closed;
    private boolean pending;
    private final Runnable timeout = () -> invalidate("내비 응답 없음 · 다시 연결해 주세요");
    private final Runnable poll = this::requestDestination;

    PleosRouteConnection(Context context, NavigationRouteInput route) {
        this.context = context;
        this.route = route;
    }

    @Override public void start() {
        token = route.start(Instant.now());
        if (!hasPermission()) {
            invalidate("내비 경로 조회 권한 없음");
            return;
        }
        try {
            helper = new NaviHelper(context);
            listener = new NaviHelperEventListener() {
                @Override public void onDestinationInfo(DestinationInfo info) {
                    Instant receivedAt = Instant.now();
                    main.post(() -> receive(info, receivedAt));
                }
                @Override public void onRouteCancelled() { postInvalidate("경로 취소 · 다시 연결해 주세요"); }
                @Override public void onDestinationChanged() { postInvalidate("목적지 변경 · 다시 연결해 주세요"); }
                @Override public void onError(NaviErrorCode code) { postInvalidate("내비 오류 · 다시 연결해 주세요"); }
            };
            helper.initialize(() -> {
                main.post(() -> {
                    if (!current()) return;
                    try {
                        helper.addListener(listener);
                        requestDestination();
                    } catch (RuntimeException error) { invalidate("내비 서비스 연결 실패"); }
                });
                return Unit.INSTANCE;
            }, error -> {
                postInvalidate("내비 서비스 연결 실패");
                return Unit.INSTANCE;
            });
            main.postDelayed(timeout, 10_000);
        } catch (RuntimeException | LinkageError error) {
            Log.w("BeforeArrival", "NaviHelper unavailable", error);
            invalidate("NaviHelper 사용 불가");
        }
    }

    private boolean hasPermission() {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean current() { return !closed && route.isCurrent(token); }

    private void requestDestination() {
        if (!current() || pending) return;
        if (!hasPermission()) { invalidate("내비 경로 조회 권한 없음"); return; }
        try {
            pending = true;
            helper.getDestinationInfo();
            main.removeCallbacks(timeout);
            main.postDelayed(timeout, 10_000);
        } catch (RuntimeException error) { invalidate("내비 서비스 사용 불가"); }
    }

    private void receive(DestinationInfo info, Instant receivedAt) {
        if (!current() || !pending) return;
        if (!hasPermission()) { invalidate("내비 경로 조회 권한 없음"); return; }
        pending = false;
        main.removeCallbacks(timeout);
        try {
            route.destination(token, NaviTimeContract.decode(info == null ? null : info.getRemainTime()), receivedAt);
            if (current()) main.postDelayed(poll, 5_000);
        } catch (RuntimeException error) {
            invalidate("도착 시간 단위·유효 범위 확인 필요");
        }
    }

    private void postInvalidate(String reason) { main.post(() -> { if (current()) invalidate(reason); }); }

    private void invalidate(String reason) {
        route.invalidate(token, Instant.now(), reason);
        dispose();
    }

    @Override public void close() {
        closed = true;
        route.close(Instant.now());
        dispose();
    }

    private void dispose() {
        main.removeCallbacks(timeout);
        main.removeCallbacks(poll);
        NaviHelper previous = helper;
        helper = null;
        if (previous == null) return;
        try { if (listener != null) previous.removeListener(listener); }
        catch (RuntimeException error) { Log.w("BeforeArrival", "Navi listener cleanup failed", error); }
        finally {
            listener = null;
            try { previous.release(); }
            catch (RuntimeException error) { Log.w("BeforeArrival", "Navi release failed", error); }
        }
    }
}
