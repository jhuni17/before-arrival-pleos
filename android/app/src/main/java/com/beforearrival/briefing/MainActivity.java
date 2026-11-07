package com.beforearrival.briefing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import java.io.ByteArrayOutputStream;
import org.json.JSONException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.beforearrival.briefing.ContentCatalog.Item;
import com.beforearrival.briefing.ContentCatalog.Source;
import com.beforearrival.briefing.ContentCatalog.Provider;
import com.beforearrival.briefing.AccessPolicy.State;

public final class MainActivity extends Activity {
    private int BACKGROUND, SURFACE, BORDER, TEXT, MUTED, ACCENT, ON_ACCENT;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(SampleRouteInput.SEOUL);

    private final SampleRouteInput sampleRoute = new SampleRouteInput();
    private boolean liveMode;
    private final NavigationRouteInput navigationRoute = new NavigationRouteInput(this::onNavigationChanged);
    private RouteInput routeInput = sampleRoute;
    private RouteConnection liveConnection;
    private ContentCatalog catalog;
    private LinearLayout sourceRows;
    private final Map<String, Integer> refreshGenerations = new HashMap<>();
    private int lifecycleGeneration;
    interface FeedLoader { String load(String registration) throws Exception; }
    private FeedLoader feedLoader = ContentFeedClient::fetch;
    private SharedPreferences preferences;
    private AlertDialog activeDialog;
    private ContentCatalog.Plan plan;
    private SpeechPlayback speech;
    private CarRestrictions restrictions;
    private State restrictionState = State.UNKNOWN;
    private boolean automotive;
    private boolean resumed;
    private boolean transcriptVisible;
    private boolean unreadOnly;
    private Button savedAll;
    private Button savedUnread;

    private LinearLayout content;
    private LinearLayout briefingRows;
    private LinearLayout savedRows;
    private TextView restrictionNotice;
    private TextView eta;
    private TextView accessLabel;
    private TextView nextItem;
    private Button routeMode;
    private TextView routeLabel;
    private TextView summary;
    private TextView transcript;
    private TextView speechStatus;
    private TextView savedHeading;
    private Button etaLong;
    private Button etaShort;
    private Button priorityButton;
    private Button listen;
    private Button transcriptButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        automotive = getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        configurePalette();
        liveMode = state == null ? LiveConnectionFactory.isSdkIncluded()
                : state.getBoolean("live_mode", LiveConnectionFactory.isSdkIncluded());
        routeInput = liveMode ? navigationRoute : sampleRoute;
        if (state != null) sampleRoute.select(state.getInt("sample_eta", 18));
        preferences = getSharedPreferences("briefing", MODE_PRIVATE);
        try (InputStream input = getAssets().open("catalog.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096]; int count;
            while ((count = input.read(chunk)) != -1) bytes.write(chunk, 0, count);
            catalog = new ContentCatalog(new String(bytes.toByteArray(), StandardCharsets.UTF_8), preferences);
        } catch (Exception error) {
            throw new IllegalStateException("공유 소식 목록을 읽지 못했어요", error);
        }
        buildScreen();
        speech = new SpeechPlayback(this, this::canInteract, this::renderSpeechStatus);
        render();
        renderAccess();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (liveMode) connectLive();
        if (!automotive) return;
        try {
            restrictions = new CarRestrictions(state -> {
                restrictionState = state;
                renderAccess();
            });
            restrictions.connect(this);
        } catch (LinkageError error) {
            restrictionState = State.UNKNOWN;
            renderAccess();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        renderAccess();
    }

    @Override
    protected void onPause() {
        resumed = false;
        lifecycleGeneration++;
        dismissDialog();
        speech.stop();
        renderSpeechStatus("");
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (liveConnection != null) {
            liveConnection.close();
            liveConnection = null;
        }
        if (restrictions != null) {
            restrictions.close();
            restrictions = null;
        }
        restrictionState = State.UNKNOWN;
        renderAccess();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("sample_eta", (int) (sampleRoute.snapshot().remainingSeconds.orElse(1080) / 60));
        state.putBoolean("live_mode", liveMode);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        speech.close();
        super.onDestroy();
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = safeInsets(insets);
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll);

        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        frame.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = Math.min(right - left, dp(880));
            if (width > 0 && content.getLayoutParams().width != width) {
                content.getLayoutParams().width = width;
                content.requestLayout();
            }
        });
        restrictionNotice = label("", 20, TEXT, true);
        restrictionNotice.setGravity(Gravity.CENTER);
        restrictionNotice.setPadding(dp(24), dp(24), dp(24), dp(24));
        frame.addView(restrictionNotice, new FrameLayout.LayoutParams(-1, -1));

        add(content, label("정차용 브리핑 · 출처를 확인하는 소식", 18, MUTED, true), 0);
        add(content, heading("도착 전에", 34), 4);
        accessLabel = label("", 18, MUTED, false);
        add(content, accessLabel, 4);

        LinearLayout route = panel();
        add(content, route, 20);
        add(route, label("도착까지", 20, MUTED, true), 0);
        eta = label("", 48, TEXT, true);
        add(route, eta, 2);
        routeLabel = label("", 18, MUTED, false);
        add(route, routeLabel, 2);
        etaLong = button("18분", false, () -> selectRoute(18));
        etaShort = button("4분", false, () -> selectRoute(4));
        routeMode = button("", false, () -> {
            if (!canInteract()) return;
            liveMode = !liveMode;
            routeInput = liveMode ? navigationRoute : sampleRoute;
            if (liveConnection != null) { liveConnection.close(); liveConnection = null; }
            if (liveMode) connectLive();
            refreshPlan();
        });

        LinearLayout briefing = panel();
        add(content, briefing, 12);
        add(briefing, heading("이번에 들을 소식", 20), 0);
        nextItem = heading("", 28);
        add(briefing, nextItem, 8);
        summary = label("", 14, MUTED, false);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(briefing, summary, 4);
        listen = button("브리핑 듣기", true, this::togglePlayback);
        transcriptButton = button("대본 보기", false, () -> {
            if (!canInteract()) return;
            transcriptVisible = !transcriptVisible;
            renderTranscript();
        });
        add(briefing, listen, 16);
        add(briefing, transcriptButton, 8);
        speechStatus = label("", 14, ACCENT, false);
        speechStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(briefing, speechStatus, 4);
        transcript = label("", 16, TEXT, false);
        add(briefing, transcript, 6);
        briefingRows = column();
        add(briefing, briefingRows, 4);

        LinearLayout settings = panel();
        add(content, settings, 12);
        add(settings, heading("내 브리핑 설정", 21), 0);
        add(settings, buttonRow(etaLong, etaShort), 8);
        add(settings, routeMode, 8);
        add(settings, button("실제 경로 다시 연결", false, () -> {
            if (canInteract() && liveMode) connectLive();
        }), 8);
        add(settings, label(ContentCatalog.LOCAL_NOTICE, 13, MUTED, false), 8);
        sourceRows = column();
        add(settings, sourceRows, 8);
        priorityButton = button("출처 추가", false, this::showAddSource);
        add(settings, priorityButton, 8);

        LinearLayout savedPanel = panel();
        add(content, savedPanel, 12);
        savedHeading = heading("", 21);
        add(savedPanel, savedHeading, 0);
        savedAll = button("전체", false, () -> selectSavedFilter(false));
        savedUnread = button("미확인만", false, () -> selectSavedFilter(true));
        add(savedPanel, buttonRow(savedAll, savedUnread), 8);
        savedRows = column();
        add(savedPanel, savedRows, 4);
        add(savedPanel, label(ContentCatalog.LOCAL_NOTICE, 13, MUTED, false), 8);
        add(content, label("샘플과 가져온 공식 소식은 각 출처에서 구분해 표시해요.", 13, MUTED, false), 14);

        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private Insets safeInsets(WindowInsets insets) {
        return Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout())
                : insets.getSystemWindowInsets();
    }

    private void selectRoute(int minutes) {
        if (!canInteract() || liveMode) return;
        sampleRoute.select(minutes);
        refreshPlan();
    }

    private void onNavigationChanged(RouteInput.Snapshot snapshot) {
        if (!liveMode || speech == null) return;
        ContentCatalog.Plan updated = catalog.plan(snapshot);
        if (plan == null || updated.budgetSeconds != plan.budgetSeconds || !updated.selected.equals(plan.selected)) {
            refreshPlan();
        } else {
            render();
        }
    }

    private void connectLive() {
        if (liveConnection != null) liveConnection.close();
        liveConnection = LiveConnectionFactory.create(this, navigationRoute);
        liveConnection.start();
    }

    private void showAddSource() {
        if (!canInteract()) return;
        LinearLayout form = column();
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText name = new EditText(this); name.setHint("출처 이름 (최대 80자)"); name.setSingleLine(true);
        EditText url = new EditText(this); url.setHint("공식 HTTPS 주소"); url.setSingleLine(true);
        Spinner provider = new Spinner(this);
        List<Provider> options = new ArrayList<>();
        for (Provider entry : catalog.providers) if (!"sample".equals(entry.id)) options.add(entry);
        provider.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!canInteract()) return;
                Provider selected = options.get(position);
                url.setText(selected.suggestedUrl);
                if (name.getText().toString().isEmpty()) name.setText(selected.label);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        add(form, name, 0); add(form, provider, 8); add(form, url, 8);
        TextView error = label("", 14, ACCENT, false); add(form, error, 4);
        add(form, label(ContentCatalog.LOCAL_NOTICE, 13, MUTED, false), 8);
        showDialog(new AlertDialog.Builder(this).setTitle("소식 출처 추가").setView(form)
                .setPositiveButton("추가", null).setNegativeButton("닫기", null));
        AlertDialog dialog = activeDialog;
        if (dialog == null) return;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!canInteract()) return;
            try {
                Provider selected = (Provider) provider.getSelectedItem();
                if (selected == null) return;
                catalog.add(name.getText().toString(), selected.id, url.getText().toString());
                catalog.persist(preferences); dialog.dismiss(); refreshPlan();
            } catch (IllegalArgumentException | JSONException invalid) { error.setText(invalid.getMessage()); }
        });
    }

    private void renderSources() {
        sourceRows.removeAllViews();
        for (Source source : catalog.sources) {
            CheckBox check = new CheckBox(this);
            check.setText(source.name); check.setTextColor(TEXT); check.setTextSize(20); check.setMinHeight(dp(56));
            check.setTypeface(getResources().getFont(R.font.asta_sans)); check.setChecked(source.enabled);
            check.setTag("enabled:" + source.id);
            check.setOnCheckedChangeListener((view, checked) -> {
                if (!canInteract()) { check.setChecked(source.enabled); return; }
                source.enabled = checked;
                refreshGenerations.put(source.id, refreshGenerations.getOrDefault(source.id, 0) + 1);
                catalog.persist(preferences); refreshPlan();
            });
            add(sourceRows, check, 8);
            add(sourceRows, label(source.statusLabel(), 13, MUTED, false), 0);
            if (source.registrationUrl != null) add(sourceRows, label(source.registrationUrl, 13, MUTED, false), 2);
            if (source.lastAttemptAt != null) add(sourceRows, label("가져오기 확인 " + source.lastAttemptAt, 13, MUTED, false), 2);
            if (source.lastSuccessAt != null) add(sourceRows, label("마지막 성공 " + source.lastSuccessAt, 13, MUTED, false), 2);
            Button up = button("위로", false, () -> moveSource(source, -1)); up.setTag("up:" + source.id);
            Button down = button("아래로", false, () -> moveSource(source, 1)); down.setTag("down:" + source.id);
            enableButton(up, catalog.sources.indexOf(source) > 0);
            enableButton(down, catalog.sources.indexOf(source) < catalog.sources.size() - 1);
            add(sourceRows, buttonRow(up, down), 4);
            if ("steam".equals(source.provider)) {
                Button refresh = button("공식 소식 가져오기", false, () -> refreshSource(source));
                refresh.setTag("refresh:" + source.id); enableButton(refresh, source.enabled); add(sourceRows, refresh, 4);
            }
        }
    }

    private void moveSource(Source source, int direction) {
        if (!canInteract()) return;
        catalog.move(source.id, direction); catalog.persist(preferences); refreshPlan();
    }

    private Thread refreshSource(Source source) {
        if (!canInteract() || !source.enabled || catalog.source(source.id) != source) return null;
        int generation = refreshGenerations.getOrDefault(source.id, 0) + 1;
        int lifecycle = lifecycleGeneration;
        refreshGenerations.put(source.id, generation);
        Thread worker = new Thread(() -> {
            String result = null;
            try { result = feedLoader.load(source.registrationUrl); } catch (Exception failed) { /* Recorded below. */ }
            String snapshot = result;
            runOnUiThread(() -> {
                if (!canInteract() || !source.enabled || catalog.source(source.id) != source || lifecycle != lifecycleGeneration
                        || refreshGenerations.getOrDefault(source.id, 0) != generation) return;
                Instant now = Instant.now();
                if (snapshot == null) catalog.markFailure(source.id, now);
                else catalog.importSnapshot(source.id, snapshot, now);
                catalog.persist(preferences); refreshPlan();
            });
        }, "official-news");
        worker.start(); return worker;
    }

    private void showDialog(AlertDialog.Builder builder) {
        if (!canInteract()) return;
        dismissDialog();
        AlertDialog dialog = builder.create();
        activeDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) activeDialog = null;
        });
        dialog.show();
    }

    private void dismissDialog() {
        AlertDialog dialog = activeDialog;
        activeDialog = null;
        if (dialog != null) dialog.dismiss();
    }

    private void togglePlayback() {
        if (!canInteract() || plan.selected.isEmpty()) return;
        if (speech.isPlaying()) speech.stop();
        else speech.play(plan.narration(), plan.budgetSeconds);
    }

    private void refreshPlan() {
        speech.stop();
        transcriptVisible = false;
        render();
    }

    private void render() {
        RouteInput.Snapshot route = routeInput.snapshot();
        long seconds = route.remainingSeconds.orElse(-1);
        long minutes = seconds < 0 ? -1 : seconds / 60;
        eta.setText(seconds >= 0 ? (seconds == 0 ? "도착" : seconds < 60 ? "1분 미만" : minutes + "분") : "도착 시간 미확인");
        routeMode.setText(liveMode ? "샘플 경로로 전환" : "실제 경로로 전환");
        etaLong.setVisibility(liveMode ? View.GONE : View.VISIBLE);
        etaShort.setVisibility(liveMode ? View.GONE : View.VISIBLE);
        routeLabel.setText(route.observedAt.equals(Instant.EPOCH) ? route.sourceLabel
                : getString(R.string.route_observation, route.sourceLabel, CLOCK.format(route.observedAt)));
        setRouteButton(etaLong, minutes == 18);
        setRouteButton(etaShort, minutes == 4);
        plan = catalog.plan(route);
        renderSources();
        nextItem.setText(plan.selected.isEmpty() ? "브리핑을 기다리고 있어요" : plan.selected.get(0).title);
        if (catalog.sources.stream().noneMatch(source -> source.enabled)) {
            summary.setText("브리핑에 넣을 소식 출처를 선택해 주세요.");
        } else if (minutes < 0) {
            summary.setText("남은 시간을 확인하면 브리핑을 준비할 수 있어요.");
        } else if (plan.selected.isEmpty()) {
            summary.setText(getString(R.string.budget_empty, plan.budgetSeconds));
        } else {
            summary.setText(getString(R.string.budget_selected, plan.budgetSeconds, plan.selected.size(), plan.selectedSeconds));
        }
        renderRows(route);
        renderSaved();
        renderSpeechStatus("");
        renderTranscript();
    }

    private void renderAccess() {
        boolean allowed = canUse();
        accessLabel.setText(allowed ? "주차 시 사용 가능 · 차량 UX 제한 해제" : "차량 상태 미확인");
        content.setVisibility(allowed ? View.VISIBLE : View.GONE);
        restrictionNotice.setVisibility(allowed ? View.GONE : View.VISIBLE);
        if (!allowed) {
            lifecycleGeneration++;
            dismissDialog();
            transcriptVisible = false;
            renderTranscript();
            speech.stop();
            restrictionNotice.setText(restrictionState == State.RESTRICTED
                    ? "주행 제한 · 화면과 음성을 중지했어요.\n정차 후 브리핑을 열어 주세요."
                    : "차량 상태 미확인 · 화면과 음성을 중지했어요.\n차량의 사용 가능 상태를 확인하면 브리핑을 열 수 있어요.");
        }
        renderSpeechStatus("");
    }

    private boolean canUse() {
        return restrictionState == State.PARKED_USE_ALLOWED;
    }

    private boolean canInteract() {
        return AccessPolicy.canInteract(resumed, restrictionState);
    }

    private void renderRows(RouteInput.Snapshot route) {
        briefingRows.removeAllViews();
        for (Item item : plan.selected) {
            String detail = item.summary;
            if (item.urgent(route.observedAt)) detail += " · 마감 임박으로 먼저 듣기";
            add(briefingRows, itemRow(item, detail), 8);
        }
        List<Item> deferred = plan.deferred;
        if (!deferred.isEmpty()) {
            add(briefingRows, heading("나중에 듣기 · " + deferred.size() + "건", 16), 12);
            for (Item item : deferred) add(briefingRows, itemRow(item, item.summary), 8);
        }
    }

    private View itemRow(Item item, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout text = column();
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Source source = catalog.source(item.sourceId);
        add(text, label((source == null ? item.sourceId : source.name) + " · 약 " + item.estimatedSeconds + "초", 13, ACCENT, true), 0);
        add(text, label(item.title, 16, TEXT, true), 2);
        add(text, label(source != null && "steam".equals(source.provider) ? "원문 발췌 · " + detail : detail, 13, MUTED, false), 2);
        add(text, label("게시 " + (item.publishedAt == null ? "시각 미제공" : item.publishedAt.toString()), 13, MUTED, false), 2);
        add(text, label(source == null ? "출처 확인 필요" : source.statusLabel(), 13, MUTED, false), 2);
        if (item.fetchedAt != null) add(text, label("가져온 시각 " + item.fetchedAt, 13, MUTED, false), 2);
        if (item.originalUrl != null) {
            add(text, label(item.originalUrl, 13, MUTED, false), 2);
            if (catalog.canOpen(item)) add(text, button("공식 원문 열기", false, () -> openOriginal(item)), 4);
        }
        Button save = button("", false, () -> toggleSaved(item));
        updateSaveButton(save, item);
        save.setTag(item.key());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(8);
        row.addView(save, params);
        return row;
    }

    private void toggleSaved(Item item) {
        if (!canInteract()) return;
        catalog.toggleSaved(item); catalog.persist(preferences);
        Button save = briefingRows.findViewWithTag(item.key());
        if (save != null) updateSaveButton(save, item);
        renderSaved();
    }

    private void updateSaveButton(Button button, Item item) {
        boolean selected = catalog.isSaved(item);
        button.setText(selected ? "저장됨" : "저장");
        button.setContentDescription(item.title + (selected ? " 저장 취소" : " 저장"));
    }

    private void selectSavedFilter(boolean unread) {
        if (!canInteract()) return;
        unreadOnly = unread;
        renderSaved();
    }

    private void renderSaved() {
        setRouteButton(savedAll, !unreadOnly);
        setRouteButton(savedUnread, unreadOnly);
        savedAll.setContentDescription("전체" + (!unreadOnly ? " 선택됨" : ""));
        savedUnread.setContentDescription("미확인만" + (unreadOnly ? " 선택됨" : ""));
        savedHeading.setText(getString(R.string.saved_heading, catalog.savedItems.size()) + " · 미확인 " + catalog.unreadCount());
        savedRows.removeAllViews();
        if (catalog.savedItems.isEmpty()) {
            add(savedRows, label("저장한 소식이 없어요.", 14, MUTED, false), 4);
        }
        if (unreadOnly && !catalog.savedItems.isEmpty() && catalog.unreadCount() == 0) {
            add(savedRows, label("미확인 소식이 없어요.", 14, MUTED, false), 4);
            add(savedRows, button("전체 보기", false, () -> selectSavedFilter(false)), 6);
        }
        for (Item item : catalog.savedItems) {
            if (unreadOnly && catalog.isConfirmed(item)) continue;
            Button entry = button(item.title + (catalog.isConfirmed(item) ? " · 확인 완료" : " · 미확인"),
                    false, () -> showSavedDetail(item));
            add(savedRows, entry, 6);
        }
    }

    private Item nextUnread(Item current) {
        int currentIndex = -1;
        for (int i = 0; i < catalog.savedItems.size(); i++) {
            if (catalog.savedItems.get(i).key().equals(current.key())) { currentIndex = i; break; }
        }
        if (currentIndex < 0) return null;
        for (int offset = 1; offset < catalog.savedItems.size(); offset++) {
            Item candidate = catalog.savedItems.get((currentIndex + offset) % catalog.savedItems.size());
            if (!candidate.key().equals(current.key()) && !catalog.isConfirmed(candidate)) return candidate;
        }
        return null;
    }

    private void showSavedDetail(Item item) {
        if (!canInteract() || !catalog.isSaved(item)) return;
        int generation = lifecycleGeneration;
        boolean target = !catalog.isConfirmed(item);
        int savedIndex = -1;
        for (int i = 0; i < catalog.savedItems.size(); i++) {
            if (catalog.savedItems.get(i).key().equals(item.key())) { savedIndex = i; break; }
        }
        LinearLayout actions = column();
        TextView progress = label("전체 저장 " + catalog.savedItems.size() + "건 중 "
                + (savedIndex + 1) + "번째 · 미확인 " + catalog.unreadCount() + "건", 14, MUTED, false);
        progress.setTag("saved-detail-position");
        add(actions, progress, 4);
        Button confirm = button(target ? "확인 완료" : "미확인으로 되돌리기", false, () -> {});
        confirm.setTag("confirm-saved");
        add(actions, confirm, 8);
        Button confirmNext = button("확인하고 다음", false, () -> {});
        confirmNext.setTag("confirm-next");
        if (target) add(actions, confirmNext, 8);
        Button next = button("다음 미확인", false, () -> {});
        next.setTag("next-unread");
        add(actions, next, 8);
        TextView empty = label("다른 미확인 소식이 없어요.", 14, MUTED, false);
        add(actions, empty, 4);
        boolean hasNext = nextUnread(item) != null;
        enableButton(next, hasNext);
        empty.setVisibility(hasNext ? View.GONE : View.VISIBLE);
        AlertDialog.Builder detail = new AlertDialog.Builder(this)
            .setTitle(item.title)
            .setView(actions)
            .setMessage(item.summary + "\n\n" + (item.originalUrl == null ? "예시 콘텐츠" : item.originalUrl) + "\n\n" + ContentCatalog.LOCAL_NOTICE)
            .setNegativeButton("닫기", null)
            .setNeutralButton("저장 취소", (dialog, which) -> toggleSaved(item));
        if (catalog.canOpen(item)) detail.setPositiveButton("공식 원문 열기", (dialog, which) -> openOriginal(item));
        showDialog(detail);
        AlertDialog shown = activeDialog;
        confirmNext.setOnClickListener(view -> {
            if (!canInteract() || generation != lifecycleGeneration || activeDialog != shown
                    || shown == null || !shown.isShowing() || !catalog.isSaved(item)
                    || catalog.isConfirmed(item)) return;
            if (!catalog.setConfirmed(item, true, preferences)) {
                confirmNext.setText("저장 실패 · 다시 시도");
                return;
            }
            Item candidate = nextUnread(item);
            renderSaved();
            if (candidate == null) dismissDialog();
            else showSavedDetail(candidate);
        });
        next.setOnClickListener(view -> {
            if (!canInteract() || generation != lifecycleGeneration || activeDialog != shown
                    || shown == null || !shown.isShowing() || !catalog.isSaved(item)) return;
            Item candidate = nextUnread(item);
            if (candidate == null) {
                enableButton(next, false);
                empty.setVisibility(View.VISIBLE);
                return;
            }
            showSavedDetail(candidate);
        });
        confirm.setOnClickListener(view -> {
            if (!canInteract() || generation != lifecycleGeneration || activeDialog != shown
                    || shown == null || !shown.isShowing() || !catalog.isSaved(item)) return;
            if (!catalog.setConfirmed(item, target, preferences)) {
                confirm.setText("저장 실패 · 다시 시도");
                return;
            }
            renderSaved();
            dismissDialog();
        });
    }

    private void openOriginal(Item item) {
        if (!canInteract() || !catalog.canOpen(item)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.originalUrl))); }
        catch (android.content.ActivityNotFoundException unavailable) { renderSpeechStatus("원문을 열 앱이 없어요."); }
    }

    private void renderSpeechStatus(String message) {
        boolean playing = speech.isPlaying();
        listen.setText(playing ? "듣기 중지" : "브리핑 듣기");
        enableButton(listen, canInteract() && !plan.selected.isEmpty());
        speechStatus.setText(message);
        speechStatus.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderTranscript() {
        enableButton(transcriptButton, !plan.selected.isEmpty());
        transcriptButton.setText(transcriptVisible ? "대본 접기" : "대본 보기");
        transcript.setText(plan.narration());
        transcript.setVisibility(transcriptVisible && !plan.selected.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setRouteButton(Button button, boolean selected) {
        button.setSelected(selected);
        button.setTextColor(selected ? ON_ACCENT : TEXT);
        button.setBackground(buttonBackground(selected));
    }

    private void enableButton(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.48f);
    }

    private LinearLayout buttonRow(Button first, Button second) {
        LinearLayout row = new LinearLayout(this);
        row.addView(first, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(8);
        row.addView(second, params);
        return row;
    }

    private Button button(String text, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(20);
        button.setTypeface(getResources().getFont(R.font.asta_sans));
        button.setTextColor(primary ? ON_ACCENT : TEXT);
        button.setMinimumHeight(dp(56));
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setBackground(buttonBackground(primary));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private RippleDrawable buttonBackground(boolean primary) {
        int ripple = primary ? 0x330D1624 : 0x55FFFFFF;
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                shape(primary ? ACCENT : SURFACE, primary ? ACCENT : BORDER, 10), null);
    }

    private GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), border);
        return shape;
    }

    private LinearLayout panel() {
        LinearLayout panel = column();
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(shape(SURFACE, BORDER, 16));
        return panel;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView heading(String text, int size) {
        TextView heading = label(text, size, TEXT, true);
        heading.setAccessibilityHeading(true);
        return heading;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(Math.max(18, size));
        view.setTypeface(getResources().getFont(R.font.asta_sans));
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private void configurePalette() {
        BACKGROUND = getColor(R.color.app_background);
        SURFACE = getColor(R.color.app_surface);
        BORDER = getColor(R.color.app_border);
        TEXT = getColor(R.color.app_text);
        MUTED = getColor(R.color.app_text_secondary);
        ACCENT = getColor(R.color.app_button);
        ON_ACCENT = getColor(R.color.app_on_button);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
