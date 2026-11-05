package com.beforearrival.briefing;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Injects UX state in JVM only; does not impersonate a real CarUxRestrictions callback. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before public void setUp() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
    }
    @After public void tearDown() { controller.pause().stop().destroy(); }

    @Test public void unknownAndRestrictedHideControlsAndCloseDialog() {
        LinearLayout content = field("content");
        assertEquals(View.GONE, content.getVisibility());
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertEquals(View.VISIBLE, content.getVisibility());
        Button priority = field("priorityButton");
        priority.performClick();
        AlertDialog dialog = field("activeDialog");
        assertTrue(dialog.isShowing());
        setAccess(AccessPolicy.State.RESTRICTED);
        assertFalse(dialog.isShowing());
        assertEquals(View.GONE, content.getVisibility());
        TextView notice = field("restrictionNotice");
        assertTrue(notice.getText().toString().startsWith("주행 제한"));
        Button listen = field("listen");
        assertFalse(listen.isEnabled());
        setAccess(AccessPolicy.State.UNKNOWN);
        assertTrue(notice.getText().toString().startsWith("차량 상태 미확인"));
    }

    @Test public void sampleSelectionRefreshesBudgetAndDeadlineOrderAndPersistsPriority() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        TextView summary = field("summary");
        TextView next = field("nextItem");
        assertEquals("듣기 예산 45초 · 3건 약 32초", summary.getText().toString());
        assertEquals(Briefing.Item.GAME.title, next.getText().toString());
        Button shortTrip = field("etaShort");
        shortTrip.performClick();
        assertEquals("듣기 예산 12초 · 1건 약 8초", summary.getText().toString());
        assertEquals(Briefing.Item.SCHEDULE.title, next.getText().toString());
        Button priority = field("priorityButton");
        priority.performClick();
        AlertDialog dialog = field("activeDialog");
        dialog.getListView().performItemClick(null, Briefing.Item.COMMUNITY.ordinal(), 0);
        assertEquals("COMMUNITY", activity.getSharedPreferences("briefing", 0).getString("priority", ""));
    }

    @Test public void switchingToUnavailableLiveModeNeverFallsBackToSample() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        Button routeMode = field("routeMode");
        routeMode.performClick();
        TextView eta = field("eta");
        assertEquals("도착 시간 미확인", eta.getText().toString());
        Button listen = field("listen");
        assertFalse(listen.isEnabled());
        RouteInput input = field("routeInput");
        assertEquals(RouteInput.Source.NAVI_HELPER, input.snapshot().source);
        assertTrue(input.snapshot().remainingSeconds.isEmpty());
    }

    @Test public void savedItemSurvivesActivityRecreation() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        LinearLayout rows = field("briefingRows");
        Button save = rows.findViewWithTag(Briefing.Item.GAME);
        save.performClick();
        assertEquals("저장됨", save.getText().toString());
        assertTrue(activity.getSharedPreferences("briefing", 0)
                .getStringSet("saved", java.util.Set.of()).contains("GAME"));
        controller.recreate();
        activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        TextView heading = field("savedHeading");
        assertEquals("도착해서 보기 · 1", heading.getText().toString());
        LinearLayout savedRows = field("savedRows");
        ((Button) savedRows.getChildAt(0)).performClick();
        AlertDialog dialog = field("activeDialog");
        assertTrue(dialog.isShowing());
    }

    private void setAccess(AccessPolicy.State state) {
        ReflectionHelpers.setField(activity, "restrictionState", state);
        ReflectionHelpers.callInstanceMethod(activity, "renderAccess");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private <T> T field(String name) { return ReflectionHelpers.getField(activity, name); }
}
