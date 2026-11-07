package com.beforearrival.briefing;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
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
        assertEquals("이번 주말, 협동 모드가 열려요", next.getText().toString());
        Button shortTrip = field("etaShort");
        shortTrip.performClick();
        assertEquals("듣기 예산 12초 · 1건 약 8초", summary.getText().toString());
        assertEquals("9시 팀 회의가 3층으로 바뀌었어요", next.getText().toString());
        ContentCatalog catalog = field("catalog");
        catalog.move("sample-community", -1); catalog.move("sample-community", -1);
        catalog.persist(activity.getSharedPreferences("briefing", 0));
        ((Button) field("etaLong")).performClick();
        assertEquals("출근길에 듣기 좋은 10분짜리 팟캐스트", next.getText().toString());
        TextView transcript = field("transcript");
        assertTrue(transcript.getText().toString().startsWith(catalog.source("sample-community").items.get(0).summary));
        controller.recreate(); activity = controller.get();
        ContentCatalog restored = field("catalog");
        assertEquals("sample-community", restored.sources.get(0).id);

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
        Button save = rows.findViewWithTag("sample-game:sample-game-item");
        save.performClick();
        assertEquals("저장됨", save.getText().toString());
        assertTrue(activity.getSharedPreferences("briefing", 0)
                .getString(ContentCatalog.STORAGE_KEY, "").contains("sample-game-item"));
        controller.recreate();
        activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        TextView heading = field("savedHeading");
        assertEquals("도착해서 보기 · 1 · 미확인 1", heading.getText().toString());
        LinearLayout savedRows = field("savedRows");
        ((Button) savedRows.getChildAt(0)).performClick();
        AlertDialog dialog = field("activeDialog");
        assertTrue(dialog.isShowing());
    }

    @Test public void addOfficialSourceThroughFormReordersIntoActualBriefingAndPersists() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ((Button) field("priorityButton")).performClick();
        AlertDialog dialog = field("activeDialog");
        Spinner spinner = descendants(dialog.getWindow().getDecorView(), Spinner.class).get(0);
        spinner.setSelection(1); Shadows.shadowOf(Looper.getMainLooper()).idle();
        List<EditText> inputs = descendants(dialog.getWindow().getDecorView(), EditText.class);
        inputs.get(0).setText("내 LoL 패치");
        inputs.get(1).setText("https://www.leagueoflegends.com/ko-kr/news/game-updates/");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Source added = catalog.sources.get(3);
        assertEquals("connected_snapshot", added.status);
        LinearLayout sources = field("sourceRows");
        for (int i = 0; i < 3; i++) ((Button) sources.findViewWithTag("up:" + added.id)).performClick();
        TextView next = field("nextItem");
        assertEquals(added.items.get(0).title, next.getText().toString());
        ContentCatalog.Plan plan = field("plan");
        assertTrue(plan.narration().startsWith(added.items.get(0).summary));
        ((Button) field("etaShort")).performClick();
        plan = field("plan");
        assertEquals("sample-schedule", plan.selected.get(0).sourceId);
        assertFalse(plan.narration().contains(added.items.get(0).summary));
        controller.recreate(); activity = controller.get();
        catalog = field("catalog"); assertEquals(added.id, catalog.sources.get(0).id);
    }

    @Test public void restrictedHiddenActionsCannotChangeSourceOrSave() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        LinearLayout sources = field("sourceRows"), rows = field("briefingRows");
        CheckBox enabled = sources.findViewWithTag("enabled:sample-game");
        Button up = sources.findViewWithTag("up:sample-schedule");
        Button save = rows.findViewWithTag("sample-game:sample-game-item");
        setAccess(AccessPolicy.State.RESTRICTED);
        enabled.setChecked(false); up.performClick(); save.performClick();
        ((Button) field("priorityButton")).performClick();
        ContentCatalog catalog = field("catalog");
        assertTrue(catalog.source("sample-game").enabled);
        assertTrue(enabled.isChecked());
        assertEquals("sample-game", catalog.sources.get(0).id);
        assertTrue(catalog.savedItems.isEmpty());
        assertNull(field("activeDialog"));
    }

    @Test public void injectedRefreshUpdatesCatalogAndRestrictionCancelsLateCallback() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Source source = catalog.add("Steam", "steam", "https://store.steampowered.com/news/app/570");
        String snapshot = "{\"items\":[{\"id\":\"test-official\",\"title\":\"공식 업데이트\","
                + "\"summary\":\"실제 가져온 응답 테스트\",\"estimatedSeconds\":8,"
                + "\"originalUrl\":\"https://store.steampowered.com/news/app/570/view/123\"}]}";
        ReflectionHelpers.setField(activity, "feedLoader", (MainActivity.FeedLoader) ignored -> snapshot);
        Thread worker = ReflectionHelpers.callInstanceMethod(activity, "refreshSource",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Source.class, source));
        worker.join(2000); assertFalse(worker.isAlive()); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("connected", source.status); assertEquals(1, source.items.size());
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        ReflectionHelpers.setField(activity, "feedLoader", (MainActivity.FeedLoader) ignored -> {
            started.countDown(); finish.await(2, java.util.concurrent.TimeUnit.SECONDS); return "{\"items\":[]}";
        });
        worker = ReflectionHelpers.callInstanceMethod(activity, "refreshSource",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Source.class, source));
        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
        setAccess(AccessPolicy.State.RESTRICTED); finish.countDown();
        worker.join(2000); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, source.items.size()); assertEquals("connected", source.status);
    }

    @Test public void disablingThenReenablingSourceDiscardsItsOldRefresh() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Source source = catalog.add("Steam", "steam", "https://store.steampowered.com/news/app/570");
        ReflectionHelpers.callInstanceMethod(activity, "refreshPlan");
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        ReflectionHelpers.setField(activity, "feedLoader", (MainActivity.FeedLoader) ignored -> {
            started.countDown(); finish.await(2, java.util.concurrent.TimeUnit.SECONDS); return "{\"items\":[]}";
        });
        Thread worker = ReflectionHelpers.callInstanceMethod(activity, "refreshSource",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Source.class, source));
        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
        LinearLayout rows = field("sourceRows");
        ((CheckBox) rows.findViewWithTag("enabled:" + source.id)).setChecked(false);
        Button refresh = rows.findViewWithTag("refresh:" + source.id);
        assertFalse(refresh.isEnabled());
        assertNull(ReflectionHelpers.callInstanceMethod(activity, "refreshSource",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Source.class, source)));
        ((CheckBox) rows.findViewWithTag("enabled:" + source.id)).setChecked(true);
        finish.countDown(); worker.join(2000); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("link_saved", source.status);
        assertNull(source.lastSuccessAt);
    }

    @Test public void savedOfficialArticleOpensExactUrlOnlyWhileAllowed() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Source source = catalog.add("내 롤", "lol", "https://www.leagueoflegends.com/ko-kr/news/game-updates/");
        ContentCatalog.Item item = source.items.get(0);
        catalog.toggleSaved(item);
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        LinearLayout rows = field("savedRows");
        ((Button) rows.getChildAt(0)).performClick();
        AlertDialog dialog = field("activeDialog");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        // AlertDialog dispatches its button callback through the main Handler.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        android.content.Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("Official action must dispatch after draining the button callback", opened);
        assertEquals(android.content.Intent.ACTION_VIEW, opened.getAction());
        assertEquals(item.originalUrl, opened.getDataString());
        assertFalse(catalog.isConfirmed(item));
        assertEquals(1, catalog.unreadCount());
        ((Button) rows.getChildAt(0)).performClick();
        dialog = field("activeDialog");
        Button open = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        open.performClick(); // Queue while allowed; permission must be checked when dispatched.
        setAccess(AccessPolicy.State.RESTRICTED);
        assertFalse(dialog.isShowing());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        open.performClick(); // Stale button after dismissal must also remain blocked.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void confirmationFailureRetryRecreationAndStaleHandlers() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item item = catalog.sources.get(0).items.get(0);
        catalog.toggleSaved(item);
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        android.content.SharedPreferences preferences = field("preferences");
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        Button confirm = openConfirmation();
        assertFalse(catalog.isConfirmed(item)); // Opening details does not confirm.
        confirm.performClick();
        assertEquals("저장 실패 · 다시 시도", confirm.getText().toString());
        assertEquals(1, catalog.unreadCount());
        ReflectionHelpers.setField(activity, "preferences", preferences);
        confirm.performClick();
        assertTrue(catalog.isConfirmed(item)); assertEquals(0, catalog.unreadCount());
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        catalog = field("catalog");
        assertTrue(catalog.isConfirmed(item)); assertEquals(0, catalog.unreadCount());
        openConfirmation().performClick();
        assertFalse(catalog.isConfirmed(item)); assertEquals(1, catalog.unreadCount());
        for (AccessPolicy.State state : new AccessPolicy.State[]{AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
            Button stale = openConfirmation();
            AlertDialog dialog = field("activeDialog");
            setAccess(state); assertFalse(dialog.isShowing());
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            stale.performClick();
            assertFalse(catalog.isConfirmed(item)); assertNull(field("activeDialog"));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
        Button stale = openConfirmation();
        controller.pause(); controller.resume();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        stale.performClick(); assertFalse(catalog.isConfirmed(item));
        openConfirmation().performClick();
        assertTrue(catalog.isConfirmed(item));
    }

    @Test public void savedFilterKeepsOrderTotalsAndResetsOnlyOnRecreation() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (ContentCatalog.Source source : catalog.sources) items.add(source.items.get(0));
        for (ContentCatalog.Item item : items) catalog.toggleSaved(item);
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        Button all = field("savedAll"), unread = field("savedUnread");
        assertTrue(all.isSelected());
        ContentCatalog.Plan plan = field("plan");
        String narration = plan.narration();
        unread.performClick();
        assertTrue(unread.isSelected()); assertFalse(all.isSelected());
        assertEquals("미확인만 선택됨", unread.getContentDescription().toString());
        LinearLayout rows = field("savedRows");
        assertEquals(items.size() - 1, rows.getChildCount());
        int row = 0;
        for (ContentCatalog.Item item : items) if (!catalog.isConfirmed(item))
            assertEquals(item.title + " · 미확인", ((Button) rows.getChildAt(row++)).getText().toString());
        assertEquals("도착해서 보기 · " + items.size() + " · 미확인 " + (items.size() - 1),
                ((TextView) field("savedHeading")).getText().toString());
        openConfirmation();
        AlertDialog detail = field("activeDialog");
        assertTrue(descendants(detail.getWindow().getDecorView(), TextView.class).stream()
                .anyMatch(view -> view.getText().toString().contains(ContentCatalog.LOCAL_NOTICE)));
        detail.dismiss();
        assertEquals(items.size() - 1, catalog.unreadCount());
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertTrue(unread.isSelected());
        assertEquals(narration, ((ContentCatalog.Plan) field("plan")).narration());
        assertEquals(items, catalog.savedItems);
        controller.recreate(); activity = controller.get();
        assertTrue(((Button) field("savedAll")).isSelected());
        assertFalse(((Button) field("savedUnread")).isSelected());
    }

    @Test public void unreadFilterEmptyFailureRetryAndReversal() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ((Button) field("savedUnread")).performClick();
        LinearLayout rows = field("savedRows");
        assertEquals(1, rows.getChildCount());
        assertEquals("저장한 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item item = catalog.sources.get(0).items.get(0);
        catalog.toggleSaved(item);
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        android.content.SharedPreferences preferences = field("preferences");
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        Button confirm = openConfirmation();
        confirm.performClick();
        assertEquals("저장 실패 · 다시 시도", confirm.getText().toString());
        assertEquals(1, rows.getChildCount()); assertFalse(catalog.isConfirmed(item));
        assertEquals("도착해서 보기 · 1 · 미확인 1", ((TextView) field("savedHeading")).getText().toString());
        ReflectionHelpers.setField(activity, "preferences", preferences);
        confirm.performClick();
        assertTrue(catalog.isConfirmed(item)); assertEquals(0, catalog.unreadCount());
        assertEquals("미확인 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
        assertEquals("도착해서 보기 · 1 · 미확인 0", ((TextView) field("savedHeading")).getText().toString());
        ((Button) rows.getChildAt(1)).performClick();
        assertTrue(((Button) field("savedAll")).isSelected());
        openConfirmation().performClick();
        ((Button) field("savedUnread")).performClick();
        assertEquals(1, rows.getChildCount());
        assertEquals(item.title + " · 미확인", ((Button) rows.getChildAt(0)).getText().toString());
    }

    @Test public void savedFilterActionsStayBlockedAcrossRestrictionsAndPause() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item item = catalog.sources.get(0).items.get(0);
        catalog.toggleSaved(item);
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(item, true, preferences));
        ((Button) field("savedUnread")).performClick();
        LinearLayout rows = field("savedRows");
        Button emptyAll = (Button) rows.getChildAt(1);
        Button all = field("savedAll"), unread = field("savedUnread");
        for (AccessPolicy.State state : new AccessPolicy.State[]{AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
            setAccess(state);
            emptyAll.performClick(); all.performClick();
            assertTrue(unread.isSelected());
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            assertTrue(unread.isSelected()); assertNull(field("activeDialog"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
        controller.pause();
        emptyAll.performClick(); all.performClick();
        assertTrue(unread.isSelected());
        controller.resume();
        assertTrue(unread.isSelected()); assertNull(field("activeDialog"));
        assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        emptyAll.performClick();
        assertTrue(all.isSelected());
        setAccess(AccessPolicy.State.UNKNOWN);
        unread.performClick(); assertTrue(all.isSelected());
    }

    @Test public void nextUnreadUsesSavedOrderWrapsAndLeavesDataUntouched() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        java.util.Map<String, ?> stored = preferences.getAll();
        ContentCatalog.Plan plan = field("plan");
        String narration = plan.narration();
        for (boolean unread : new boolean[]{false, true}) {
            ((Button) field(unread ? "savedUnread" : "savedAll")).performClick();
            showDetail(items.get(0));
            Button staleNext = detailAction("next-unread");
            Button staleConfirm = detailAction("confirm-saved");
            AlertDialog old = field("activeDialog");
            staleNext.performClick();
            assertFalse(old.isShowing());
            assertDetail(items.get(2));
            AlertDialog current = field("activeDialog");
            staleNext.performClick(); staleConfirm.performClick();
            assertSame(current, field("activeDialog"));
            for (int i = 3; i < items.size(); i++) {
                detailAction("next-unread").performClick();
                assertDetail(items.get(i));
            }
            detailAction("next-unread").performClick();
            assertDetail(items.get(0));
            showDetail(items.get(1)); // A confirmed detail uses the same navigation.
            detailAction("next-unread").performClick();
            assertDetail(items.get(2));
            assertEquals(items, catalog.savedItems);
            assertEquals(items.size() - 1, catalog.unreadCount());
            assertEquals(stored, preferences.getAll());
            assertSame(plan, field("plan"));
            assertEquals(narration, plan.narration());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
    }

    @Test public void nextUnreadRechecksConfirmationAndDeletionAndNeverReopensSelf() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        showDetail(items.get(0));
        Button next = detailAction("next-unread");
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        next.performClick();
        assertDetail(items.get(2)); // Recheck at click time, rather than using the opening snapshot.
        showDetail(items.get(0));
        next = detailAction("next-unread");
        for (int i = 1; i < items.size(); i++)
            assertTrue(catalog.setConfirmed(items.get(i), true, preferences));
        AlertDialog original = field("activeDialog");
        next.performClick();
        assertSame(original, field("activeDialog"));
        assertFalse(next.isEnabled());
        assertTrue(descendants(original.getWindow().getDecorView(), TextView.class).stream()
                .anyMatch(view -> view.getVisibility() == View.VISIBLE
                        && view.getText().toString().equals("다른 미확인 소식이 없어요.")));
        showDetail(items.get(0));
        assertFalse(detailAction("next-unread").isEnabled());
        assertTrue(catalog.setConfirmed(items.get(1), false, preferences));
        showDetail(items.get(0));
        next = detailAction("next-unread");
        catalog.toggleSaved(items.get(1)); // Deleted candidates must also be skipped.
        next.performClick();
        assertFalse(next.isEnabled());
        assertTrue(catalog.setConfirmed(items.get(2), false, preferences));
        showDetail(items.get(0));
        next = detailAction("next-unread");
        original = field("activeDialog");
        catalog.toggleSaved(items.get(0));
        next.performClick();
        assertSame(original, field("activeDialog"));
        assertFalse(catalog.isConfirmed(items.get(2)));
    }

    @Test public void nextUnreadAndConfirmStayStaleAfterRestrictionsPauseAndRecreation() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        ((Button) field("savedUnread")).performClick();
        for (int transition = 0; transition < 4; transition++) {
            showDetail(items.get(0));
            Button next = detailAction("next-unread"), confirm = detailAction("confirm-saved");
            AlertDialog old = field("activeDialog");
            if (transition < 2) {
                setAccess(transition == 0 ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
                setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            } else if (transition == 2) {
                controller.pause(); controller.resume();
                setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            } else {
                controller.recreate(); activity = controller.get();
                setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            }
            assertFalse(old.isShowing());
            next.performClick(); confirm.performClick();
            assertNull(field("activeDialog"));
            assertFalse(catalog.isConfirmed(items.get(0)));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertTrue(((Button) field(transition == 3 ? "savedAll" : "savedUnread")).isSelected());
        }
    }

    private List<ContentCatalog.Item> saveNavigationItems() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (ContentCatalog.Source source : catalog.sources) items.add(source.items.get(0));
        assertTrue(items.size() >= 3);
        for (ContentCatalog.Item item : items) catalog.toggleSaved(item);
        catalog.persist(field("preferences"));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        return items;
    }

    private void showDetail(ContentCatalog.Item item) {
        ReflectionHelpers.callInstanceMethod(activity, "showSavedDetail",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Item.class, item));
    }

    private Button detailAction(String tag) {
        AlertDialog dialog = field("activeDialog");
        return dialog.findViewById(android.R.id.custom).findViewWithTag(tag);
    }

    private void assertDetail(ContentCatalog.Item item) {
        AlertDialog dialog = field("activeDialog");
        List<TextView> texts = descendants(dialog.getWindow().getDecorView(), TextView.class);
        assertTrue(texts.stream().anyMatch(view -> view.getText().toString().equals(item.title)));
        assertTrue(texts.stream().anyMatch(view -> view.getText().toString().equals(item.summary + "\n\n"
                + (item.originalUrl == null ? "예시 콘텐츠" : item.originalUrl) + "\n\n" + ContentCatalog.LOCAL_NOTICE)));
        ContentCatalog catalog = field("catalog");
        assertEquals(catalog.canOpen(item), dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null
                && dialog.getButton(AlertDialog.BUTTON_POSITIVE).getVisibility() == View.VISIBLE);
    }

    private Button openConfirmation() {
        LinearLayout rows = field("savedRows");
        ((Button) rows.getChildAt(0)).performClick();
        AlertDialog dialog = field("activeDialog");
        return dialog.findViewById(android.R.id.custom).findViewWithTag("confirm-saved");
    }

    private static <T> List<T> descendants(View view, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(view)) result.add(type.cast(view));
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result.addAll(descendants(((ViewGroup) view).getChildAt(i), type));
        return result;
    }

    private void setAccess(AccessPolicy.State state) {
        ReflectionHelpers.setField(activity, "restrictionState", state);
        ReflectionHelpers.callInstanceMethod(activity, "renderAccess");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private <T> T field(String name) { return ReflectionHelpers.getField(activity, name); }
}
