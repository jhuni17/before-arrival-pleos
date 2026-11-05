package com.beforearrival.briefing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.beforearrival.briefing.Briefing.Item;
import com.beforearrival.briefing.CarRestrictions.State;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(13, 22, 36);
    private static final int SURFACE = Color.rgb(24, 37, 54);
    private static final int BORDER = Color.rgb(57, 76, 96);
    private static final int TEXT = Color.rgb(243, 247, 250);
    private static final int MUTED = Color.rgb(187, 202, 216);
    private static final int ACCENT = Color.rgb(114, 228, 221);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(SampleRouteInput.SEOUL);

    private final SampleRouteInput sampleRoute = new SampleRouteInput();
    private final RouteInput routeInput = sampleRoute;
    private final EnumSet<Item> enabled = EnumSet.noneOf(Item.class);
    private final EnumSet<Item> saved = EnumSet.noneOf(Item.class);
    private SharedPreferences preferences;
    private AlertDialog activeDialog;
    private Item priority = Item.SCHEDULE;
    private Briefing.Plan plan;
    private SpeechPlayback speech;
    private CarRestrictions restrictions;
    private State restrictionState = State.UNKNOWN;
    private boolean automotive;
    private boolean resumed;
    private boolean transcriptVisible;

    private LinearLayout content;
    private LinearLayout briefingRows;
    private LinearLayout savedRows;
    private TextView restrictionNotice;
    private TextView eta;
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
        if (state != null) sampleRoute.select(state.getInt("sample_eta", 18));
        preferences = getSharedPreferences("briefing", MODE_PRIVATE);
        for (Item item : Item.values()) {
            if (preferences.getBoolean("enabled_" + item.name(), true)) enabled.add(item);
            if (preferences.getStringSet("saved", Collections.emptySet()).contains(item.name())) saved.add(item);
            if (item.name().equals(preferences.getString("priority", ""))) priority = item;
        }
        buildScreen();
        speech = new SpeechPlayback(this, this::renderSpeechStatus);
        render();
        renderAccess();
    }

    @Override
    protected void onStart() {
        super.onStart();
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
        dismissDialog();
        speech.stop();
        renderSpeechStatus("");
        super.onPause();
    }

    @Override
    protected void onStop() {
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
        state.putInt("sample_eta", sampleRoute.snapshot().remainingMinutes.orElse(18));
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

        add(content, label("BEFORE ARRIVAL  /  정차용 데모", 12, ACCENT, true), 0);
        add(content, heading("도착 전에", 34), 4);
        add(content, label("남은 시간에 맞춰 듣는 내 출근길 브리핑", 16, MUTED, false), 4);

        LinearLayout route = panel();
        add(content, route, 20);
        add(route, label("목적지까지", 13, MUTED, true), 0);
        eta = label("", 34, ACCENT, true);
        add(route, eta, 2);
        routeLabel = label("", 14, MUTED, false);
        add(route, routeLabel, 2);
        etaLong = button("18분", false, () -> selectRoute(18));
        etaShort = button("4분", false, () -> selectRoute(4));
        add(route, buttonRow(etaLong, etaShort), 8);

        LinearLayout briefing = panel();
        add(content, briefing, 12);
        add(briefing, heading("오늘 브리핑", 21), 0);
        summary = label("", 14, MUTED, false);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(briefing, summary, 4);
        listen = button("브리핑 듣기", true, this::togglePlayback);
        transcriptButton = button("대본 보기", false, () -> {
            if (!canInteract()) return;
            transcriptVisible = !transcriptVisible;
            renderTranscript();
        });
        add(briefing, buttonRow(listen, transcriptButton), 8);
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
        for (Item item : Item.values()) {
            CheckBox check = new CheckBox(this);
            check.setText(item.label);
            check.setTextColor(TEXT);
            check.setMinHeight(dp(48));
            check.setChecked(enabled.contains(item));
            check.setOnCheckedChangeListener((view, checked) -> {
                if (!canInteract()) return;
                if (checked) enabled.add(item);
                else enabled.remove(item);
                preferences.edit().putBoolean("enabled_" + item.name(), checked).apply();
                refreshPlan();
            });
            add(settings, check, 0);
        }
        priorityButton = button("", false, this::choosePriority);
        add(settings, priorityButton, 4);

        LinearLayout savedPanel = panel();
        add(content, savedPanel, 12);
        savedHeading = heading("", 21);
        add(savedPanel, savedHeading, 0);
        savedRows = column();
        add(savedPanel, savedRows, 4);
        add(content, label("예시 콘텐츠 · 선택한 출처와 저장 목록은 이 기기에 보관돼요.", 13, MUTED, false), 14);

        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private Insets safeInsets(WindowInsets insets) {
        return Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout())
                : insets.getSystemWindowInsets();
    }

    private void selectRoute(int minutes) {
        if (!canInteract()) return;
        sampleRoute.select(minutes);
        refreshPlan();
    }

    private void choosePriority() {
        Item[] items = Item.values();
        String[] labels = new String[items.length];
        for (Item item : items) labels[item.ordinal()] = item.label;
        showDialog(new AlertDialog.Builder(this)
                .setTitle("먼저 듣고 싶은 소식")
                .setSingleChoiceItems(labels, priority.ordinal(), (dialog, index) -> {
                    dialog.dismiss();
                    if (!canInteract()) return;
                    priority = items[index];
                    preferences.edit().putString("priority", priority.name()).apply();
                    refreshPlan();
                })
                .setNegativeButton("닫기", null));
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
        else speech.play(plan.narration());
    }

    private void refreshPlan() {
        speech.stop();
        transcriptVisible = false;
        render();
    }

    private void render() {
        RouteInput.Snapshot route = routeInput.snapshot();
        int minutes = route.remainingMinutes.orElse(-1);
        eta.setText(minutes >= 0 ? minutes + "분" : "도착 시간 미확인");
        routeLabel.setText(route.sourceLabel + " · " + CLOCK.format(route.observedAt) + " 기준");
        setRouteButton(etaLong, minutes == 18);
        setRouteButton(etaShort, minutes == 4);
        priorityButton.setText("먼저 듣기 · " + priority.label);
        plan = Briefing.plan(route, enabled, priority);
        if (enabled.isEmpty()) {
            summary.setText("브리핑에 넣을 소식 출처를 선택해 주세요.");
        } else if (minutes < 0) {
            summary.setText("남은 시간을 확인하면 브리핑을 준비할 수 있어요.");
        } else if (plan.selected.isEmpty()) {
            summary.setText("듣기 예산 " + plan.budgetSeconds + "초 · 도착 후 볼 수 있어요.");
        } else {
            summary.setText("듣기 예산 " + plan.budgetSeconds + "초 · " + plan.selected.size()
                    + "건 약 " + plan.selectedSeconds + "초");
        }
        renderRows(route);
        renderSaved();
        renderSpeechStatus("");
        renderTranscript();
    }

    private void renderAccess() {
        boolean allowed = canUse();
        content.setVisibility(allowed ? View.VISIBLE : View.GONE);
        restrictionNotice.setVisibility(allowed ? View.GONE : View.VISIBLE);
        if (!allowed) {
            dismissDialog();
            speech.stop();
            restrictionNotice.setText(restrictionState == State.RESTRICTED
                    ? "정차 후 브리핑을 열어 주세요."
                    : "차량의 사용 가능 상태를 확인하고 있어요. 확인되면 브리핑을 열 수 있어요.");
        }
        renderSpeechStatus("");
    }

    private boolean canUse() {
        return !automotive || restrictionState == State.PARKED_USE_ALLOWED;
    }

    private boolean canInteract() {
        return resumed && canUse();
    }

    private void renderRows(RouteInput.Snapshot route) {
        briefingRows.removeAllViews();
        for (Item item : plan.selected) {
            String detail = item.detail;
            if (item.isUrgent(route.observedAt)) detail += " · 마감 임박으로 먼저 듣기";
            add(briefingRows, itemRow(item, detail), 8);
        }
        List<Item> deferred = plan.deferred;
        if (!deferred.isEmpty()) {
            add(briefingRows, heading("나중에 듣기 · " + deferred.size() + "건", 16), 12);
            for (Item item : deferred) add(briefingRows, itemRow(item, item.detail), 8);
        }
    }

    private View itemRow(Item item, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout text = column();
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        add(text, label(item.label + " · 약 " + item.estimatedSeconds + "초", 13, ACCENT, true), 0);
        add(text, label(item.title, 16, TEXT, true), 2);
        add(text, label(detail, 13, MUTED, false), 2);
        Button save = button("", false, () -> toggleSaved(item));
        updateSaveButton(save, item);
        save.setTag(item);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(8);
        row.addView(save, params);
        return row;
    }

    private void toggleSaved(Item item) {
        if (!canInteract()) return;
        if (!saved.add(item)) saved.remove(item);
        Set<String> ids = new HashSet<>();
        for (Item entry : saved) ids.add(entry.name());
        preferences.edit().putStringSet("saved", ids).apply();
        Button save = briefingRows.findViewWithTag(item);
        if (save != null) updateSaveButton(save, item);
        renderSaved();
    }

    private void updateSaveButton(Button button, Item item) {
        boolean selected = saved.contains(item);
        button.setText(selected ? "저장됨" : "저장");
        button.setContentDescription(item.title + (selected ? " 저장 취소" : " 저장"));
    }

    private void renderSaved() {
        savedHeading.setText("도착해서 보기 · " + saved.size());
        savedRows.removeAllViews();
        if (saved.isEmpty()) {
            add(savedRows, label("저장한 소식이 없어요.", 14, MUTED, false), 4);
        }
        for (Item item : saved) {
            Button entry = button(item.title, false, () -> showDialog(new AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setMessage(item.narration)
                    .setPositiveButton("닫기", null)
                    .setNeutralButton("저장 취소", (dialog, which) -> toggleSaved(item))));
            add(savedRows, entry, 6);
        }
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
        button.setTextColor(selected ? BACKGROUND : TEXT);
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
        button.setTextSize(15);
        button.setTextColor(primary ? BACKGROUND : TEXT);
        button.setMinimumHeight(dp(48));
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
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
