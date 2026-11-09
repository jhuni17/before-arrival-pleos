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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
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
        assertEquals("듣기 예산 45초\n3건 약 32초", summary.getText().toString());
        assertEquals("이번 주말, 협동 모드가 열려요", next.getText().toString());
        Button shortTrip = field("etaShort");
        shortTrip.performClick();
        assertEquals("듣기 예산 12초\n1건 약 8초", summary.getText().toString());
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
        assertEquals("ETA 미확인", eta.getText().toString());
        Button listen = field("listen");
        assertFalse(listen.isEnabled());
        RouteInput input = field("routeInput");
        assertEquals(RouteInput.Source.NAVI_HELPER, input.snapshot().source);
        assertTrue(input.snapshot().remainingSeconds.isEmpty());
    }

    @Test public void firstGroupSharesEtaTitleAndPlaybackForSampleAndLiveReady() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        TextView eta = field("eta"), next = field("nextItem");
        Button listen = field("listen"), reconnect = field("reconnect");
        assertSame(((View) eta.getParent()).getParent(), ((View) next.getParent()).getParent());
        assertSame(next.getParent(), listen.getParent());
        assertTrue(((TextView) field("routeLabel")).getText().toString().contains("샘플"));
        assertEquals(View.VISIBLE, listen.getVisibility());
        assertEquals(View.GONE, reconnect.getVisibility());
        assertTrue(listen.isEnabled());
        RouteInput.Snapshot ready = new RouteInput.Snapshot(java.util.OptionalLong.of(1080),
                java.time.Instant.parse("2026-11-05T00:00:00Z"),
                RouteInput.Source.NAVI_HELPER, "실제 경로 · 테스트 입력");
        ReflectionHelpers.setField(activity, "liveMode", true);
        ReflectionHelpers.setField(activity, "routeInput", (RouteInput) () -> ready);
        ReflectionHelpers.callInstanceMethod(activity, "render");
        assertEquals("18분", eta.getText().toString());
        assertEquals(View.VISIBLE, listen.getVisibility());
        assertTrue(listen.isEnabled());
        assertTrue(((TextView) field("routeLabel")).getText().toString().contains("실제 ETA"));
        assertNotSame(next.getParent(), ((Button) field("transcriptButton")).getParent());
        next.setText("매우 긴 한국어 소식 제목을 확대된 글자로도 여러 줄에 걸쳐 확인할 수 있어야 해요");
        assertEquals(Integer.MAX_VALUE, next.getMaxLines());
        assertNull(next.getEllipsize());
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, next.getLayoutParams().height);
    }

    @Test public void overviewKeepsOnlyEssentialCopyAndNames() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        LinearLayout overview = field("overview");
        for (TextView text : descendants((View) field("content"), TextView.class)) {
            assertFalse("app-authored sample UI has no middle-dot separators: " + text.getText(),
                    text.getText().toString().contains("\u00b7"));
        }
        assertEquals("도착까지", ((TextView) field("accessLabel")).getText().toString());
        TextView title = field("nextItem");
        assertEquals("첫 소식\n" + title.getText(), title.getContentDescription().toString());
        assertEquals(3, ((ViewGroup) field("routePanel")).getChildCount());
        assertEquals(3, ((ViewGroup) field("newsPanel")).getChildCount());
        assertFalse(descendants(overview, TextView.class).stream().anyMatch(view ->
                view.getText().toString().equals("첫 소식") || view.getText().toString().equals("도착 전에")));
        Button listen = field("listen");
        assertEquals("브리핑 듣기", listen.getText().toString());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, listen.getImportantForAccessibility());
        assertTrue(listen.isEnabled());
    }

    @Test public void externalMiddleDotsSurviveSavedRowsAndDetail() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item item = new ContentCatalog.Item(new org.json.JSONObject()
                .put("id", "external-dot").put("sourceId", catalog.sources.get(0).id)
                .put("title", "외부 제목 · 원문").put("summary", "외부 요약 · 원문")
                .put("provenance", "official_snapshot"));
        catalog.toggleSaved(item);
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertTrue(descendants((View) field("savedRows"), Button.class).stream()
                .anyMatch(view -> view.getText().toString().startsWith(item.title + "\n")));
        showDetail(item);
        AlertDialog dialog = field("activeDialog");
        assertTrue(descendants(dialog.getWindow().getDecorView(), TextView.class).stream()
                .anyMatch(view -> view.getText().toString().equals(item.title)));
        assertTrue(descendants(dialog.getWindow().getDecorView(), TextView.class).stream()
                .anyMatch(view -> view.getText().toString().startsWith(item.summary + "\n\n")));
    }

    @Test public void movedReconnectIsUniqueAndRetainsLiveAndInteractionGuards() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        Button reconnect = field("reconnect");
        LiveRouteSession session = field("navigationRoute");
        RouteInput.Snapshot before = session.snapshot();
        reconnect.performClick(); // Retained hidden control must still reject sample mode.
        assertSame(before, session.snapshot());
        ((Button) field("routeMode")).performClick();
        assertEquals(View.VISIBLE, reconnect.getVisibility());
        assertTrue(reconnect.isEnabled());
        assertSame(((TextView) field("nextItem")).getParent(), reconnect.getParent());
        assertEquals(View.GONE, ((Button) field("listen")).getVisibility());
        assertEquals(View.GONE, ((Button) field("transcriptButton")).getVisibility());
        assertEquals(activity.getString(R.string.route_reconnect_hint),
                ((TextView) field("summary")).getText().toString());
        long count = descendants((View) field("content"), Button.class).stream()
                .filter(button -> button.getText().toString().equals(activity.getString(R.string.route_reconnect))).count();
        assertEquals(1, count);
        before = session.snapshot();
        reconnect.performClick();
        assertNotSame(before, session.snapshot());
        for (AccessPolicy.State state : new AccessPolicy.State[] {
                AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
            setAccess(state);
            before = session.snapshot();
            reconnect.performClick();
            assertSame(before, session.snapshot());
            assertEquals(View.GONE, ((View) field("content")).getVisibility());
        }
        assertTrue((Boolean) field("liveMode"));
        assertEquals(RouteInput.Source.NAVI_HELPER, session.snapshot().source);
    }

    /** Synthetic app-window layout only; not a Pleos emulator or SDK callback. */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void overviewReflowsWithinInsetSafeWindowWithLargeKoreanText() {
        float originalFontScale = RuntimeEnvironment.getApplication()
                .getResources().getConfiguration().fontScale;
        try {
            String[] primaryNames = {"eta", "nextItem", "listen"};
            float[] normalSizes = new float[primaryNames.length];
            float[] sizesSp = {40, 28, 20};
            for (int i = 0; i < primaryNames.length; i++) {
                normalSizes[i] = ((TextView) field(primaryNames[i])).getTextSize();
            }
            MainActivity original = activity;
            float normalScaledDensity = activity.getResources().getDisplayMetrics().scaledDensity;
            RuntimeEnvironment.setFontScale(1.3f);
            android.util.DisplayMetrics metrics = RuntimeEnvironment.getApplication()
                    .getResources().getDisplayMetrics();
            assertEquals(1.3f, RuntimeEnvironment.getApplication()
                    .getResources().getConfiguration().fontScale, 0.001f);
            assertTrue("effective display metrics enlarge SP", metrics.scaledDensity > normalScaledDensity);
            // Recreate after updating Robolectric's environment so new views consume scaled metrics.
            controller.recreate();
            activity = controller.get();
            assertNotSame("font scale change recreates the screen", original, activity);
            assertEquals(1.3f, activity.getResources().getConfiguration().fontScale, 0.001f);
            for (int i = 0; i < primaryNames.length; i++) {
                TextView view = field(primaryNames[i]);
                assertEquals(primaryNames[i] + " uses configuration font scale",
                        android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                                sizesSp[i], activity.getResources().getDisplayMetrics()),
                        view.getTextSize(), 0.5f);
            }
            // Android 35 scales SP nonlinearly: 40sp ETA may stay unchanged at 1.3x.
            assertTrue("listen is enlarged", ((TextView) field("listen")).getTextSize() > normalSizes[2]);
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            float density = activity.getResources().getDisplayMetrics().density;
            TextView title = field("nextItem");
            title.setText("긴 한국어 제목 · 오늘 아침 도착 전에 확인할 지역 소식과 회의 장소 변경 안내");
            root.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
                    .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(
                            (int) (32 * density), (int) (24 * density), (int) (48 * density), (int) (24 * density)))
                    .build());
            for (int width : new int[]{1400, 420, 1200, 420}) {
                for (int pass = 0; pass < 4; pass++) {
                    root.measure(View.MeasureSpec.makeMeasureSpec((int) (width * density), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec((int) (900 * density), View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
                }
                LinearLayout overview = field("overview");
                assertEquals(width >= 1200 ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL, overview.getOrientation());
                assertEquals(root.getWidth() - root.getPaddingLeft() - root.getPaddingRight(),
                        ((View) field("content")).getWidth());
                assertEquals((int) (32 * density), root.getPaddingLeft());
                assertEquals((int) (48 * density), root.getPaddingRight());
                assertNull(title.getEllipsize());
                if (width == 420) assertTrue(title.getLayout().getLineCount() > 1);
                assertEquals(0, title.getLayout().getEllipsisCount(title.getLineCount() - 1));
                assertTrue(title.getHeight() >= title.getLayout().getHeight());
                for (String name : primaryNames) {
                    TextView view = field(name);
                    assertTrue(name, view.getWidth() > 0);
                    android.text.Layout text = view.getLayout();
                    assertNotNull(name + " text layout", text);
                    assertEquals(name + " contains all characters", view.getText().length(),
                            text.getLineEnd(text.getLineCount() - 1));
                    assertTrue(name + " text fits height", text.getHeight() <= view.getHeight()
                            - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom());
                    for (int line = 0; line < text.getLineCount(); line++) {
                        assertEquals(name + " has no ellipsis", 0, text.getEllipsisCount(line));
                        assertTrue(name + " line fits width", text.getLineWidth(line) <= text.getWidth() + 1);
                    }
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    view.getDrawingRect(bounds);
                    ((ViewGroup) root).offsetDescendantRectToMyCoords(view, bounds);
                    assertTrue(name + " clears left inset", bounds.left >= root.getPaddingLeft());
                    assertTrue(name + " clears right inset", bounds.right <= root.getWidth() - root.getPaddingRight());
                    assertTrue(name + " clears top inset", bounds.top >= root.getPaddingTop());
                    assertTrue(name + " fits first viewport", bounds.bottom <= root.getHeight() - root.getPaddingBottom());
                }
                Button listen = field("listen");
                assertSame(((View) field("nextItem")).getParent(), listen.getParent());
                assertNotSame(listen.getParent(), ((View) field("summary")).getParent());
                assertTrue(title.isAccessibilityHeading());
                assertEquals(View.GONE, ((View) field("reconnect")).getVisibility());
            }
        } finally {
            RuntimeEnvironment.setFontScale(originalFontScale);
        }
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
        assertEquals("도착해서 보기\n1\n미확인 1", heading.getText().toString());
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

    @Test public void savedDetailButtonsRejectQueuedAndStaleCallbacksAcrossLifecycle() throws org.json.JSONException {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item item = catalog.add("공식 소식", "lol",
                "https://www.leagueoflegends.com/ko-kr/news/game-updates/").items.get(0);
        catalog.toggleSaved(item);
        ContentCatalog.Item other = catalog.sources.get(0).items.get(0);
        catalog.toggleSaved(other);
        assertTrue(catalog.setConfirmed(item, true, field("preferences")));
        for (int action : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL}) {
            for (int transition = 0; transition < 6; transition++) {
                catalog = field("catalog");
                android.content.SharedPreferences preferences = field("preferences");
                List<String> keys = new ArrayList<>();
                for (ContentCatalog.Item saved : catalog.savedItems) keys.add(saved.key());
                java.util.Map<String, ?> stored = preferences.getAll();
                showDetail(item);
                MainActivity owner = activity;
                AlertDialog old = field("activeDialog");
                Button stale = old.getButton(action);
                stale.performClick(); // Queue the DialogInterface callback without draining the Looper.
                if (transition < 2) {
                    ReflectionHelpers.setField(activity, "restrictionState", transition == 0
                            ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
                    ReflectionHelpers.callInstanceMethod(activity, "renderAccess");
                    ReflectionHelpers.setField(activity, "restrictionState", AccessPolicy.State.PARKED_USE_ALLOWED);
                    ReflectionHelpers.callInstanceMethod(activity, "renderAccess");
                } else if (transition == 2) {
                    controller.pause(); controller.resume();
                } else if (transition == 3) {
                    old.dismiss();
                } else if (transition == 4) {
                    showDetail(other);
                } else {
                    controller.recreate(); activity = controller.get();
                    setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
                }
                AlertDialog replacement = transition == 4 ? field("activeDialog") : null;
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                stale.performClick(); // Retained button references must also stay invalid.
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertFalse(old.isShowing());
                assertSame(replacement, field("activeDialog"));
                if (replacement != null) { assertTrue(replacement.isShowing()); assertDetail(other); }
                assertNull(Shadows.shadowOf(owner).getNextStartedActivity());
                if (owner != activity) assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
                catalog = field("catalog");
                List<String> remaining = new ArrayList<>();
                for (ContentCatalog.Item saved : catalog.savedItems) remaining.add(saved.key());
                assertEquals(keys, remaining);
                assertTrue(catalog.isConfirmed(item));
                assertFalse(catalog.isConfirmed(other));
                assertEquals(stored, preferences.getAll());
                assertEquals(stored, ((android.content.SharedPreferences) field("preferences")).getAll());
                assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            }
        }
    }

    @Test public void savedDetailRemovalOnlyRemovesSavedItemAndResaveIsUnread() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item item = items.get(0);
        assertTrue(catalog.setConfirmed(item, true, field("preferences")));
        showDetail(item);
        AlertDialog dialog = field("activeDialog");
        Button remove = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        remove.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(catalog.isSaved(item));
        assertEquals(items.subList(1, items.size()), catalog.savedItems);
        java.util.Map<String, ?> stored = ((android.content.SharedPreferences) field("preferences")).getAll();
        remove.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(catalog.isSaved(item)); // A stale toggle must never re-save a removed item.
        assertEquals(stored, ((android.content.SharedPreferences) field("preferences")).getAll());
        ReflectionHelpers.callInstanceMethod(activity, "toggleSaved",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Item.class, item));
        assertTrue(catalog.isSaved(item));
        assertFalse(catalog.isConfirmed(item));
        controller.recreate(); activity = controller.get();
        assertTrue(((ContentCatalog) field("catalog")).isSaved(item));
        assertFalse(((ContentCatalog) field("catalog")).isConfirmed(item));
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
        assertEquals("저장 실패\n다시 시도", confirm.getText().toString());
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
        assertSavedFilterCounts();
        assertEquals("미확인만 " + (items.size() - 1) + "건 선택됨", unread.getContentDescription().toString());
        LinearLayout rows = field("savedRows");
        assertEquals(items.size() - 1, rows.getChildCount());
        int row = 0;
        for (ContentCatalog.Item item : items) if (!catalog.isConfirmed(item))
            assertEquals(savedRowText(item), ((Button) rows.getChildAt(row++)).getText().toString());
        assertEquals("도착해서 보기\n" + items.size() + "\n미확인 " + (items.size() - 1),
                ((TextView) field("savedHeading")).getText().toString());
        openConfirmation();
        AlertDialog detail = field("activeDialog");
        assertTrue(descendants(detail.getWindow().getDecorView(), TextView.class).stream()
                .anyMatch(view -> view.getText().toString().contains(ContentCatalog.LOCAL_NOTICE.replace(" \u00b7 ", "\n"))));
        detail.dismiss();
        assertEquals(items.size() - 1, catalog.unreadCount());
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertTrue(unread.isSelected());
        assertEquals(narration, ((ContentCatalog.Plan) field("plan")).narration());
        assertEquals(items, catalog.savedItems);
        controller.recreate(); activity = controller.get();
        assertTrue(((Button) field("savedAll")).isSelected());
        assertFalse(((Button) field("savedUnread")).isSelected());
        assertSavedFilterCounts();
    }

    @Test public void confirmedFilterReopensInSavedOrderAndNavigatesWithoutChangingData() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        assertTrue(catalog.setConfirmed(items.get(2), true, preferences));
        java.util.Map<String, ?> stored = preferences.getAll();
        ContentCatalog.Plan plan = field("plan");
        String narration = plan.narration();
        Button confirmed = field("savedConfirmed");
        confirmed.performClick();
        assertTrue(confirmed.isSelected());
        assertFalse(((Button) field("savedAll")).isSelected());
        assertFalse(((Button) field("savedUnread")).isSelected());
        assertEquals("확인 완료만 2건 선택됨", confirmed.getContentDescription().toString());
        LinearLayout rows = field("savedRows");
        assertEquals(2, rows.getChildCount());
        for (int i = 0; i < 2; i++)
            assertEquals(savedRowText(items.get(i + 1)), ((Button) rows.getChildAt(i)).getText().toString());
        assertEquals("도착해서 보기\n" + items.size() + "\n미확인 " + (items.size() - 2),
                ((TextView) field("savedHeading")).getText().toString());
        ((Button) rows.getChildAt(0)).performClick();
        assertDetail(items.get(1));
        assertNull(detailAction("confirm-next"));
        Button stale = detailAction("confirm-saved");
        detailAction("next-unread").performClick();
        assertDetail(items.get(3 % items.size()));
        stale.performClick();
        assertTrue(catalog.isConfirmed(items.get(1)));
        assertTrue(confirmed.isSelected());
        assertEquals(stored, preferences.getAll());
        assertEquals(items, catalog.savedItems);
        assertSame(plan, field("plan"));
        assertEquals(narration, plan.narration());
        controller.recreate(); activity = controller.get();
        assertTrue(((Button) field("savedAll")).isSelected());
        assertFalse(((Button) field("savedConfirmed")).isSelected());
        assertTrue(((ContentCatalog) field("catalog")).isConfirmed(items.get(1)));
    }

    @Test public void confirmedFilterEmptyAndReversalFailureRetryPersist() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ((Button) field("savedConfirmed")).performClick();
        LinearLayout rows = field("savedRows");
        assertEquals(1, rows.getChildCount());
        assertEquals("저장한 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item item = catalog.sources.get(0).items.get(0);
        catalog.toggleSaved(item);
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertEquals("확인 완료한 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
        ((Button) rows.getChildAt(1)).performClick();
        assertTrue(((Button) field("savedAll")).isSelected());
        assertNull(field("activeDialog"));
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(item, true, preferences));
        ((Button) field("savedConfirmed")).performClick();
        Button revert = openConfirmation();
        assertEquals("미확인으로 되돌리기", revert.getText().toString());
        AlertDialog detail = field("activeDialog");
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        revert.performClick();
        assertEquals("저장 실패\n다시 시도", revert.getText().toString());
        assertSame(detail, field("activeDialog")); assertTrue(detail.isShowing());
        assertDetail(item);
        assertTrue(catalog.isConfirmed(item)); assertEquals(1, rows.getChildCount());
        ReflectionHelpers.setField(activity, "preferences", preferences);
        revert.performClick();
        assertNull(field("activeDialog")); assertFalse(catalog.isConfirmed(item));
        assertEquals("확인 완료한 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
        controller.recreate(); activity = controller.get();
        assertFalse(((ContentCatalog) field("catalog")).isConfirmed(item));
        assertTrue(((Button) field("savedAll")).isSelected());
    }

    @Test public void confirmedFilterAndStaleDetailStayBlockedAcrossLifecycle() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        assertTrue(catalog.setConfirmed(items.get(0), true, field("preferences")));
        Button confirmed = field("savedConfirmed");
        confirmed.performClick();
        for (int transition = 0; transition < 3; transition++) {
            Button revert = openConfirmation();
            Button next = detailAction("next-unread");
            AlertDialog old = field("activeDialog");
            if (transition == 2) controller.pause();
            else setAccess(transition == 0 ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
            ((Button) field("savedAll")).performClick();
            revert.performClick(); next.performClick();
            assertFalse(old.isShowing()); assertNull(field("activeDialog"));
            assertTrue(confirmed.isSelected()); assertTrue(catalog.isConfirmed(items.get(0)));
            if (transition == 2) controller.resume();
            else setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            revert.performClick(); next.performClick();
            assertNull(field("activeDialog")); assertTrue(confirmed.isSelected());
            assertTrue(catalog.isConfirmed(items.get(0)));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
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
        assertEquals("저장 실패\n다시 시도", confirm.getText().toString());
        assertEquals(1, rows.getChildCount()); assertFalse(catalog.isConfirmed(item));
        assertEquals("도착해서 보기\n1\n미확인 1", ((TextView) field("savedHeading")).getText().toString());
        ReflectionHelpers.setField(activity, "preferences", preferences);
        confirm.performClick();
        assertTrue(catalog.isConfirmed(item)); assertEquals(0, catalog.unreadCount());
        assertEquals("미확인 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
        assertEquals("도착해서 보기\n1\n미확인 0", ((TextView) field("savedHeading")).getText().toString());
        ((Button) rows.getChildAt(1)).performClick();
        assertTrue(((Button) field("savedAll")).isSelected());
        openConfirmation().performClick();
        ((Button) field("savedUnread")).performClick();
        assertEquals(1, rows.getChildCount());
        assertEquals(savedRowText(item), ((Button) rows.getChildAt(0)).getText().toString());
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

    @Test public void firstUnreadUsesWholeSavedOrderAndPreservesFilterAndData() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(items.get(0), true, preferences));
        java.util.Map<String, ?> stored = preferences.getAll();
        ContentCatalog.Plan plan = field("plan");
        String narration = plan.narration();
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            Button selected = field(filter);
            selected.performClick();
            assertFirstUnreadPreview(items.get(1));
            ((Button) field("savedFirstUnread")).performClick();
            assertDetail(items.get(1));
            AlertDialog shown = field("activeDialog");
            shown.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(shown.isShowing());
            assertNull(field("activeDialog"));
            assertTrue(selected.isSelected());
            assertEquals(items, catalog.savedItems);
            assertEquals(stored, preferences.getAll());
            assertSame(plan, field("plan"));
            assertEquals(narration, plan.narration());
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertTrue(((Button) field("savedAll")).isSelected());
        assertNull(field("activeDialog"));
        ((Button) field("savedFirstUnread")).performClick();
        assertDetail(items.get(1));
        assertFirstUnreadPreview(items.get(1));
    }

    @Test public void firstUnreadRechecksCandidatesAndRefreshesEmptyState() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertFalse(((Button) field("savedFirstUnread")).isEnabled());
        assertFirstUnreadPreview(null);
        assertEquals(View.VISIBLE, ((TextView) field("savedUnreadEmpty")).getVisibility());
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            Button button = field(filter);
            assertTrue(button.isEnabled());
            button.performClick();
            assertTrue(button.isSelected());
            assertSavedFilterCounts();
            assertNull(field("activeDialog"));
        }
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        Button entry = field("savedFirstUnread");
        assertTrue(entry.isEnabled());
        catalog.toggleSaved(items.get(0));
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        entry.performClick();
        assertDetail(items.get(2));
        assertFirstUnreadPreview(items.get(2));
        AlertDialog shown = field("activeDialog");
        shown.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(shown.isShowing());
        assertNull(field("activeDialog"));
        for (ContentCatalog.Item item : catalog.savedItems)
            assertTrue(catalog.setConfirmed(item, true, preferences));
        entry.performClick();
        assertNull(field("activeDialog"));
        assertFalse(entry.isEnabled());
        assertFirstUnreadPreview(null);
        assertEquals(View.VISIBLE, ((TextView) field("savedUnreadEmpty")).getVisibility());
        assertTrue(catalog.setConfirmed(items.get(2), false, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertTrue(entry.isEnabled());
        entry.performClick();
        assertDetail(items.get(2));
        assertFirstUnreadPreview(items.get(2));
        assertFalse(detailAction("next-unread").isEnabled());
        detailAction("confirm-saved").performClick();
        assertFalse(entry.isEnabled());
        assertFirstUnreadPreview(null);
        showDetail(items.get(2));
        detailAction("confirm-saved").performClick();
        assertTrue(entry.isEnabled());
        entry.performClick();
        ((AlertDialog) field("activeDialog")).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(catalog.savedItems.contains(items.get(2)));
        assertNull(field("activeDialog"));
        assertFalse(entry.isEnabled());
        assertFirstUnreadPreview(null);
    }

    @Test public void firstUnreadRejectsDirectCallbacksOutsideParkedResumedState() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        for (AccessPolicy.State state : new AccessPolicy.State[]{
                AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
            ((Button) field("savedFirstUnread")).performClick();
            AlertDialog shown = field("activeDialog");
            setAccess(state);
            assertFalse(shown.isShowing());
            ReflectionHelpers.callInstanceMethod(activity, "showFirstUnread");
            assertNull(field("activeDialog"));
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            assertNull(field("activeDialog"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
        ((Button) field("savedFirstUnread")).performClick();
        AlertDialog shown = field("activeDialog");
        controller.pause();
        assertFalse(shown.isShowing());
        ReflectionHelpers.callInstanceMethod(activity, "showFirstUnread");
        assertNull(field("activeDialog"));
        controller.resume();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertNull(field("activeDialog"));
        ((Button) field("savedFirstUnread")).performClick();
        assertDetail(items.get(0));
    }

    private void assertSavedFilterCounts() {
        ContentCatalog catalog = field("catalog");
        String source = field("savedSourceId");
        String query = ((String) field("savedQuery")).trim().toLowerCase(java.util.Locale.ROOT);
        int total = 0, unread = 0;
        for (ContentCatalog.Item item : catalog.savedItems) {
            if (source != null && !source.equals(item.sourceId)) continue;
            if (!item.title.trim().toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            total++;
            if (!catalog.isConfirmed(item)) unread++;
        }
        assertSavedScopeCounts(total, unread);
    }

    private void assertSavedScopeCounts(int total, int unread) {
        String[] fields = {"savedAll", "savedUnread", "savedConfirmed"};
        String[] names = {"전체", "미확인만", "확인 완료만"};
        String[] filters = {"ALL", "UNREAD", "CONFIRMED"};
        int[] counts = {total, unread, total - unread};
        for (int i = 0; i < fields.length; i++) {
            Button button = field(fields[i]);
            String text = names[i] + " " + counts[i] + "건";
            assertEquals(field("savedFilter").toString().equals(filters[i]), button.isSelected());
            assertEquals(text, button.getText().toString());
            assertEquals(text + (button.isSelected() ? " 선택됨" : ""),
                    button.getContentDescription().toString());
            assertNull(button.getEllipsize());
            assertEquals(Integer.MAX_VALUE, button.getMaxLines());
            assertEquals(20f * activity.getResources().getDisplayMetrics().scaledDensity,
                    button.getTextSize(), 0.1f);
        }
    }

    private void assertSavedFilterCounts(int total, int unread) {
        ContentCatalog catalog = field("catalog");
        assertEquals(total, catalog.savedItems.size());
        assertEquals(unread, catalog.unreadCount());
        assertEquals(total - unread, catalog.savedItems.stream().filter(catalog::isConfirmed).count());
        assertSavedFilterCounts();
    }

    private void assertFirstUnreadPreview(ContentCatalog.Item expected) {
        assertSavedFilterCounts();
        TextView preview = field("savedFirstUnreadPreview");
        assertEquals(expected == null ? "" : "첫 미확인\n" + expected.title,
                preview.getText().toString());
        assertEquals(expected == null ? View.GONE : View.VISIBLE, preview.getVisibility());
        assertFalse(preview.isClickable());
        assertNull(preview.getEllipsize());
        assertEquals(Integer.MAX_VALUE, preview.getMaxLines());
    }

    @Test public void firstUnreadPreviewPreservesFullLongTitleWithoutSideEffects() throws Exception {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        String title = "첫 미확인 소식의 긴 한국어 제목 전체를 줄바꿈으로 표시합니다. ";
        title = title + title + title + title;
        ContentCatalog.Item longItem = new ContentCatalog.Item(items.get(0).json().put("title", title));
        catalog.savedItems.set(0, longItem);
        android.content.SharedPreferences preferences = field("preferences");
        java.util.Map<String, ?> stored = preferences.getAll();
        List<ContentCatalog.Item> order = new ArrayList<>(catalog.savedItems);
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            ((Button) field(filter)).performClick();
            ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
            assertFirstUnreadPreview(longItem);
            assertEquals(stored, preferences.getAll());
            assertEquals(order, catalog.savedItems);
            assertEquals(items.size(), catalog.unreadCount());
            assertNull(field("activeDialog"));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
    }

    @Test public void firstUnreadPreviewTracksConfirmationDeletionUndoAndFailedRetries() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        for (boolean throwsAfterWrite : new boolean[]{false, true}) {
            assertFirstUnreadPreview(items.get(0));
            String storage = preferences.getString(ContentCatalog.STORAGE_KEY, null);
            List<ContentCatalog.Item> order = new ArrayList<>(catalog.savedItems);
            ReflectionHelpers.setField(activity, "preferences",
                    CatalogTest.failingCommits(preferences, throwsAfterWrite));
            showDetail(items.get(0));
            detailAction("confirm-saved").performClick();
            assertFirstUnreadPreview(items.get(0));
            assertFalse(catalog.isConfirmed(items.get(0)));
            assertEquals(order, catalog.savedItems);
            assertEquals(storage, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            ReflectionHelpers.setField(activity, "preferences", preferences);
            detailAction("confirm-saved").performClick();
            assertFirstUnreadPreview(items.get(1));
            ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
            undoConfirmation().performClick();
            assertFirstUnreadPreview(items.get(0));
            deleteSaved(items.get(0));
            assertFirstUnreadPreview(items.get(1));
            undoDeletion().performClick();
            assertFirstUnreadPreview(items.get(0));
        }
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertFirstUnreadPreview(items.get(0));
        assertTrue(((Button) field("savedAll")).isSelected());
    }

    @Test public void nextUnreadUsesSavedOrderWrapsAndLeavesDataUntouched() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        java.util.Map<String, ?> stored = preferences.getAll();
        ContentCatalog.Plan plan = field("plan");
        String narration = plan.narration();
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            ((Button) field(filter)).performClick();
            showDetail(items.get(0));
            assertDetail(items.get(0));
            assertPreview(items.get(2));
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
            assertPreview(items.get(0));
            detailAction("next-unread").performClick();
            assertDetail(items.get(0));
            showDetail(items.get(1)); // A confirmed detail uses the same navigation.
            assertPreview(items.get(2));
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

    @Test public void nextUnreadPreviewPreservesFullLongTitle() throws Exception {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        String title = "다음 소식의 전체 제목을 줄임 없이 읽을 수 있어야 합니다. ";
        title = title + title + title + title;
        ContentCatalog.Item longItem = new ContentCatalog.Item(items.get(1).json().put("title", title));
        catalog.savedItems.set(1, longItem);
        showDetail(items.get(0));
        assertPreview(longItem);
        detailAction("next-unread").performClick();
        assertDetail(longItem);
    }

    @Test public void nextUnreadRechecksConfirmationAndDeletionAndNeverReopensSelf() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        showDetail(items.get(0));
        Button next = detailAction("next-unread");
        assertPreview(items.get(1));
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        next.performClick();
        assertDetail(items.get(2)); // Recheck at click time, rather than using the opening snapshot.
        showDetail(items.get(0));
        assertPreview(items.get(2));
        assertTrue(catalog.setConfirmed(items.get(1), false, preferences));
        catalog.toggleSaved(items.get(2));
        detailAction("next-unread").performClick();
        assertDetail(items.get(1));
        catalog.toggleSaved(items.get(2));
        assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
        showDetail(items.get(0));
        next = detailAction("next-unread");
        for (int i = 1; i < items.size(); i++)
            assertTrue(catalog.setConfirmed(items.get(i), true, preferences));
        AlertDialog original = field("activeDialog");
        next.performClick();
        assertSame(original, field("activeDialog"));
        assertFalse(next.isEnabled());
        assertPreview(null);
        assertTrue(descendants(original.getWindow().getDecorView(), TextView.class).stream()
                .anyMatch(view -> view.getVisibility() == View.VISIBLE
                        && view.getText().toString().equals("다른 미확인 소식이 없어요.")));
        showDetail(items.get(0));
        assertFalse(detailAction("next-unread").isEnabled());
        assertPreview(null);
        assertTrue(catalog.setConfirmed(items.get(1), false, preferences));
        showDetail(items.get(0));
        next = detailAction("next-unread");
        catalog.toggleSaved(items.get(1)); // Deleted candidates must also be skipped.
        next.performClick();
        assertFalse(next.isEnabled());
        assertPreview(null);
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
            Button confirmNext = detailAction("confirm-next");
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
            next.performClick(); confirm.performClick(); confirmNext.performClick();
            assertNull(field("activeDialog"));
            assertFalse(catalog.isConfirmed(items.get(0)));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertTrue(((Button) field(transition == 3 ? "savedAll" : "savedUnread")).isSelected());
        }
    }

    @Test public void confirmNextUsesSavedOrderWrapsAndFinishesInBothFilters() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        String narration = ((ContentCatalog.Plan) field("plan")).narration();
        for (boolean unread : new boolean[]{false, true}) {
            for (ContentCatalog.Item item : items) assertTrue(catalog.setConfirmed(item, false, preferences));
            assertTrue(catalog.setConfirmed(items.get(1), true, preferences));
            ((Button) field(unread ? "savedUnread" : "savedAll")).performClick();
            showDetail(items.get(1));
            assertDetail(items.get(1));
            assertNull(detailAction("confirm-next"));
            showDetail(items.get(items.size() - 1));
            Button stale = detailAction("confirm-next");
            stale.performClick();
            assertSavedFilterCounts(items.size(), items.size() - 2);
            assertDetail(items.get(0));
            AlertDialog current = field("activeDialog");
            stale.performClick();
            assertSame(current, field("activeDialog"));
            detailAction("confirm-next").performClick();
            assertSavedFilterCounts(items.size(), items.size() - 3);
            for (int i = 2; i < items.size() - 1; i++) {
                assertDetail(items.get(i));
                detailAction("confirm-next").performClick();
                assertSavedFilterCounts(items.size(), items.size() - i - 2);
            }
            assertNull(field("activeDialog"));
            assertEquals(0, catalog.unreadCount());
            assertEquals(items, catalog.savedItems);
            LinearLayout rows = field("savedRows");
            if (unread) {
                assertEquals("미확인 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
                assertEquals("전체 보기", ((Button) rows.getChildAt(1)).getText().toString());
            } else assertEquals(items.size(), rows.getChildCount());
            assertEquals(narration, ((ContentCatalog.Plan) field("plan")).narration());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
        controller.recreate(); activity = controller.get();
        assertSavedFilterCounts(items.size(), 0);
        assertTrue(((Button) field("savedAll")).isSelected());
    }

    @Test public void confirmNextRechecksCurrentAndCandidatesAndRetriesFailedStorage() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        java.util.Map<String, ?> stored;
        for (boolean throwsAfterWrite : new boolean[]{false, true}) {
            assertTrue(catalog.setConfirmed(items.get(0), false, preferences));
            ((Button) field("savedAll")).performClick();
            showDetail(items.get(0));
            Button retry = detailAction("confirm-next");
            AlertDialog before = field("activeDialog");
            stored = preferences.getAll();
            String storage = preferences.getString(ContentCatalog.STORAGE_KEY, null);
            assertSavedFilterCounts(items.size(), items.size());
            assertEquals(items, catalog.savedItems);
            for (ContentCatalog.Item item : items) assertFalse(catalog.isConfirmed(item));
            assertDetail(items.get(0));
            ReflectionHelpers.setField(activity, "preferences",
                    CatalogTest.failingCommits(preferences, throwsAfterWrite));
            retry.performClick();
            assertSavedFilterCounts(items.size(), items.size());
            assertEquals("저장 실패\n다시 시도", retry.getText().toString());
            assertEquals("도착해서 보기\n" + items.size() + "\n미확인 " + items.size(),
                    ((TextView) field("savedHeading")).getText().toString());
            assertSame(before, field("activeDialog"));
            assertDetail(items.get(0));
            assertEquals(items, catalog.savedItems);
            for (ContentCatalog.Item item : items) assertFalse(catalog.isConfirmed(item));
            assertEquals(storage, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            assertEquals(stored, preferences.getAll());
            ReflectionHelpers.setField(activity, "preferences", preferences);
            retry.performClick();
            assertSavedFilterCounts(items.size(), items.size() - 1);
            assertEquals(items, catalog.savedItems);
            assertTrue(catalog.isConfirmed(items.get(0)));
            for (int i = 1; i < items.size(); i++) assertFalse(catalog.isConfirmed(items.get(i)));
            assertDetail(items.get(1));
        }
        assertTrue(catalog.setConfirmed(items.get(0), false, preferences));
        ((Button) field("savedAll")).performClick();
        showDetail(items.get(0));
        Button action = detailAction("confirm-next");
        AlertDialog original;
        catalog.toggleSaved(items.get(1));
        for (int i = 2; i < items.size() - 1; i++)
            assertTrue(catalog.setConfirmed(items.get(i), true, preferences));
        action.performClick();
        assertTrue(catalog.isConfirmed(items.get(0)));
        assertDetail(items.get(items.size() - 1));
        for (boolean deleted : new boolean[]{false, true}) {
            ContentCatalog.Item item = items.get(items.size() - 1);
            showDetail(item);
            action = detailAction("confirm-next");
            original = field("activeDialog");
            if (deleted) catalog.toggleSaved(item);
            else assertTrue(catalog.setConfirmed(item, true, preferences));
            stored = preferences.getAll();
            action.performClick();
            assertSame(original, field("activeDialog"));
            assertEquals(stored, preferences.getAll());
            if (!deleted) assertTrue(catalog.setConfirmed(item, false, preferences));
        }
    }

    @Test public void savedDetailPositionRefreshesAfterListChangesAndRecreation() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        ContentCatalog.Item last = items.get(items.size() - 1);
        showDetail(last);
        assertDetail(last);
        catalog.toggleSaved(items.get(0));
        assertTrue(catalog.setConfirmed(last, true, preferences));
        showDetail(last);
        assertDetail(last);
        catalog.toggleSaved(items.get(0)); // Re-added items move to the end of the saved order.
        catalog.persist(preferences);
        showDetail(items.get(0));
        assertDetail(items.get(0));
        ((Button) field("savedUnread")).performClick();
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertTrue(((Button) field("savedAll")).isSelected());
        showDetail(last);
        assertDetail(last);
        catalog = field("catalog");
        catalog.toggleSaved(last);
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        showDetail(last);
        assertNull(field("activeDialog"));
    }

    @Test public void undoConfirmationUpdatesEveryFilterWithoutChangingPlanOrSavedOrder() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Plan plan = field("plan");
        String narration = plan.narration();
        assertNull(undoConfirmation());
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            Button selected = field(filter);
            selected.performClick();
            showDetail(items.get(0));
            detailAction("confirm-saved").performClick();
            Button undo = undoConfirmation();
            assertNotNull(undo);
            LinearLayout region = field("savedUndo");
            assertTrue(descendants(region, TextView.class).stream()
                    .anyMatch(text -> text.getText().toString().equals(items.get(0).title)));
            undo.performClick();
            assertNull(undoConfirmation());
            assertFalse(catalog.isConfirmed(items.get(0)));
            assertEquals(items, catalog.savedItems);
            assertEquals(items.size(), catalog.unreadCount());
            assertTrue(selected.isSelected());
            assertTrue(((Button) field("savedFirstUnread")).isEnabled());
            assertEquals(View.GONE, ((TextView) field("savedUnreadEmpty")).getVisibility());
            LinearLayout rows = field("savedRows");
            assertEquals(filter.equals("savedConfirmed") ? 2 : items.size(), rows.getChildCount());
            assertNull(field("activeDialog"));
            assertSame(plan, field("plan"));
            assertEquals(narration, plan.narration());
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            undo.performClick(); // Successful undo consumes this callback.
            assertFalse(catalog.isConfirmed(items.get(0)));
        }
    }

    @Test public void undoConfirmationKeepsLatestSuccessAndIgnoresNavigationAndStaleButtons() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        showDetail(items.get(0));
        detailAction("confirm-next").performClick();
        assertDetail(items.get(1));
        Button old = undoConfirmation();
        detailAction("next-unread").performClick();
        assertDetail(items.get(2));
        ((AlertDialog) field("activeDialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        assertSame(old, undoConfirmation());
        showDetail(items.get(1));
        detailAction("confirm-saved").performClick();
        Button latest = undoConfirmation();
        old.performClick();
        assertSame(latest, undoConfirmation());
        assertTrue(catalog.isConfirmed(items.get(0)));
        assertTrue(catalog.isConfirmed(items.get(1)));
        latest.performClick();
        assertTrue(catalog.isConfirmed(items.get(0)));
        assertFalse(catalog.isConfirmed(items.get(1)));
    }

    @Test public void undoConfirmationAfterLastConfirmNextRestoresUnreadControls() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        for (int i = 1; i < items.size(); i++)
            assertTrue(catalog.setConfirmed(items.get(i), true, field("preferences")));
        ((Button) field("savedUnread")).performClick();
        showDetail(items.get(0));
        detailAction("confirm-next").performClick();
        assertSavedFilterCounts(items.size(), 0);
        assertNull(field("activeDialog"));
        assertEquals(0, catalog.unreadCount());
        assertFalse(((Button) field("savedFirstUnread")).isEnabled());
        assertEquals(View.VISIBLE, ((TextView) field("savedUnreadEmpty")).getVisibility());
        undoConfirmation().performClick();
        assertSavedFilterCounts(items.size(), 1);
        assertEquals(1, catalog.unreadCount());
        assertTrue(((Button) field("savedFirstUnread")).isEnabled());
        assertEquals(View.GONE, ((TextView) field("savedUnreadEmpty")).getVisibility());
        assertTrue(((Button) field("savedUnread")).isSelected());
        assertEquals(1, ((LinearLayout) field("savedRows")).getChildCount());
        for (int i = 1; i < items.size(); i++) assertTrue(catalog.isConfirmed(items.get(i)));
        assertNull(field("activeDialog"));
        controller.recreate(); activity = controller.get();
        assertSavedFilterCounts(items.size(), 1);
        assertEquals("ALL", field("savedFilter").toString());
        assertTrue(((Button) field("savedAll")).isSelected());
        ContentCatalog restored = field("catalog");
        assertFalse(restored.isConfirmed(items.get(0)));
        for (int i = 1; i < items.size(); i++) assertTrue(restored.isConfirmed(items.get(i)));
    }

    @Test public void undoConfirmationStorageFailuresKeepTargetAndRetryPersists() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        showDetail(items.get(0));
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        detailAction("confirm-saved").performClick();
        assertNull(undoConfirmation());
        ReflectionHelpers.setField(activity, "preferences", preferences);
        detailAction("confirm-saved").performClick();
        Button undo = undoConfirmation();
        String storage = preferences.getString(ContentCatalog.STORAGE_KEY, null);
        showDetail(items.get(1));
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        detailAction("confirm-next").performClick();
        assertSame(undo, undoConfirmation());
        assertFalse(catalog.isConfirmed(items.get(1)));
        assertEquals(storage, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        undo.performClick();
        assertTrue(catalog.isConfirmed(items.get(0)));
        assertSame(undo, undoConfirmation());
        assertTrue(undo.getText().toString().contains("다시 시도"));
        assertEquals(storage, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        ReflectionHelpers.setField(activity, "preferences", preferences);
        undo.performClick();
        controller.recreate(); activity = controller.get();
        assertFalse(((ContentCatalog) field("catalog")).isConfirmed(items.get(0)));
        assertNull(undoConfirmation());
    }

    @Test public void undoConfirmationDeletionResaveAndExplicitReversalInvalidateCallbacks() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        for (boolean delete : new boolean[]{true, false}) {
            showDetail(items.get(0));
            detailAction("confirm-saved").performClick();
            assertTrue(catalog.isConfirmed(items.get(0)));
            Button old = undoConfirmation();
            assertNotNull(old);
            showDetail(items.get(0));
            if (delete) {
                AlertDialog dialog = field("activeDialog");
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertFalse(catalog.isSaved(items.get(0)));
                assertFalse(catalog.isConfirmed(items.get(0)));
                assertNull(undoConfirmation());
                assertFalse(dialog.isShowing());
                assertNull(field("activeDialog"));
                ReflectionHelpers.callInstanceMethod(activity, "toggleSaved",
                        ReflectionHelpers.ClassParameter.from(ContentCatalog.Item.class, items.get(0)));
            } else detailAction("confirm-saved").performClick();
            assertTrue(catalog.isSaved(items.get(0)));
            assertFalse(catalog.isConfirmed(items.get(0)));
            assertNull(undoConfirmation());
            showDetail(items.get(0));
            detailAction("confirm-saved").performClick();
            assertTrue(catalog.isConfirmed(items.get(0)));
            Button current = undoConfirmation();
            assertNotNull(current);
            String storage = preferences.getString(ContentCatalog.STORAGE_KEY, null);
            old.performClick();
            assertTrue(catalog.isConfirmed(items.get(0)));
            assertTrue(catalog.isSaved(items.get(0)));
            assertEquals(storage, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            assertSame(current, undoConfirmation());
            current.performClick();
            assertTrue(catalog.isSaved(items.get(0)));
            assertFalse(catalog.isConfirmed(items.get(0)));
            assertNull(undoConfirmation());
            assertEquals(items.size(), catalog.savedItems.size());
            for (int i = 1; i < items.size(); i++) {
                assertTrue(catalog.isSaved(items.get(i)));
                assertFalse(catalog.isConfirmed(items.get(i)));
            }
        }
    }

    @Test public void undoConfirmationNeverReturnsAfterRestrictionsPauseOrRecreation() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        for (int transition = 0; transition < 4; transition++) {
            ContentCatalog catalog = field("catalog");
            assertTrue(catalog.setConfirmed(items.get(0), false, field("preferences")));
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            showDetail(items.get(0));
            detailAction("confirm-saved").performClick();
            Button old = undoConfirmation();
            ((Button) field("savedConfirmed")).performClick();
            if (transition < 2) setAccess(transition == 0
                    ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
            else if (transition == 2) controller.pause();
            else { controller.recreate(); activity = controller.get(); }
            old.performClick();
            assertNull(undoConfirmation());
            if (transition == 2) controller.resume();
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            old.performClick();
            assertTrue(((ContentCatalog) field("catalog")).isConfirmed(items.get(0)));
            assertNull(undoConfirmation());
            assertNull(field("activeDialog"));
            if (transition == 3) assertTrue(((Button) field("savedAll")).isSelected());
        }
    }

    private void assertSavedCapacity(int count) {
        assertSavedFilterCounts();
        assertEquals("전체 저장 " + count + "/" + ContentCatalog.SAVED_ITEM_LIMIT
                + "건\n추가 저장 가능 " + Math.max(0, ContentCatalog.SAVED_ITEM_LIMIT - count) + "건",
                ((TextView) field("savedCapacity")).getText().toString());
        TextView notice = field("savedCapacityNotice");
        assertEquals(ContentCatalog.SAVED_CAPACITY_NOTICE.replace(" \u00b7 ", "\n"), notice.getText().toString());
        assertEquals(count >= ContentCatalog.SAVED_ITEM_LIMIT ? View.VISIBLE : View.GONE, notice.getVisibility());
        assertTrue(notice.getMaxLines() > 1);
    }

    @Test public void capacityConfirmedShortcutNavigatesWithoutMutatingSavedState() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        for (int i = 0; i < ContentCatalog.SAVED_ITEM_LIMIT - 1; i++)
            assertTrue(catalog.save(CatalogTest.capacityItem(catalog, i), preferences));
        ContentCatalog.Item first = catalog.savedItems.get(0);
        assertTrue(catalog.setConfirmed(first, true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        Button shortcut = field("savedCapacityConfirmed");
        assertEquals(View.GONE, shortcut.getVisibility());
        shortcut.performClick();
        assertEquals("ALL", field("savedFilter").toString());
        assertTrue(catalog.setConfirmed(first, false, preferences));
        ContentCatalog.Item last = CatalogTest.capacityItem(catalog, ContentCatalog.SAVED_ITEM_LIMIT - 1);
        assertTrue(catalog.save(last, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertEquals(View.GONE, shortcut.getVisibility());
        shortcut.performClick();
        assertEquals("ALL", field("savedFilter").toString());
        assertTrue(catalog.setConfirmed(first, true, preferences));
        assertTrue(catalog.setConfirmed(last, true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertEquals(View.VISIBLE, shortcut.getVisibility());
        assertEquals("확인 완료 항목 보기\n전체 2건", shortcut.getText().toString());
        assertEquals(shortcut.getText().toString(), shortcut.getContentDescription().toString());
        assertNull(shortcut.getEllipsize());
        assertEquals(Integer.MAX_VALUE, shortcut.getMaxLines());
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, shortcut.getLayoutParams().width);
        assertEquals(20f * activity.getResources().getDisplayMetrics().scaledDensity,
                shortcut.getTextSize(), 0.1f);
        assertEquals(Math.round(56 * activity.getResources().getDisplayMetrics().density), shortcut.getMinimumHeight());
        String stored = preferences.getString(ContentCatalog.STORAGE_KEY, null);
        List<ContentCatalog.Item> before = new ArrayList<>(catalog.savedItems);
        String narration = ((ContentCatalog.Plan) field("plan")).narration();
        Object token = field("undoToken");
        Object deleted = field("undoDeleted");
        for (String filter : new String[]{"savedAll", "savedUnread"}) {
            ((Button) field(filter)).performClick();
            shortcut.performClick();
            assertEquals("CONFIRMED", field("savedFilter").toString());
            assertSavedFilterCounts(ContentCatalog.SAVED_ITEM_LIMIT, ContentCatalog.SAVED_ITEM_LIMIT - 2);
            LinearLayout rows = field("savedRows");
            assertEquals(2, rows.getChildCount());
            assertEquals(savedRowText(first), ((Button) rows.getChildAt(0)).getText().toString());
            assertEquals(savedRowText(last), ((Button) rows.getChildAt(1)).getText().toString());
            assertEquals(before, catalog.savedItems);
            assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            assertEquals(narration, ((ContentCatalog.Plan) field("plan")).narration());
            assertSame(token, field("undoToken"));
            assertSame(deleted, field("undoDeleted"));
            assertNull(field("activeDialog"));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
        ((Button) field("savedUnread")).performClick();
        for (AccessPolicy.State state : new AccessPolicy.State[]{AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
            setAccess(state);
            shortcut.performClick();
            ReflectionHelpers.callInstanceMethod(activity, "showCapacityConfirmed");
            assertEquals("UNREAD", field("savedFilter").toString());
            assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            assertEquals(before, catalog.savedItems);
        }
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        controller.pause();
        shortcut.performClick();
        ReflectionHelpers.callInstanceMethod(activity, "showCapacityConfirmed");
        assertEquals("UNREAD", field("savedFilter").toString());
        assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        controller.resume();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        deleteSaved(last);
        assertEquals(View.GONE, shortcut.getVisibility());
        shortcut.performClick();
        assertEquals("UNREAD", field("savedFilter").toString());
        undoDeletion().performClick();
        assertEquals(View.VISIBLE, shortcut.getVisibility());
        assertEquals(before, catalog.savedItems);
        assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        // A still-visible button must recheck conditions changed before the next render.
        assertTrue(catalog.setConfirmed(first, false, preferences));
        assertTrue(catalog.setConfirmed(last, false, preferences));
        shortcut.performClick();
        assertEquals("UNREAD", field("savedFilter").toString());
        controller.recreate(); activity = controller.get();
        assertEquals("ALL", field("savedFilter").toString());
        assertEquals(View.GONE, ((Button) field("savedCapacityConfirmed")).getVisibility());
    }

    @Test public void capacityDisplayUsesTotalAcrossFiltersAndDeleteUndoRecreation() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertTrue(((Button) field("savedAll")).isSelected());
        assertSavedCapacity(0);
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        for (int i = 0; i < ContentCatalog.SAVED_ITEM_LIMIT - 1; i++)
            assertTrue(catalog.save(CatalogTest.capacityItem(catalog, i), preferences));
        assertTrue(catalog.setConfirmed(catalog.savedItems.get(0), true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertSavedCapacity(ContentCatalog.SAVED_ITEM_LIMIT - 1);
        ContentCatalog.Item last = CatalogTest.capacityItem(catalog, ContentCatalog.SAVED_ITEM_LIMIT - 1);
        deleteSaved(last); // toggleSaved saves a new item through the Activity.
        assertSavedCapacity(ContentCatalog.SAVED_ITEM_LIMIT);
        String stored = preferences.getString(ContentCatalog.STORAGE_KEY, null);
        List<ContentCatalog.Item> before = new ArrayList<>(catalog.savedItems);
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            ((Button) field(filter)).performClick();
            assertSavedCapacity(ContentCatalog.SAVED_ITEM_LIMIT);
            assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            assertEquals(before, catalog.savedItems);
            assertTrue(catalog.isConfirmed(before.get(0)));
            assertNull(field("activeDialog"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
        deleteSaved(last);
        assertSavedCapacity(ContentCatalog.SAVED_ITEM_LIMIT - 1);
        undoDeletion().performClick();
        assertSavedCapacity(ContentCatalog.SAVED_ITEM_LIMIT);
        assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        controller.recreate(); activity = controller.get();
        assertSavedCapacity(ContentCatalog.SAVED_ITEM_LIMIT);
        assertTrue(((Button) field("savedAll")).isSelected());
        for (AccessPolicy.State state : new AccessPolicy.State[]{AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
            setAccess(state);
            assertEquals(View.GONE, ((View) field("content")).getVisibility());
        }
    }

    @Test public void capacityKeepsFiltersButtonsAndUndoUntilUserDeletesAndRetries() throws Exception {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        ContentCatalog.Item target = items.get(0);
        showDetail(items.get(1)); detailAction("confirm-saved").performClick();
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        Object confirmationToken = field("undoToken");
        String narration = ((ContentCatalog.Plan) field("plan")).narration();
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            deleteSaved(target);
            for (int i = catalog.savedItems.size(); i < ContentCatalog.SAVED_ITEM_LIMIT; i++)
                assertTrue(catalog.save(CatalogTest.capacityItem(catalog, i), preferences));
            ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
            ((Button) field(filter)).performClick();
            Button save = ((LinearLayout) field("briefingRows")).findViewWithTag(target.key());
            Button undo = undoDeletion();
            Button confirmation = undoConfirmation();
            String stored = preferences.getString(ContentCatalog.STORAGE_KEY, null);
            assertSavedCapacity(catalog.savedItems.size());
            String heading = ((TextView) field("savedHeading")).getText().toString();
            List<ContentCatalog.Item> before = new ArrayList<>(catalog.savedItems);
            LinearLayout rows = field("savedRows");
            List<View> views = new ArrayList<>();
            for (int i = 0; i < rows.getChildCount(); i++) views.add(rows.getChildAt(i));
            save.performClick();
            assertSavedCapacity(catalog.savedItems.size());
            assertEquals(ContentCatalog.SAVED_CAPACITY_NOTICE.replace(" \u00b7 ", "\n"), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertEquals("저장", save.getText().toString());
            assertEquals(target.title + " 저장", save.getContentDescription().toString());
            assertEquals(before, catalog.savedItems);
            assertEquals(ContentCatalog.SAVED_ITEM_LIMIT - 1, catalog.unreadCount());
            assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            assertEquals(heading, ((TextView) field("savedHeading")).getText().toString());
            assertSavedCapacity(before.size());
            assertTrue(((Button) field(filter)).isSelected());
            assertSame(items.get(2), ReflectionHelpers.callInstanceMethod(activity, "firstUnread"));
            assertFirstUnreadPreview(items.get(2));
            assertEquals(views.size(), rows.getChildCount());
            for (int i = 0; i < views.size(); i++) assertSame(views.get(i), rows.getChildAt(i));
            assertSame(undo, undoDeletion()); assertSame(confirmation, undoConfirmation());
            undo.performClick();
            assertEquals(ContentCatalog.SAVED_CAPACITY_NOTICE.replace(" \u00b7 ", "\n"), undo.getText().toString());
            assertSame(undo, undoDeletion()); assertEquals(before, catalog.savedItems);
            assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            ContentCatalog.Item removed = catalog.savedItems.get(catalog.savedItems.size() - 1);
            deleteSaved(removed);
            assertSavedCapacity(ContentCatalog.SAVED_ITEM_LIMIT - 1);
            ContentCatalog.DeletedItem latestDeleted = field("undoDeleted");
            assertEquals(removed.key(), latestDeleted.item.key());
            Button latest = undoDeletion();
            assertNotSame(undo, latest);
            undo.performClick(); assertFalse(catalog.isSaved(target));
            save = ((LinearLayout) field("briefingRows")).findViewWithTag(target.key());
            save.performClick();
            assertSavedCapacity(catalog.savedItems.size());
            assertEquals(ContentCatalog.SAVED_ITEM_LIMIT, catalog.savedItems.size());
            assertSame(target, catalog.savedItems.get(ContentCatalog.SAVED_ITEM_LIMIT - 1));
            assertSame(latestDeleted, field("undoDeleted"));
            assertEquals(removed.key(), ((ContentCatalog.DeletedItem) field("undoDeleted")).item.key());
            Button currentUndo = undoDeletion();
            assertNotNull(currentUndo);
            assertFalse(catalog.isSaved(removed));
            int targetCount = 0;
            for (ContentCatalog.Item item : catalog.savedItems)
                if (item.key().equals(target.key())) targetCount++;
            assertEquals(1, targetCount);
            List<ContentCatalog.Item> afterRetry = new ArrayList<>(catalog.savedItems);
            String storedAfterRetry = preferences.getString(ContentCatalog.STORAGE_KEY, null);
            currentUndo.performClick();
            assertEquals(ContentCatalog.SAVED_CAPACITY_NOTICE.replace(" \u00b7 ", "\n"), currentUndo.getText().toString());
            undo.performClick(); // The previous deletion's callback has expired.
            assertFalse(catalog.isSaved(removed));
            assertTrue(catalog.isSaved(target));
            assertSame(latestDeleted, field("undoDeleted"));
            assertSame(currentUndo, undoDeletion());
            assertEquals(afterRetry, catalog.savedItems);
            assertEquals(storedAfterRetry, preferences.getString(ContentCatalog.STORAGE_KEY, null));
            assertTrue(catalog.isConfirmed(items.get(1)));
            assertEquals(ContentCatalog.SAVED_ITEM_LIMIT - 1, catalog.unreadCount());
            assertTrue(((Button) field(filter)).isSelected());
            assertSame(items.get(2), ReflectionHelpers.callInstanceMethod(activity, "firstUnread"));
            assertFirstUnreadPreview(items.get(2));
            assertSame(confirmationToken, field("undoToken"));
            assertNull(field("activeDialog"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertEquals(narration, ((ContentCatalog.Plan) field("plan")).narration());
        }
        controller.recreate(); activity = controller.get();
        ContentCatalog restored = field("catalog");
        assertSavedCapacity(restored.savedItems.size());
        assertEquals(ContentCatalog.SAVED_ITEM_LIMIT, restored.savedItems.size());
        for (int i = 0; i < catalog.savedItems.size(); i++)
            assertEquals(catalog.savedItems.get(i).json().toString(), restored.savedItems.get(i).json().toString());
    }

    @Test public void newSaveFailureKeepsFiltersAndUndoUntilSameButtonRetry() throws Exception {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        ContentCatalog.Item target = items.get(0);
        showDetail(items.get(1)); detailAction("confirm-saved").performClick();
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        Object confirmationToken = field("undoToken");
        String narration = ((ContentCatalog.Plan) field("plan")).narration();
        for (String query : new String[]{target.title, "no matching saved title"})
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            applySearch(query);
            deleteSaved(target);
            Button save = ((LinearLayout) field("briefingRows")).findViewWithTag(target.key());
            assertNotNull(save);
            ((Button) field(filter)).performClick();
            Button undo = undoDeletion();
            Button confirmation = undoConfirmation();
            String storage = preferences.getString(ContentCatalog.STORAGE_KEY, null);
            assertSavedCapacity(catalog.savedItems.size());
            String heading = ((TextView) field("savedHeading")).getText().toString();
            List<ContentCatalog.Item> before = new ArrayList<>(catalog.savedItems);
            List<View> rows = new ArrayList<>();
            LinearLayout savedRows = field("savedRows");
            for (int i = 0; i < savedRows.getChildCount(); i++) rows.add(savedRows.getChildAt(i));
            // Both modes write to real preferences before returning false or throwing.
            for (boolean throwsAfterWrite : new boolean[]{false, true}) {
                ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
                save.performClick();
                assertEquals("저장 실패\n다시 시도", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
                assertEquals("저장", save.getText().toString());
                assertEquals(target.title + " 저장", save.getContentDescription().toString());
                assertEquals(before, catalog.savedItems); assertFalse(catalog.isSaved(target));
                assertTrue(catalog.isConfirmed(items.get(1)));
                assertEquals(items.size() - 2, catalog.unreadCount());
                assertEquals(storage, preferences.getString(ContentCatalog.STORAGE_KEY, null));
                assertEquals(heading, ((TextView) field("savedHeading")).getText().toString());
                assertSavedCapacity(before.size());
                assertTrue(((Button) field(filter)).isSelected());
                assertSame(items.get(2), ReflectionHelpers.callInstanceMethod(activity, "firstUnread"));
                assertFirstUnreadPreview(items.get(2));
                assertEquals(rows.size(), savedRows.getChildCount());
                for (int i = 0; i < rows.size(); i++) assertSame(rows.get(i), savedRows.getChildAt(i));
                assertSame(undo, undoDeletion()); assertSame(confirmation, undoConfirmation());
                assertSearchDisplay(query, filter);
            }
            ReflectionHelpers.setField(activity, "preferences", preferences);
            save.performClick();
            assertSavedCapacity(catalog.savedItems.size());
            assertEquals(items.size(), catalog.savedItems.size());
            assertSame(target, catalog.savedItems.get(items.size() - 1));
            assertEquals("저장됨", save.getText().toString());
            assertEquals(target.title + " 저장 취소", save.getContentDescription().toString());
            assertEquals("도착해서 보기\n" + items.size() + "\n미확인 " + (items.size() - 1),
                    ((TextView) field("savedHeading")).getText().toString());
            assertTrue(((Button) field(filter)).isSelected());
            assertFalse(catalog.isConfirmed(target));
            assertSearchDisplay(query, filter);
            assertNull(undoDeletion()); assertNotNull(undoConfirmation());
            assertSame(confirmationToken, field("undoToken"));
            undo.performClick(); assertEquals(items.size(), catalog.savedItems.size());
            assertNull(field("activeDialog"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertEquals(narration, ((ContentCatalog.Plan) field("plan")).narration());
        }
        controller.recreate(); activity = controller.get();
        assertEquals("", (String) field("savedQuery"));
        assertTrue(((Button) field("savedAll")).isSelected());
        ContentCatalog restored = field("catalog");
        assertSavedCapacity(restored.savedItems.size());
        assertEquals(catalog.savedItems.size(), restored.savedItems.size());
        for (int i = 0; i < catalog.savedItems.size(); i++)
            assertEquals(catalog.savedItems.get(i).json().toString(), restored.savedItems.get(i).json().toString());
    }

    @Test public void newSavePreservesOtherDeletionUndoAndRejectsUnsafeCallbacks() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        applySearch(items.get(0).title);
        deleteSaved(items.get(0)); deleteSaved(items.get(1));
        Button save = ((LinearLayout) field("briefingRows")).findViewWithTag(items.get(0).key());
        String stored = preferences.getString(ContentCatalog.STORAGE_KEY, null);
        for (AccessPolicy.State state : new AccessPolicy.State[]{AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
            setAccess(state); save.performClick();
            assertFalse(catalog.isSaved(items.get(0)));
            assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        }
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        controller.pause(); save.performClick();
        assertFalse(catalog.isSaved(items.get(0)));
        assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        controller.resume(); setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertTrue(catalog.save(items.get(1), preferences)); deleteSaved(items.get(1));
        Button undo = undoDeletion();
        save = ((LinearLayout) field("briefingRows")).findViewWithTag(items.get(0).key());
        save.performClick(); assertTrue(catalog.isSaved(items.get(0)));
        assertNotNull(undoDeletion());
        undo.performClick(); assertTrue(catalog.isSaved(items.get(1)));
    }

    private Button undoDeletion() {
        return ((LinearLayout) field("savedUndo")).findViewWithTag("undo-deletion");
    }

    private void deleteSaved(ContentCatalog.Item item) {
        ReflectionHelpers.callInstanceMethod(activity, "toggleSaved",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Item.class, item));
    }

    @Test public void deletionUndoPreservesOtherConfirmationAndHandlesFailuresAndResave() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        showDetail(items.get(0)); detailAction("confirm-saved").performClick();
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        Button confirmation = undoConfirmation();
        String narration = ((ContentCatalog.Plan) field("plan")).narration();
        deleteSaved(items.get(1));
        Button first = undoDeletion(); assertNotNull(first);
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        deleteSaved(items.get(2));
        assertSame(first, undoDeletion()); assertTrue(catalog.isSaved(items.get(2)));
        first.performClick(); assertFalse(catalog.isSaved(items.get(1)));
        assertTrue(first.getText().toString().contains("다시 시도"));
        ReflectionHelpers.setField(activity, "preferences", preferences);
        first.performClick(); assertTrue(catalog.isSaved(items.get(1)));
        assertNotNull(undoConfirmation());
        confirmation.performClick(); assertFalse(catalog.isConfirmed(items.get(0)));
        deleteSaved(items.get(1)); first = undoDeletion();
        deleteSaved(items.get(2)); Button latest = undoDeletion();
        first.performClick(); assertFalse(catalog.isSaved(items.get(1)));
        latest.performClick(); assertTrue(catalog.isSaved(items.get(2)));
        deleteSaved(items.get(0)); Button stale = undoDeletion();
        deleteSaved(items.get(0)); // Re-save invalidates the old callback.
        stale.performClick(); assertNull(undoDeletion()); assertTrue(catalog.isSaved(items.get(0)));
        assertNull(field("activeDialog"));
        assertEquals(narration, ((ContentCatalog.Plan) field("plan")).narration());
    }

    @Test public void deletionUndoUpdatesEveryFilterAndRestoresConfirmedDetailDeletion() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            showDetail(items.get(0)); detailAction("confirm-saved").performClick();
            ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
            ((Button) field(filter)).performClick();
            showDetail(items.get(0));
            ((AlertDialog) field("activeDialog")).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(catalog.isSaved(items.get(0))); assertNull(undoConfirmation());
            assertNotNull(undoDeletion());
            assertEquals(items.size() - 1, catalog.savedItems.size());
            assertEquals(items.size() - 1, catalog.unreadCount());
            assertSame(items.get(1), ReflectionHelpers.callInstanceMethod(activity, "firstUnread"));
            assertFirstUnreadPreview(items.get(1));
            assertDeletionFilterDisplay(filter, items, false);
            undoDeletion().performClick();
            assertTrue(catalog.isConfirmed(items.get(0))); assertNull(undoConfirmation());
            assertEquals(items.size(), catalog.savedItems.size());
            assertEquals(items.size() - 1, catalog.unreadCount());
            assertDeletionFilterDisplay(filter, items, true);
            assertNull(field("activeDialog"));
            assertTrue(catalog.setConfirmed(items.get(0), false, field("preferences")));
        }
    }

    private void assertDeletionFilterDisplay(String filter, List<ContentCatalog.Item> items,
            boolean restored) {
        assertTrue(((Button) field(filter)).isSelected());
        int total = restored ? items.size() : items.size() - 1;
        assertEquals("도착해서 보기\n" + total + "\n미확인 " + (items.size() - 1),
                ((TextView) field("savedHeading")).getText().toString());
        assertTrue(((Button) field("savedFirstUnread")).isEnabled());
        assertEquals(View.GONE, ((TextView) field("savedUnreadEmpty")).getVisibility());
        assertSame(items.get(1), ReflectionHelpers.callInstanceMethod(activity, "firstUnread"));
        assertFirstUnreadPreview(items.get(1));
        LinearLayout rows = field("savedRows");
        if (filter.equals("savedConfirmed") && !restored) {
            // Two empty-state views, while the other saved items remain unread.
            assertEquals(2, rows.getChildCount());
            assertEquals("확인 완료한 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
            assertEquals("전체 보기", ((Button) rows.getChildAt(1)).getText().toString());
            return;
        }
        assertEquals(filter.equals("savedConfirmed") ? 1
                : filter.equals("savedUnread") ? items.size() - 1 : total, rows.getChildCount());
        int row = 0;
        for (int i = 0; i < items.size(); i++) {
            if (i == 0 && (!restored || filter.equals("savedUnread"))) continue;
            if (i != 0 && filter.equals("savedConfirmed")) continue;
            assertEquals(savedRowText(items.get(i)),
                    ((Button) rows.getChildAt(row++)).getText().toString());
        }
        assertFalse(descendants(rows, TextView.class).stream().anyMatch(text ->
                text.getText().toString().equals("확인 완료한 소식이 없어요.")
                        || text.getText().toString().equals("전체 보기")));
    }

    @Test public void deletingLastUnreadAndUndoUpdateEntryAndCountsForEveryFilter() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        for (int i = 1; i < items.size(); i++)
            assertTrue(catalog.setConfirmed(items.get(i), true, field("preferences")));
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            ((Button) field(filter)).performClick();
            deleteSaved(items.get(0));
            assertEquals(0, catalog.unreadCount());
            assertFalse(((Button) field("savedFirstUnread")).isEnabled());
            assertNull(ReflectionHelpers.callInstanceMethod(activity, "firstUnread"));
            assertFirstUnreadPreview(null);
            assertEquals("도착해서 보기\n" + (items.size() - 1) + "\n미확인 0",
                    ((TextView) field("savedHeading")).getText().toString());
            undoDeletion().performClick();
            assertEquals(1, catalog.unreadCount());
            assertTrue(((Button) field("savedFirstUnread")).isEnabled());
            assertSame(items.get(0), ReflectionHelpers.callInstanceMethod(activity, "firstUnread"));
            assertFirstUnreadPreview(items.get(0));
            assertEquals(filter.equals("savedAll") ? items.size() : filter.equals("savedUnread")
                    ? 1 : items.size() - 1, ((LinearLayout) field("savedRows")).getChildCount());
        }
    }

    @Test public void deletionUndoCallbacksExpireAcrossSafetyAndLifecycleTransitions() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        for (int transition = 0; transition < 4; transition++) {
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            deleteSaved(items.get(0)); Button old = undoDeletion(); assertNotNull(old);
            if (transition < 2) setAccess(transition == 0 ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
            else if (transition == 2) controller.pause();
            else { controller.recreate(); activity = controller.get(); }
            old.performClick(); assertNull(undoDeletion());
            if (transition == 2) controller.resume();
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            old.performClick(); assertNull(undoDeletion());
            assertFalse(((ContentCatalog) field("catalog")).isSaved(items.get(0)));
            assertNull(field("activeDialog"));
            deleteSaved(items.get(0));
        }
    }

    private Button undoConfirmation() {
        return ((LinearLayout) field("savedUndo")).findViewWithTag("undo-confirmation");
    }

    private Button searchButton(View view, String text) {
        if (view instanceof Button && text.equals(((Button) view).getText().toString())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = searchButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private AlertDialog searchDraft(String query) {
        Button open = searchButton(activity.getWindow().getDecorView(), "저장한 제목 검색");
        assertNotNull(open);
        open.performClick();
        AlertDialog dialog = field("activeDialog");
        EditText input = dialog.findViewById(android.R.id.content).findViewWithTag("saved-search-input");
        assertEquals("저장한 제목 검색", input.getContentDescription().toString());
        assertEquals((String) field("savedQuery"), input.getText().toString());
        input.setText(query);
        return dialog;
    }

    private void applySearch(String query) {
        searchDraft(query).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void savedTitleSearchMatchesLiteralTitlesAndIntersectsEveryFilter() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        String[] titles = {"  한글 AbC [.*]  ", "한글 AbC [.*]", "요약 아닌 제목"};
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            ContentCatalog.Item item = new ContentCatalog.Item(new org.json.JSONObject()
                    .put("id", "search-" + i).put("sourceId", catalog.sources.get(i).id)
                    .put("title", titles[i]).put("summary", "summary-only"));
            items.add(item);
            catalog.toggleSaved(item);
        }
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(items.get(0), true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        java.util.Map<String, ?> before = preferences.getAll();
        ContentCatalog.Plan plan = field("plan");
        for (String query : new String[]{" 한글 ", " aBc ", "[.*]"}) {
            applySearch(query);
            for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
                ((Button) field(filter)).performClick();
                LinearLayout rows = field("savedRows");
                int expected = filter.equals("savedAll") ? 2 : 1;
                assertEquals(expected, rows.getChildCount());
                assertTrue(((TextView) field("savedSearchStatus")).getText().toString()
                        .contains("표시 " + expected + "건"));
                int first = filter.equals("savedUnread") ? 1 : 0;
                assertEquals(savedRowText(items.get(first)), ((Button) rows.getChildAt(0)).getText().toString());
                ((Button) rows.getChildAt(0)).performClick();
                AlertDialog detail = field("activeDialog");
                assertDetail(items.get(first));
                detail.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            }
        }
        applySearch("summary-only");
        assertEquals("검색 결과가 없어요", ((TextView) ((LinearLayout) field("savedRows"))
                .getChildAt(0)).getText().toString());
        ((Button) field("savedSearchClear")).performClick();
        assertTrue(((Button) field("savedConfirmed")).isSelected());
        assertEquals("", (String) field("savedQuery"));
        applySearch("   ");
        assertEquals("", (String) field("savedQuery"));
        assertEquals(before, preferences.getAll());
        assertEquals(items, catalog.savedItems);
        assertSame(plan, field("plan"));
        assertEquals("도착해서 보기\n3\n미확인 2",
                ((TextView) field("savedHeading")).getText().toString());
    }

    @Test public void savedTitleSearchRecalculatesMutationsAndPreservesUndo() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        applySearch(items.get(0).title);
        ((Button) field("savedUnread")).performClick();
        LinearLayout rows = field("savedRows");
        ((Button) rows.getChildAt(0)).performClick();
        Button confirm = detailAction("confirm-saved");
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        confirm.performClick();
        assertFalse(catalog.isConfirmed(items.get(0)));
        assertEquals(1, rows.getChildCount());
        ReflectionHelpers.setField(activity, "preferences", preferences);
        confirm.performClick();
        assertTrue(catalog.isConfirmed(items.get(0)));
        assertEquals("검색 결과가 없어요", ((TextView) rows.getChildAt(0)).getText().toString());
        Object token = field("undoToken");
        java.util.Map<String, ?> before = preferences.getAll();
        applySearch("no match");
        ((Button) field("savedSearchClear")).performClick();
        assertSame(token, field("undoToken"));
        assertEquals(before, preferences.getAll());
        undoConfirmation().performClick();
        assertFalse(catalog.isConfirmed(items.get(0)));
        applySearch(items.get(0).title);
        ((Button) rows.getChildAt(0)).performClick();
        ((AlertDialog) field("activeDialog")).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(catalog.isSaved(items.get(0)));
        Object deleted = field("undoDeleted");
        applySearch("no match");
        assertSame(deleted, field("undoDeleted"));
        applySearch(items.get(0).title);
        undoDeletion().performClick();
        assertTrue(catalog.isSaved(items.get(0)));
        assertEquals(savedRowText(items.get(0)), ((Button) rows.getChildAt(0)).getText().toString());
    }

    @Test public void savedConditionsResetClearsEveryCombinationWithoutMutatingSavedState() {
        assertResetDisplay(new ArrayList<>());
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(items.get(0), true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        assertResetDisplay(items);
        java.util.Map<String, ?> before = preferences.getAll();
        Object plan = field("plan");
        boolean playing = ((SpeechPlayback) field("speech")).isPlaying();
        for (String query : new String[]{"", items.get(0).title, "no matching saved title"}) {
            for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
                chooseSource(items.get(0).sourceId);
                if (!query.isEmpty()) applySearch(query);
                ((Button) field(filter)).performClick();
                Button reset = searchButton(activity.getWindow().getDecorView(), "조건 모두 지우기");
                assertNotNull(reset);
                assertEquals(View.VISIBLE, reset.getVisibility());
                reset.performClick();
                assertResetDisplay(items);
                assertEquals(before, preferences.getAll());
                assertTrue(catalog.isConfirmed(items.get(0)));
                assertEquals(items.size() - 1, catalog.unreadCount());
                assertSavedCapacity(items.size());
                assertSavedFilterCounts(items.size(), items.size() - 1);
                assertSame(plan, field("plan"));
                assertEquals(playing, ((SpeechPlayback) field("speech")).isPlaying());
                assertNull(field("activeDialog"));
            }
        }
        for (ContentCatalog.Item item : items) deleteSaved(item);
        applySearch("empty library");
        ((Button) field("savedConfirmed")).performClick();
        ((Button) field("savedConditionsClear")).performClick();
        assertResetDisplay(new ArrayList<>());
        assertEquals("저장한 소식이 없어요.", ((TextView) ((LinearLayout) field("savedRows"))
                .getChildAt(0)).getText().toString());
    }

    private void assertResetDisplay(List<ContentCatalog.Item> items) {
        assertNull(field("savedSourceId"));
        assertEquals("", (String) field("savedQuery"));
        assertSavedFilterCounts();
        assertTrue(((Button) field("savedAll")).isSelected());
        assertFalse(((Button) field("savedUnread")).isSelected());
        assertFalse(((Button) field("savedConfirmed")).isSelected());
        for (String name : new String[]{"savedSearchStatus", "savedSearchClear", "savedConditionsClear"})
            assertEquals(View.GONE, ((View) field(name)).getVisibility());
        LinearLayout rows = field("savedRows");
        assertEquals(Math.max(1, items.size()), rows.getChildCount());
        for (int i = 0; i < items.size(); i++)
            assertEquals(savedRowText(items.get(i)), ((Button) rows.getChildAt(i)).getText().toString());
    }

    @Test public void savedConditionsResetPreservesBothUndoActions() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        showDetail(items.get(0));
        detailAction("confirm-saved").performClick();
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        applySearch("no match");
        ((Button) field("savedConfirmed")).performClick();
        Object token = field("undoToken");
        ((Button) field("savedConditionsClear")).performClick();
        assertSame(token, field("undoToken"));
        undoConfirmation().performClick();
        assertFalse(((ContentCatalog) field("catalog")).isConfirmed(items.get(0)));
        deleteSaved(items.get(0));
        Object deleted = field("undoDeleted");
        applySearch("no match");
        ((Button) field("savedUnread")).performClick();
        ((Button) field("savedConditionsClear")).performClick();
        assertSame(deleted, field("undoDeleted"));
        undoDeletion().performClick();
        assertResetDisplay(items);
    }

    @Test public void savedConditionsResetRejectsUnsafeCallbacksAndRecreationDefaults() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        for (int transition = 0; transition < 3; transition++) {
            applySearch("no match");
            ((Button) field("savedConfirmed")).performClick();
            Button retained = field("savedConditionsClear");
            java.util.Map<String, ?> before = ((android.content.SharedPreferences) field("preferences")).getAll();
            if (transition == 2) controller.pause();
            else setAccess(transition == 0 ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
            retained.performClick();
            assertEquals("no match", (String) field("savedQuery"));
            assertTrue(((Button) field("savedConfirmed")).isSelected());
            assertEquals(before, ((android.content.SharedPreferences) field("preferences")).getAll());
            assertEquals(items, ((ContentCatalog) field("catalog")).savedItems);
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertNull(field("activeDialog"));
            if (transition == 2) controller.resume();
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        }
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        List<ContentCatalog.Item> restored = ((ContentCatalog) field("catalog")).savedItems;
        assertEquals(items.size(), restored.size());
        for (int i = 0; i < items.size(); i++) assertEquals(items.get(i).key(), restored.get(i).key());
        assertResetDisplay(restored);
    }

    private void assertSearchDisplay(String query, String filter) {
        ContentCatalog catalog = field("catalog");
        assertEquals(query, (String) field("savedQuery"));
        assertTrue(((Button) field(filter)).isSelected());
        List<ContentCatalog.Item> expected = new ArrayList<>();
        for (ContentCatalog.Item item : catalog.savedItems) {
            if (!item.title.trim().toLowerCase(java.util.Locale.ROOT)
                    .contains(query.trim().toLowerCase(java.util.Locale.ROOT))) continue;
            if (filter.equals("savedUnread") && catalog.isConfirmed(item)) continue;
            if (filter.equals("savedConfirmed") && !catalog.isConfirmed(item)) continue;
            expected.add(item);
        }
        LinearLayout rows = field("savedRows");
        assertEquals(expected.isEmpty() ? (catalog.savedItems.isEmpty() ? 1 : 2) : expected.size(), rows.getChildCount());
        for (int i = 0; i < expected.size(); i++)
            assertEquals(savedRowText(expected.get(i)), ((Button) rows.getChildAt(i)).getText().toString());
        if (expected.isEmpty())
            assertEquals("검색 결과가 없어요", ((TextView) rows.getChildAt(0)).getText().toString());
        assertTrue(((TextView) field("savedSearchStatus")).getText().toString()
                .contains("표시 " + expected.size() + "건"));
        assertEquals("도착해서 보기\n" + catalog.savedItems.size() + "\n미확인 " + catalog.unreadCount(),
                ((TextView) field("savedHeading")).getText().toString());
        assertSavedCapacity(catalog.savedItems.size());
    }

    private Button searchResultDelete(String filter) {
        ((Button) field("savedAll")).performClick();
        ((Button) ((LinearLayout) field("savedRows")).getChildAt(0)).performClick();
        Button remove = ((AlertDialog) field("activeDialog")).getButton(AlertDialog.BUTTON_NEUTRAL);
        ((Button) field(filter)).performClick();
        return remove;
    }

    @Test public void savedTitleSearchDeletionAndRestoreFailuresRetryWithCurrentFilter() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        ContentCatalog.Item target = items.get(0);
        showDetail(target); detailAction("confirm-saved").performClick();
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        deleteSaved(items.get(1)); // A different, valid deletion undo must survive a failed delete.
        String query = target.title;
        applySearch(query);
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            ((Button) field(filter)).performClick();
            List<ContentCatalog.Item> order = new ArrayList<>(catalog.savedItems);
            java.util.Map<String, ?> stored = preferences.getAll();
            Object previousUndo = field("undoDeleted");
            Object confirmation = field("undoToken");
            // failingCommits performs the real write before false/exception in both modes.
            for (boolean throwsAfterWrite : new boolean[]{false, true}) {
                ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
                searchResultDelete(filter).performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertEquals(order, catalog.savedItems);
                assertEquals(stored, preferences.getAll());
                assertTrue(catalog.isConfirmed(target));
                assertSame(previousUndo, field("undoDeleted"));
                assertSame(confirmation, field("undoToken"));
                assertSearchDisplay(query, filter);
            }
            ReflectionHelpers.setField(activity, "preferences", preferences);
            searchResultDelete(filter).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(catalog.isSaved(target));
            assertSearchDisplay(query, filter);
            Button undo = undoDeletion();
            assertNotNull(undo);
            Object deleted = field("undoDeleted");
            List<ContentCatalog.Item> remaining = new ArrayList<>(catalog.savedItems);
            stored = preferences.getAll();
            ContentCatalog.Plan plan = field("plan");
            applySearch("no matching saved title");
            ((Button) field("savedSearchClear")).performClick();
            assertSame(deleted, field("undoDeleted"));
            assertEquals(stored, preferences.getAll());
            assertSame(plan, field("plan"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            applySearch(query);
            undo = undoDeletion();
            for (boolean throwsAfterWrite : new boolean[]{false, true}) {
                ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
                undo.performClick();
                assertEquals(remaining, catalog.savedItems);
                assertEquals(stored, preferences.getAll());
                assertSame(deleted, field("undoDeleted"));
                assertSame(undo, undoDeletion());
                assertSearchDisplay(query, filter);
            }
            ReflectionHelpers.setField(activity, "preferences", preferences);
            undo.performClick();
            assertEquals(order, catalog.savedItems);
            assertTrue(catalog.isConfirmed(target));
            assertNull(undoDeletion());
            assertSearchDisplay(query, filter);
        }
    }

    @Test public void savedTitleSearchDraftAndStaleCallbacksCannotApplyAfterAccessChanges() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        applySearch(items.get(0).title);
        String applied = field("savedQuery");
        AlertDialog cancelled = searchDraft("discard");
        cancelled.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(applied, (String) field("savedQuery"));
        for (int transition = 0; transition < 3; transition++) {
            AlertDialog old = searchDraft("stale");
            Button apply = old.getButton(AlertDialog.BUTTON_POSITIVE);
            if (transition == 2) controller.pause();
            else setAccess(transition == 0 ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
            assertFalse(old.isShowing());
            apply.performClick();
            ((Button) field("savedSearchClear")).performClick();
            searchButton(activity.getWindow().getDecorView(), "저장한 제목 검색").performClick();
            assertNull(field("activeDialog"));
            assertEquals(applied, (String) field("savedQuery"));
            if (transition == 2) controller.resume();
            else setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            apply.performClick();
            assertEquals(applied, (String) field("savedQuery"));
            assertNull(field("activeDialog"));
        }
        controller.recreate();
        activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertEquals("", (String) field("savedQuery"));
        assertTrue(((Button) field("savedAll")).isSelected());
        assertNull(field("activeDialog"));
    }

    private AlertDialog sourceChoices() {
        ((Button) field("savedSource")).performClick();
        AlertDialog dialog = field("activeDialog");
        assertNotNull(dialog);
        return dialog;
    }

    private void assertSourceCounts(int saved, int unread) {
        ContentCatalog catalog = field("catalog");
        assertSavedFilterCounts();
        AlertDialog dialog = sourceChoices();
        android.widget.ListAdapter adapter = dialog.getListView().getAdapter();
        assertEquals("전체 출처\n저장 " + saved + "건\n미확인 " + unread + "건", adapter.getItem(0).toString());
        for (int i = 0; i < catalog.sources.size(); i++) {
            ContentCatalog.Source source = catalog.sources.get(i);
            int sourceSaved = 0, sourceUnread = 0;
            for (ContentCatalog.Item item : catalog.savedItems) if (source.id.equals(item.sourceId)) {
                sourceSaved++;
                if (!catalog.isConfirmed(item)) sourceUnread++;
            }
            String name = source.name;
            if (catalog.sources.stream().filter(other -> other.name.equals(source.name)).count() > 1)
                name += "\n" + source.id;
            assertEquals(name + "\n저장 " + sourceSaved + "건\n미확인 " + sourceUnread + "건",
                    adapter.getItem(i + 1).toString());
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void savedFilterScopeCountsRefreshAfterMutationsFailuresRetriesAndRecreation() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        ContentCatalog.Item target = items.get(0);
        android.content.SharedPreferences preferences = field("preferences");
        int count = items.size();
        chooseSource(target.sourceId);
        applySearch(target.title);
        assertSourceCounts(count, count);
        assertSavedScopeCounts(1, 1);
        for (boolean throwsAfterWrite : new boolean[]{false, true}) {
            showDetail(target);
            ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
            detailAction("confirm-saved").performClick();
            ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
            assertSourceCounts(count, count);
            assertSavedScopeCounts(1, 1);
            ReflectionHelpers.setField(activity, "preferences", preferences);
            showDetail(target);
            detailAction("confirm-saved").performClick();
            assertSourceCounts(count, count - 1);
            assertSavedScopeCounts(1, 0);
            ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
            undoConfirmation().performClick();
            assertSourceCounts(count, count - 1);
            assertSavedScopeCounts(1, 0);
            ReflectionHelpers.setField(activity, "preferences", preferences);
            undoConfirmation().performClick();
            assertSourceCounts(count, count);
            assertSavedScopeCounts(1, 1);
            showDetail(target);
            detailAction("confirm-saved").performClick();
            showDetail(target);
            ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
            detailAction("confirm-saved").performClick();
            assertSourceCounts(count, count - 1);
            assertSavedScopeCounts(1, 0);
            ReflectionHelpers.setField(activity, "preferences", preferences);
            showDetail(target);
            detailAction("confirm-saved").performClick();
            assertSourceCounts(count, count);
            assertSavedScopeCounts(1, 1);
            ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
            deleteSaved(target);
            assertSourceCounts(count, count);
            assertSavedScopeCounts(1, 1);
            ReflectionHelpers.setField(activity, "preferences", preferences);
            deleteSaved(target);
            assertSourceCounts(count - 1, count - 1);
            assertSavedScopeCounts(0, 0);
            ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
            undoDeletion().performClick();
            assertSourceCounts(count - 1, count - 1);
            assertSavedScopeCounts(0, 0);
            ReflectionHelpers.setField(activity, "preferences", preferences);
            undoDeletion().performClick();
            assertSourceCounts(count, count);
            assertSavedScopeCounts(1, 1);
        }
        for (boolean throwsAfterWrite : new boolean[]{false, true}) {
            deleteSaved(target);
            assertSavedScopeCounts(0, 0);
            ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences, throwsAfterWrite));
            ReflectionHelpers.callInstanceMethod(activity, "toggleSaved",
                    ReflectionHelpers.ClassParameter.from(ContentCatalog.Item.class, target));
            assertSourceCounts(count - 1, count - 1);
            assertSavedScopeCounts(0, 0);
            ReflectionHelpers.setField(activity, "preferences", preferences);
            ReflectionHelpers.callInstanceMethod(activity, "toggleSaved",
                    ReflectionHelpers.ClassParameter.from(ContentCatalog.Item.class, target));
            assertSourceCounts(count, count);
            assertSavedScopeCounts(1, 1);
        }
        showDetail(target);
        detailAction("confirm-saved").performClick();
        chooseSource(target.sourceId);
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertNull(field("savedSourceId"));
        assertEquals("", (String) field("savedQuery"));
        assertSourceCounts(count, count - 1);
        assertSavedScopeCounts(count, count - 1);
    }

    private void chooseSource(String id) {
        ContentCatalog catalog = field("catalog");
        String query = field("savedQuery");
        Object filter = field("savedFilter");
        AlertDialog dialog = sourceChoices();
        int index = 0;
        if (id != null) {
            for (int i = 0; i < catalog.sources.size(); i++)
                if (id.equals(catalog.sources.get(i).id)) index = i + 1;
            assertTrue(index > 0);
        }
        dialog.getListView().performItemClick(null, index, index);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(id, (String) field("savedSourceId"));
        assertEquals(query, (String) field("savedQuery"));
        assertSame(filter, field("savedFilter"));
        assertNull(field("activeDialog"));
    }

    @Test public void savedFilterScopeChoicesIntersectSearchAndStatusWithoutMutations() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ContentCatalog.Source source = new ContentCatalog.Source(new org.json.JSONObject()
                    .put("id", "source-fixture-" + i).put("name", "동명 출처").put("enabled", false));
            catalog.sources.add(source);
            if (i == 2) continue;
            for (int j = 0; j < 2; j++) {
                ContentCatalog.Item item = new ContentCatalog.Item(new org.json.JSONObject()
                        .put("id", "item-" + j).put("sourceId", source.id).put("title", "  한글 AbC [.*]  "));
                catalog.toggleSaved(item);
                items.add(item);
            }
        }
        android.content.SharedPreferences preferences = field("preferences");
        catalog.persist(preferences);
        assertTrue(catalog.setConfirmed(items.get(0), true, preferences));
        assertTrue(catalog.setConfirmed(items.get(2), true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        java.util.Map<String, ?> before = preferences.getAll();
        Object plan = field("plan");
        boolean playing = ((SpeechPlayback) field("speech")).isPlaying();
        AlertDialog choices = sourceChoices();
        assertEquals(catalog.sources.size() + 1, choices.getListView().getAdapter().getCount());
        assertEquals("동명 출처\nsource-fixture-0\n저장 2건\n미확인 1건", choices.getListView().getAdapter()
                .getItem(catalog.sources.size() - 2).toString());
        ReflectionHelpers.callInstanceMethod(activity, "dismissDialog");
        for (int source = -1; source < 3; source++) {
            chooseSource(source < 0 ? null : "source-fixture-" + source);
            for (String query : new String[]{"", "한글", " aBc ", "[.*]", "no match"}) {
                applySearch(query);
                for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
                    ((Button) field(filter)).performClick();
                    assertSourceCounts(4, 2);
                    int total = source == 2 || query.equals("no match") ? 0 : source < 0 ? 4 : 2;
                    assertSavedScopeCounts(total, total / 2);
                    int count = filter.equals("savedAll") ? total : total / 2;
                    assertTrue(((TextView) field("savedSearchStatus")).getText().toString()
                            .contains("필터 건수는 선택한 출처와 검색어 기준이에요."));
                    LinearLayout rows = field("savedRows");
                    assertEquals(count == 0 ? 2 : count, rows.getChildCount());
                    assertTrue(((TextView) field("savedSearchStatus")).getText().toString().contains("표시 " + count + "건"));
                    assertTrue(((TextView) field("savedSearchStatus")).getText().toString().contains("전체 저장 순서"));
                    int row = 0;
                    if (count > 0) for (int index = 0; index < items.size(); index++) {
                        if (source >= 0 && index / 2 != source) continue;
                        if (filter.equals("savedUnread") && index % 2 == 0) continue;
                        if (filter.equals("savedConfirmed") && index % 2 != 0) continue;
                        assertEquals(savedRowText(items.get(index)),
                                ((Button) rows.getChildAt(row++)).getText().toString());
                    }
                    assertEquals(count, row);
                }
            }
            ((Button) field("savedSearchClear")).performClick();
            assertEquals(source < 0 ? null : "source-fixture-" + source, (String) field("savedSourceId"));
            ((Button) field("savedConditionsClear")).performClick();
            assertNull(field("savedSourceId"));
            assertResetDisplay(items);
        }
        chooseSource("source-fixture-2");
        searchButton((View) field("savedRows"), "전체 보기").performClick();
        assertNull(field("savedSourceId"));
        assertResetDisplay(items);
        assertEquals(before, preferences.getAll());
        assertEquals(items, catalog.savedItems);
        assertSavedFilterCounts(4, 2);
        assertSavedCapacity(4);
        assertSame(plan, field("plan"));
        assertEquals(playing, ((SpeechPlayback) field("speech")).isPlaying());
    }

    @Test public void savedFilterScopeEmptyLibraryAndReset() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        applySearch("한글 [.*]");
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            ((Button) field(filter)).performClick();
            assertSavedScopeCounts(0, 0);
            LinearLayout rows = field("savedRows");
            assertEquals(1, rows.getChildCount());
            assertEquals("저장한 소식이 없어요.", ((TextView) rows.getChildAt(0)).getText().toString());
        }
        ((Button) field("savedConditionsClear")).performClick();
        assertResetDisplay(new ArrayList<>());
        assertSavedScopeCounts(0, 0);
    }

    private static final String CLEAR_SOURCE_A = "sample-game";
    private static final String CLEAR_SOURCE_B = "sample-schedule";
    private static final String CLEAR_SOURCE_EMPTY = "sample-community";

    private List<ContentCatalog.Item> sourceClearFixture() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        // Use valid sample sources so the catalog restores this fixture after recreation.
        for (String id : new String[]{CLEAR_SOURCE_A, CLEAR_SOURCE_B, CLEAR_SOURCE_EMPTY}) {
            ContentCatalog.Source source = catalog.source(id);
            assertNotNull(source);
            catalog.sources.set(catalog.sources.indexOf(source), new ContentCatalog.Source(
                    source.json().put("name", "동명 출처").put("enabled", false)));
        }
        String[][] values = {{"a1", CLEAR_SOURCE_A, "  Alpha one  "},
                {"b1", CLEAR_SOURCE_B, "ALPHA two"}, {"b2", CLEAR_SOURCE_B, "alpha three"},
                {"a2", CLEAR_SOURCE_A, "Beta four"}, {"b3", CLEAR_SOURCE_B, "Beta five"}};
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (String[] value : values) {
            ContentCatalog.Item item = new ContentCatalog.Item(new org.json.JSONObject()
                    .put("id", value[0]).put("sourceId", value[1]).put("title", value[2])
                    .put("summary", "테스트 요약").put("estimatedSeconds", 10).put("provenance", "demo_sample"));
            catalog.toggleSaved(item);
            items.add(item);
        }
        catalog.persist(field("preferences"));
        assertTrue(catalog.setConfirmed(items.get(1), true, field("preferences")));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        return items;
    }

    private void assertSourceClearRows(List<ContentCatalog.Item> items, int... indices) {
        LinearLayout rows = field("savedRows");
        assertEquals(indices.length, rows.getChildCount());
        for (int i = 0; i < indices.length; i++)
            assertEquals(savedRowText(items.get(indices[i])), ((Button) rows.getChildAt(i)).getText().toString());
    }

    private void clearSourceWithoutMutations() {
        ContentCatalog catalog = field("catalog");
        List<ContentCatalog.Item> order = new ArrayList<>(catalog.savedItems);
        List<Boolean> confirmed = new ArrayList<>();
        for (ContentCatalog.Item item : order) confirmed.add(catalog.isConfirmed(item));
        android.content.SharedPreferences preferences = field("preferences");
        java.util.Map<String, ?> stored = preferences.getAll();
        String query = field("savedQuery");
        Object filter = field("savedFilter"), plan = field("plan");
        Object deleted = field("undoDeleted"), confirmation = field("undoConfirmed"), token = field("undoToken");
        boolean playing = ((SpeechPlayback) field("speech")).isPlaying();
        Button clear = field("savedSourceClear");
        assertEquals(View.VISIBLE, clear.getVisibility());
        assertEquals("출처만 지우기", clear.getText().toString());
        assertEquals(clear.getText(), clear.getContentDescription());
        LinearLayout panel = (LinearLayout) clear.getParent();
        assertSame(clear, panel.getChildAt(panel.indexOfChild(field("savedSource")) + 1));
        clear.performClick();
        assertNull(field("savedSourceId"));
        assertNull(field("activeDialog"));
        assertEquals(View.GONE, clear.getVisibility());
        assertEquals("출처\n전체 출처", ((Button) field("savedSource")).getText().toString());
        assertEquals(query, (String) field("savedQuery"));
        assertSame(filter, field("savedFilter"));
        assertSame(plan, field("plan"));
        assertSame(deleted, field("undoDeleted"));
        assertSame(confirmation, field("undoConfirmed"));
        assertSame(token, field("undoToken"));
        assertEquals(playing, ((SpeechPlayback) field("speech")).isPlaying());
        assertEquals(stored, preferences.getAll());
        assertEquals(order, catalog.savedItems);
        for (int i = 0; i < order.size(); i++) assertEquals(confirmed.get(i), Boolean.valueOf(catalog.isConfirmed(order.get(i))));
    }

    @Test public void savedSourceClearPreservesEachStatusAndNormalizedSearch() throws Exception {
        List<ContentCatalog.Item> items = sourceClearFixture();
        String[] filters = {"savedAll", "savedUnread", "savedConfirmed"};
        int[][] before = {{0}, {0}, {}}, after = {{0, 1, 2}, {0, 2}, {1}};
        for (int i = 0; i < filters.length; i++) {
            chooseSource(CLEAR_SOURCE_A);
            applySearch(" aLpHa ");
            ((Button) field(filters[i])).performClick();
            assertSavedScopeCounts(1, 1);
            if (i < 2) assertSourceClearRows(items, before[i]);
            else assertTrue(((TextView) field("savedSearchStatus")).getText().toString().contains("표시 0건"));
            clearSourceWithoutMutations();
            assertSavedScopeCounts(3, 2);
            assertSourceClearRows(items, after[i]);
            assertEquals(View.VISIBLE, ((TextView) field("savedSearchStatus")).getVisibility());
            assertTrue(((TextView) field("savedSearchStatus")).getText().toString().contains("필터 건수는 선택한 출처와 검색어 기준이에요."));
            chooseSource(CLEAR_SOURCE_EMPTY);
            assertSavedScopeCounts(0, 0);
            clearSourceWithoutMutations();
            assertSourceClearRows(items, after[i]);
            applySearch("missing");
            chooseSource(CLEAR_SOURCE_B);
            assertSavedScopeCounts(0, 0);
            clearSourceWithoutMutations();
            assertSavedScopeCounts(0, 0);
            assertEquals("검색 결과가 없어요", ((TextView) ((LinearLayout) field("savedRows")).getChildAt(0)).getText().toString());
        }
        chooseSource(CLEAR_SOURCE_A);
        ((Button) field("savedSearchClear")).performClick();
        assertEquals(CLEAR_SOURCE_A, (String) field("savedSourceId"));
        assertTrue(((Button) field("savedConfirmed")).isSelected());
        clearSourceWithoutMutations();
        assertSavedScopeCounts(5, 4);
        assertEquals(View.GONE, ((TextView) field("savedSearchStatus")).getVisibility());
        assertSourceClearRows(items, 1);
        assertSourceCounts(5, 4);
        assertSavedCapacity(5);
        ((Button) field("savedFirstUnread")).performClick();
        assertDetail(items.get(0));
    }

    @Test public void savedSourceClearPreservesUndoTargets() throws Exception {
        List<ContentCatalog.Item> items = sourceClearFixture();
        ContentCatalog catalog = field("catalog");
        showDetail(items.get(0));
        detailAction("confirm-saved").performClick();
        chooseSource(CLEAR_SOURCE_A);
        clearSourceWithoutMutations();
        undoConfirmation().performClick();
        assertFalse(catalog.isConfirmed(items.get(0)));
        assertTrue(catalog.isConfirmed(items.get(1)));
        deleteSaved(items.get(2));
        chooseSource(CLEAR_SOURCE_EMPTY);
        clearSourceWithoutMutations();
        undoDeletion().performClick();
        assertEquals(items, catalog.savedItems);
    }

    @Test public void savedSourceClearRejectsUnsafeClicksAndResetsOnRecreation() throws Exception {
        List<ContentCatalog.Item> items = sourceClearFixture();
        chooseSource(CLEAR_SOURCE_A);
        applySearch("alpha");
        ((Button) field("savedUnread")).performClick();
        for (int transition = 0; transition < 3; transition++) {
            Button retained = field("savedSourceClear");
            java.util.Map<String, ?> stored = ((android.content.SharedPreferences) field("preferences")).getAll();
            Object filter = field("savedFilter");
            if (transition == 2) controller.pause();
            else setAccess(transition == 0 ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
            retained.performClick();
            assertEquals(CLEAR_SOURCE_A, (String) field("savedSourceId"));
            assertEquals("alpha", (String) field("savedQuery"));
            assertSame(filter, field("savedFilter"));
            assertEquals(stored, ((android.content.SharedPreferences) field("preferences")).getAll());
            assertEquals(items, ((ContentCatalog) field("catalog")).savedItems);
            if (transition == 2) controller.resume();
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            assertEquals(CLEAR_SOURCE_A, (String) field("savedSourceId"));
            assertNull(field("activeDialog"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertResetDisplay(items);
        assertTrue(((ContentCatalog) field("catalog")).isConfirmed(items.get(1)));
        assertEquals(View.GONE, ((Button) field("savedSourceClear")).getVisibility());
        chooseSource(CLEAR_SOURCE_A);
        chooseSource(null);
        assertEquals(View.GONE, ((Button) field("savedSourceClear")).getVisibility());
        chooseSource(CLEAR_SOURCE_A);
        applySearch("alpha");
        ((Button) field("savedConditionsClear")).performClick();
        assertResetDisplay(items);
        assertEquals(View.GONE, ((Button) field("savedSourceClear")).getVisibility());
    }

    @Test public void savedSourceClearEmptyArchive() {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            chooseSource(catalog.sources.get(0).id);
            applySearch("missing");
            ((Button) field(filter)).performClick();
            clearSourceWithoutMutations();
            assertSavedScopeCounts(0, 0);
            assertEquals("저장한 소식이 없어요.", ((TextView) ((LinearLayout) field("savedRows")).getChildAt(0)).getText().toString());
        }
    }

    @Test public void savedSourceDeletionUndoAndNavigationUseWholeSavedOrder() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        chooseSource(items.get(1).sourceId);
        ((Button) field("savedFirstUnread")).performClick();
        assertDetail(items.get(0));
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        Button confirmNext = detailAction("confirm-next");
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        confirmNext.performClick();
        assertFalse(catalog.isConfirmed(items.get(0)));
        assertDetail(items.get(0));
        ReflectionHelpers.setField(activity, "preferences", preferences);
        confirmNext.performClick();
        assertDetail(items.get(1));
        AlertDialog detail = field("activeDialog");
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        detail.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(catalog.isSaved(items.get(1)));
        ReflectionHelpers.setField(activity, "preferences", preferences);
        deleteSaved(items.get(1));
        assertEquals(items.get(1).sourceId, (String) field("savedSourceId"));
        assertTrue(((TextView) field("savedSearchStatus")).getText().toString().contains("표시 0건"));
        Button undo = undoDeletion();
        ReflectionHelpers.setField(activity, "preferences", CatalogTest.failingCommits(preferences));
        undo.performClick();
        assertFalse(catalog.isSaved(items.get(1)));
        assertEquals(items.get(1).sourceId, (String) field("savedSourceId"));
        ReflectionHelpers.setField(activity, "preferences", preferences);
        undo.performClick();
        assertEquals(items, catalog.savedItems);
        showDetail(items.get(1));
        detailAction("next-unread").performClick();
        assertDetail(items.get(2));
    }

    @Test public void savedSourceEmptyArchiveAndCapacityShortcutReset() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        String sourceId = catalog.sources.get(0).id;
        assertSourceCounts(0, 0);
        chooseSource(sourceId);
        assertEquals(View.VISIBLE, ((Button) field("savedConditionsClear")).getVisibility());
        assertTrue(((TextView) field("savedSearchStatus")).getText().toString().contains("표시 0건"));
        ((Button) field("savedConditionsClear")).performClick();
        assertResetDisplay(new ArrayList<>());
        android.content.SharedPreferences preferences = field("preferences");
        for (int i = 0; i < ContentCatalog.SAVED_ITEM_LIMIT; i++) catalog.toggleSaved(CatalogTest.capacityItem(catalog, i));
        assertTrue(catalog.setConfirmed(catalog.savedItems.get(0), true, preferences));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        chooseSource(sourceId);
        applySearch("no match");
        ((Button) field("savedCapacityConfirmed")).performClick();
        assertNull(field("savedSourceId"));
        assertEquals("", (String) field("savedQuery"));
        assertTrue(((Button) field("savedConfirmed")).isSelected());
        assertEquals(1, ((LinearLayout) field("savedRows")).getChildCount());
    }

    @Test public void savedSourceRejectsUnsafeAndDismissedCallbacksAndRecreatesAtAll() {
        List<ContentCatalog.Item> items = saveNavigationItems();
        chooseSource(items.get(0).sourceId);
        for (int transition = 0; transition < 3; transition++) {
            AlertDialog dialog = sourceChoices();
            Button retained = field("savedSource");
            java.util.Map<String, ?> before = ((android.content.SharedPreferences) field("preferences")).getAll();
            if (transition == 2) controller.pause();
            else setAccess(transition == 0 ? AccessPolicy.State.RESTRICTED : AccessPolicy.State.UNKNOWN);
            retained.performClick();
            dialog.getListView().performItemClick(null, 0, 0);
            assertNull(field("activeDialog"));
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            assertEquals(items.get(0).sourceId, (String) field("savedSourceId"));
            if (transition == 2) controller.resume();
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            dialog.getListView().performItemClick(null, 0, 0);
            assertEquals(items.get(0).sourceId, (String) field("savedSourceId"));
            assertEquals(before, ((android.content.SharedPreferences) field("preferences")).getAll());
        }
        controller.recreate(); activity = controller.get();
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        assertNull(field("savedSourceId"));
        chooseSource(((ContentCatalog) field("catalog")).sources.get(0).id);
        ((Button) field("savedConditionsClear")).performClick();
        assertNull(field("savedSourceId"));
    }

    // Three explicit identities exercise ordering; this fixture size is not a product limit.
    private List<ContentCatalog.Item> confirmNextFinishFixture(int count) throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        catalog.savedItems.clear();
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ContentCatalog.Item sample = catalog.sources.get(i).items.get(0);
            ContentCatalog.Item item = new ContentCatalog.Item(sample.json()
                    .put("id", "finish-" + i).put("title", "같은 제목").put("summary", "finish summary " + i));
            items.add(item);
            catalog.toggleSaved(item);
        }
        catalog.persist(field("preferences"));
        ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
        return items;
    }

    private void assertConfirmNextFinish(String label, ContentCatalog.Item candidate) {
        Button button = detailAction("confirm-next");
        assertEquals(label, button.getText().toString());
        assertNull(button.getContentDescription()); // Accessible name follows current text, including retry.
        assertEquals(candidate != null, detailAction("next-unread").isEnabled());
        assertPreview(candidate);
        AlertDialog dialog = field("activeDialog");
        assertEquals(candidate == null, descendants(dialog.getWindow().getDecorView(), TextView.class)
                .stream().anyMatch(v -> v.getVisibility() == View.VISIBLE
                        && v.getText().toString().equals("다른 미확인 소식이 없어요.")));
    }

    @Test public void confirmNextFinishSingleFailureRetryAndUndo() throws Exception {
        ContentCatalog.Item item = confirmNextFinishFixture(1).get(0);
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        for (boolean throwsAfterWrite : new boolean[]{false, true}) {
            showDetail(item);
            assertConfirmNextFinish("확인하고 마치기", null);
            Button action = detailAction("confirm-next");
            AlertDialog dialog = field("activeDialog");
            java.util.Map<String, ?> stored = preferences.getAll();
            List<ContentCatalog.Item> order = new ArrayList<>(catalog.savedItems);
            Button undo = undoConfirmation();
            ReflectionHelpers.setField(activity, "preferences",
                    CatalogTest.failingCommits(preferences, throwsAfterWrite));
            action.performClick();
            assertEquals("저장 실패\n다시 시도", action.getText().toString());
            assertNull(action.getContentDescription());
            assertSame(dialog, field("activeDialog"));
            assertFalse(catalog.isConfirmed(item));
            assertEquals(order, catalog.savedItems);
            assertEquals(stored, preferences.getAll());
            assertSame(undo, undoConfirmation());
            ReflectionHelpers.setField(activity, "preferences", preferences);
            action.performClick();
            assertTrue(catalog.isConfirmed(item));
            assertNull(field("activeDialog"));
            undoConfirmation().performClick();
            assertFalse(catalog.isConfirmed(item));
            assertNull(field("activeDialog"));
            showDetail(item);
            assertConfirmNextFinish("확인하고 마치기", null);
        }
    }

    @Test public void confirmNextFinishUsesGlobalOrderAndRefreshesOnReopenAndClick() throws Exception {
        List<ContentCatalog.Item> items = confirmNextFinishFixture(3);
        ContentCatalog.Item first = items.get(0), middle = items.get(1), last = items.get(2);
        ContentCatalog catalog = field("catalog");
        android.content.SharedPreferences preferences = field("preferences");
        assertTrue(catalog.setConfirmed(middle, true, preferences));
        for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
            ((Button) field(filter)).performClick();
            ReflectionHelpers.setField(activity, "savedQuery", "no matching title");
            ReflectionHelpers.setField(activity, "savedSourceId", middle.sourceId);
            ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
            showDetail(last);
            assertConfirmNextFinish("확인하고 다음", first); // Wrap to distinct key with identical title.
        }
        detailAction("confirm-next").performClick();
        assertDetail(first);
        assertConfirmNextFinish("확인하고 마치기", null); // All other entries confirmed.
        assertTrue(catalog.setConfirmed(last, false, preferences));
        showDetail(first);
        assertConfirmNextFinish("확인하고 다음", last);
        assertTrue(catalog.setConfirmed(last, true, preferences));
        showDetail(first);
        assertConfirmNextFinish("확인하고 마치기", null);
        Button finish = detailAction("confirm-next");
        assertTrue(catalog.setConfirmed(middle, false, preferences));
        finish.performClick(); // Opening label is a snapshot; click rechecks candidates.
        assertDetail(middle);
        assertConfirmNextFinish("확인하고 마치기", null);
        showDetail(first);
        assertNull(detailAction("confirm-next"));
    }

    private void assertFinishFixtureUnchanged(String stage, ContentCatalog.Item item,
            java.util.Map<String, ?> stored) {
        ContentCatalog catalog = field("catalog");
        assertEquals(stage, 1, catalog.savedItems.size());
        assertEquals(stage, item.key(), catalog.savedItems.get(0).key());
        assertFalse(stage, catalog.isConfirmed(item));
        assertEquals(stage, stored, ((android.content.SharedPreferences) field("preferences")).getAll());
    }

    @Test public void confirmNextFinishRejectsUnsafeAndStaleReferences() throws Exception {
        ContentCatalog.Item item = confirmNextFinishFixture(1).get(0);
        for (String transition : new String[]{"restricted", "unknown", "pause", "recreate"}) {
            showDetail(item);
            assertConfirmNextFinish("확인하고 마치기", null);
            Button stale = detailAction("confirm-next");
            ContentCatalog previousCatalog = field("catalog");
            java.util.Map<String, ?> stored = ((android.content.SharedPreferences) field("preferences")).getAll();
            if (transition.equals("restricted")) setAccess(AccessPolicy.State.RESTRICTED);
            else if (transition.equals("unknown")) setAccess(AccessPolicy.State.UNKNOWN);
            else if (transition.equals("pause")) controller.pause();
            else { controller.recreate(); activity = controller.get(); }
            assertFinishFixtureUnchanged(transition + " before stale click", item, stored);
            stale.performClick();
            assertFalse(transition, previousCatalog.isConfirmed(item));
            assertFinishFixtureUnchanged(transition + " after stale click", item, stored);
            assertNull(field("activeDialog"));
            if (transition.equals("pause")) controller.resume();
            setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
            assertFinishFixtureUnchanged(transition + " after parked transition", item, stored);
            stale.performClick();
            assertFalse(transition, previousCatalog.isConfirmed(item));
            assertFinishFixtureUnchanged(transition + " after parked stale click", item, stored);
            assertNull(field("activeDialog"));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse(((SpeechPlayback) field("speech")).isPlaying());
        }
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Item restored = catalog.savedItems.get(0);
        showDetail(restored);
        assertConfirmNextFinish("확인하고 마치기", null);
        detailAction("confirm-next").performClick();
        assertTrue(catalog.isConfirmed(restored));
        assertNull(field("activeDialog"));
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

    @Test public void savedMetadataUsesStoredProvenanceAndOptionalTimeWithoutMutations() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Source named = catalog.sources.get(0);
        ContentCatalog.Source blank = new ContentCatalog.Source(new org.json.JSONObject()
                .put("id", "blank-fixture").put("name", " "));
        catalog.sources.add(blank);
        String[] kinds = {"demo_sample", "official_snapshot", "official_runtime", "unknown"};
        String[] labels = {"예시 콘텐츠", "공식 소식\n스냅샷 자료", "공식 소식\n직접 갱신 자료", "자료 구분 확인 필요"};
        for (int i = 0; i < kinds.length; i++) for (boolean timed : new boolean[]{false, true}) {
            String sourceId = i == 0 ? named.id : i == 1 ? blank.id : "missing-fixture";
            ContentCatalog.Item item = new ContentCatalog.Item(new org.json.JSONObject()
                    .put("id", kinds[i] + timed).put("sourceId", sourceId).put("title", "같은 제목")
                    .put("summary", "저장된 요약").put("provenance", kinds[i])
                    .put("fetchedAt", timed ? "2026-01-02T03:04:05Z" : org.json.JSONObject.NULL));
            catalog.toggleSaved(item);
            android.content.SharedPreferences preferences = field("preferences");
            catalog.persist(preferences);
            java.util.Map<String, ?> before = preferences.getAll();
            List<ContentCatalog.Item> order = new ArrayList<>(catalog.savedItems);
            ContentCatalog.Plan plan = field("plan");
            for (String filter : new String[]{"savedAll", "savedUnread", "savedConfirmed"}) {
                ((Button) field(filter)).performClick();
                showDetail(item);
                assertMetadata(item, "출처\n" + (i == 0 ? named.name : "출처 확인 필요") + "\n" + labels[i]
                        + (i == 1 || i == 2 ? "\n가져온 시각(UTC)\n"
                        + (timed ? "2026-01-02T03:04:05Z" : "가져온 시각 미제공") : ""));
                assertEquals(before, preferences.getAll());
                assertEquals(order, catalog.savedItems);
                assertFalse(catalog.isConfirmed(item));
                assertSame(plan, field("plan"));
                assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
                assertFalse(((SpeechPlayback) field("speech")).isPlaying());
            }
        }
    }

    @Test public void savedMetadataSurvivesFixtureRefreshFailureAndRecreation() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Source source = catalog.add("공식 출처 fixture", "lol",
                "https://www.leagueoflegends.com/ko-kr/news/game-updates/");
        // Local fixture input only: no network request or real SDK callback.
        ContentCatalog.Item saved = new ContentCatalog.Item(source.items.get(0).json()
                .put("provenance", "official_snapshot").put("fetchedAt", "2026-01-02T03:04:05Z"));
        catalog.toggleSaved(saved);
        ContentCatalog.Item sameTitle = new ContentCatalog.Item(catalog.sources.get(0).items.get(0).json()
                .put("title", saved.title));
        catalog.toggleSaved(sameTitle);
        String expected = saved.json().toString();
        String metadata = "출처\n" + source.name + "\n공식 소식\n스냅샷 자료\n가져온 시각(UTC)\n2026-01-02T03:04:05Z";
        for (boolean failure : new boolean[]{false, true}) {
            assertTrue(catalog.importSnapshot(source.id, new org.json.JSONObject().put("items",
                    new org.json.JSONArray().put(saved.json().put("summary", "갱신 fixture 요약"))).toString(),
                    java.time.Instant.parse("2026-02-03T04:05:06Z"), true));
            if (failure) catalog.markFailure(source.id, java.time.Instant.parse("2026-02-04T04:05:06Z"));
            catalog.persist(field("preferences"));
            for (boolean recreate : new boolean[]{false, true}) {
                if (recreate) {
                    controller.recreate(); activity = controller.get();
                    setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
                    catalog = field("catalog");
                    assertTrue(((Button) field("savedAll")).isSelected());
                }
                ContentCatalog.Item restored = catalog.savedItems.get(0);
                assertEquals(expected, restored.json().toString());
                ReflectionHelpers.callInstanceMethod(activity, "renderSaved");
                LinearLayout rows = field("savedRows");
                Button first = (Button) rows.getChildAt(0), second = (Button) rows.getChildAt(1);
                assertEquals(savedRowText(restored), first.getText().toString());
                assertEquals(savedRowText(catalog.savedItems.get(1)), second.getText().toString());
                assertNotEquals(first.getText().toString(), second.getText().toString());
                java.util.Map<String, ?> stored = ((android.content.SharedPreferences) field("preferences")).getAll();
                first.performClick();
                assertDetail(restored);
                assertMetadata(restored, metadata);
                ((AlertDialog) field("activeDialog")).dismiss();
                second.performClick();
                assertDetail(catalog.savedItems.get(1));
                assertEquals(stored, ((android.content.SharedPreferences) field("preferences")).getAll());
                assertMetadata(catalog.savedItems.get(1), "출처\n"
                        + catalog.source(sameTitle.sourceId).name + "\n예시 콘텐츠");
            }
        }
    }

    @Test public void savedRowsDistinguishSourcesAcrossFiltersWithoutMutations() throws Exception {
        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
        ContentCatalog catalog = field("catalog");
        String longText = new String(new char[40]).replace("\0", "긴 제목과 출처 ");
        String[] names = {"첫 출처", "다른 출처", " ", longText};
        List<ContentCatalog.Item> items = new ArrayList<>();
        for (int i = 0; i <= names.length; i++) {
            String sourceId = "saved-row-fixture-" + i;
            if (i < names.length) catalog.sources.add(new ContentCatalog.Source(new org.json.JSONObject()
                    .put("id", sourceId).put("name", names[i])));
            ContentCatalog.Item item = new ContentCatalog.Item(new org.json.JSONObject()
                    .put("id", "row-" + i).put("sourceId", sourceId)
                    .put("title", i == 3 ? longText : "같은 제목")
                    .put("summary", "항목별 요약 " + i).put("originalUrl", "https://example.org/article/" + i)
                    .put("provenance", "demo_sample"));
            items.add(item);
            catalog.toggleSaved(item);
        }
        assertNotEquals(items.get(0).key(), items.get(1).key());
        android.content.SharedPreferences preferences = field("preferences");
        for (boolean confirmed : new boolean[]{false, true}) {
            for (ContentCatalog.Item item : items) catalog.setConfirmed(item, confirmed, preferences);
            catalog.persist(preferences);
            {
                java.util.Map<String, ?> stored = preferences.getAll();
                List<ContentCatalog.Item> order = new ArrayList<>(catalog.savedItems);
                ContentCatalog.Plan plan = field("plan");
                for (String filter : new String[]{"savedAll", confirmed ? "savedConfirmed" : "savedUnread"}) {
                    ((Button) field(filter)).performClick();
                    LinearLayout rows = field("savedRows");
                    assertEquals(items.size(), rows.getChildCount());
                    assertNotEquals(((Button) rows.getChildAt(0)).getText().toString(),
                            ((Button) rows.getChildAt(1)).getText().toString());
                    for (int i = 0; i < order.size(); i++) {
                        Button row = (Button) rows.getChildAt(i);
                        assertEquals(savedRowText(order.get(i)), row.getText().toString());
                        assertEquals(row.getText().toString(), row.getContentDescription().toString());
                        assertNull(row.getEllipsize());
                        assertEquals(Integer.MAX_VALUE, row.getMaxLines());
                        row.performClick();
                        assertDetail(order.get(i));
                        ((AlertDialog) field("activeDialog")).dismiss();
                        for (AccessPolicy.State blocked : new AccessPolicy.State[]{
                                AccessPolicy.State.RESTRICTED, AccessPolicy.State.UNKNOWN}) {
                            setAccess(blocked);
                            row.performClick();
                            AlertDialog blockedDialog = field("activeDialog");
                            assertTrue(blockedDialog == null || !blockedDialog.isShowing());
                        }
                        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
                        controller.pause();
                        row.performClick();
                        AlertDialog active = field("activeDialog");
                        assertTrue(active == null || !active.isShowing());
                        controller.resume();
                        setAccess(AccessPolicy.State.PARKED_USE_ALLOWED);
                    }
                    assertEquals(stored, preferences.getAll());
                    assertEquals(order, catalog.savedItems);
                    assertEquals(confirmed ? 0 : items.size(), catalog.unreadCount());
                    assertSame(plan, field("plan"));
                    assertSavedFilterCounts();
                    assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
                    assertFalse(((SpeechPlayback) field("speech")).isPlaying());
                }
            }
        }
    }

    private String savedRowText(ContentCatalog.Item item) {
        ContentCatalog catalog = field("catalog");
        ContentCatalog.Source source = catalog.source(item.sourceId);
        String name = source == null || source.name.trim().isEmpty() ? "출처 확인 필요" : source.name;
        return item.title + (catalog.isConfirmed(item) ? "\n확인 완료" : "\n미확인") + "\n출처\n" + name;
    }

    private void assertMetadata(ContentCatalog.Item item, String expected) {
        AlertDialog dialog = field("activeDialog");
        TextView metadata = dialog.findViewById(android.R.id.custom).findViewWithTag("saved-detail-metadata");
        assertEquals(expected, metadata.getText().toString());
        assertNull(metadata.getEllipsize());
        assertEquals(Integer.MAX_VALUE, metadata.getMaxLines());
        assertFalse(metadata.isClickable());
        assertNull(metadata.getContentDescription()); // Accessibility reads the complete text.
    }

    private void showDetail(ContentCatalog.Item item) {
        ReflectionHelpers.callInstanceMethod(activity, "showSavedDetail",
                ReflectionHelpers.ClassParameter.from(ContentCatalog.Item.class, item));
    }

    private Button detailAction(String tag) {
        AlertDialog dialog = field("activeDialog");
        return dialog.findViewById(android.R.id.custom).findViewWithTag(tag);
    }

    private void assertPreview(ContentCatalog.Item expected) {
        AlertDialog dialog = field("activeDialog");
        TextView preview = dialog.findViewById(android.R.id.custom).findViewWithTag("next-unread-preview");
        assertEquals(expected == null ? View.GONE : View.VISIBLE, preview.getVisibility());
        assertEquals(expected == null ? "" : "다음 미확인\n" + expected.title, preview.getText().toString());
        assertNull(preview.getEllipsize());
        assertEquals(Integer.MAX_VALUE, preview.getMaxLines());
        assertFalse(preview.isClickable());
        assertNull(preview.getContentDescription()); // Accessibility reads the full TextView text.
    }

    private void assertDetail(ContentCatalog.Item item) {
        AlertDialog dialog = field("activeDialog");
        List<TextView> texts = descendants(dialog.getWindow().getDecorView(), TextView.class);
        assertTrue(texts.stream().anyMatch(view -> view.getText().toString().equals(item.title)));
        assertTrue(texts.stream().anyMatch(view -> view.getText().toString().equals(item.summary + "\n\n"
                + (item.originalUrl == null ? "예시 콘텐츠" : item.originalUrl) + "\n\n" + ContentCatalog.LOCAL_NOTICE.replace(" \u00b7 ", "\n"))));
        ContentCatalog catalog = field("catalog");
        int index = -1;
        for (int i = 0; i < catalog.savedItems.size(); i++) {
            if (catalog.savedItems.get(i).key().equals(item.key())) { index = i; break; }
        }
        ContentCatalog.Source source = catalog.source(item.sourceId);
        String name = source == null || source.name.trim().isEmpty() ? "출처 확인 필요" : source.name;
        String kind = "demo_sample".equals(item.provenance) ? "예시 콘텐츠"
                : "official_snapshot".equals(item.provenance) ? "공식 소식\n스냅샷 자료"
                : "official_runtime".equals(item.provenance) ? "공식 소식\n직접 갱신 자료" : "자료 구분 확인 필요";
        boolean official = "official_snapshot".equals(item.provenance) || "official_runtime".equals(item.provenance);
        assertMetadata(item, "출처\n" + name + "\n" + kind + (official ? "\n가져온 시각(UTC)\n"
                + (item.fetchedAt == null ? "가져온 시각 미제공" : item.fetchedAt.toString()) : ""));
        TextView position = dialog.findViewById(android.R.id.custom).findViewWithTag("saved-detail-position");
        assertEquals("전체 저장 " + catalog.savedItems.size() + "건 중 " + (index + 1)
                + "번째\n미확인 " + catalog.unreadCount() + "건", position.getText().toString());
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
