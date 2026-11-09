package com.beforearrival.briefing;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.beforearrival.briefing.AccessPolicy.State;
import com.beforearrival.briefing.ContentCatalog.Item;
import com.beforearrival.briefing.ContentCatalog.Plan;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class BriefingMediaService extends MediaBrowserService {
    private static final String ROOT = "commute-briefing";
    private static final String CHANNEL = "briefing_playback";
    private static final int NOTIFICATION = 41;
    // Activity and service run in the same process, on the main thread.
    private static final class Request {
        final Plan plan;
        final RouteInput.Snapshot route;
        Request(Plan plan, RouteInput.Snapshot route) {
            this.plan = plan;
            this.route = route;
        }
    }

    private static Request pending;
    private static BriefingMediaService current;
    private static Consumer<String> observer;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private final Runnable expire = () -> finish("듣기 예산에 맞춰 브리핑을 멈췄어요.");
    private final Runnable unavailable = () -> finish("차량 상태를 확인할 수 없어 브리핑을 멈췄어요.");
    private final Consumer<RouteInput.Snapshot> liveRouteObserver = this::onLiveRoute;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private CarRestrictions restrictions;
    private State carState = State.UNKNOWN;
    private TextToSpeech engine;
    interface SpeechFactory {
        ComparisonSpeech create(Context context, Runnable started, Runnable done, Runnable failed);
    }
    SpeechFactory speechFactory = ComparisonFactory::create;
    private ComparisonSpeech candidate;
    private boolean candidatePlaying, candidateStopping;
    private Runnable afterCandidateStop;
    private boolean comparison() {
        return ComparisonFixture.enabled(this);
    }
    private boolean candidateMode() {
        return ComparisonFactory.productionBackend()
                && !(comparison() && "baseline".equals(BuildConfig.TTS_COMPARISON));
    }
    private void voiceTrace(String event) {
        if (BuildConfig.DEBUG && candidateMode()) android.util.Log.d("PleosSpeech",
                "engine=pleos mode=ON_DEVICE voice=seoyoon event=" + event);
    }
    private void trial(String event) {
        if (comparison()) android.util.Log.d("TtsComparison", "elapsedRealtimeMs="
                + SystemClock.elapsedRealtime() + " engine=" + BuildConfig.TTS_COMPARISON
                + " topic=" + index + " " + event);
    }
    private boolean playing() { return candidateMode() ? candidatePlaying : prepared && player != null && player.isPlaying(); }
    private void stopCandidate(Runnable next) {
        candidatePlaying = false;
        afterCandidateStop = next;
        if (candidateStopping) return;
        if (candidate == null) { if (next != null) next.run(); return; }
        candidateStopping = true;
        trial("stop_request");
        ComparisonSpeech stopping = candidate;
        try { stopping.stop(() -> {
            if (candidate != stopping) return;
            trial("stop_completion_proxy");
            candidate = null; candidateStopping = false;
            Runnable continuation = afterCandidateStop; afterCandidateStop = null;
            if (continuation != null) continuation.run();
        }); } catch (RuntimeException unavailable) {
            trial("stop_failed_no_next_topic"); failPlayback();
        }
    }
    private void startCandidate() {
        if (candidate != null || candidateStopping || !hasFocus || !wantsPlayback || !canControl()) return;
        long token = ++generation;
        candidateGeneration = token;
        voiceTrace("engine");
        trial("start_request voice=seoyoon mode=ON_DEVICE");
        try {
            candidate = speechFactory.create(this,
                () -> { if (token == generation && canControl() && hasFocus && wantsPlayback) {
                    candidatePlaying = true; voiceTrace("start"); trial("sdk_onStart_proxy"); updateState();
                } },
                () -> { if (token == generation && !candidateStopping && !items.isEmpty()) {
                    candidatePlaying = false; voiceTrace("done");
                    candidate.release(); candidate = null;
                    if (!nextTopic()) finish("");
                } },
                () -> { if (token == candidateGeneration && candidate != null) {
                    trial("candidate_unavailable_or_error_no_fallback"); failPlayback();
                } });
            candidate.start(items.get(index).summary);
            main.postDelayed(() -> {
                if (token == generation && candidate != null && !candidatePlaying && !candidateStopping) {
                    trial("initialization_or_start_timeout"); failPlayback();
                }
            }, 15000);
        } catch (RuntimeException error) {
            trial("candidate_initialization_unsupported_no_fallback"); failPlayback();
        }
    }
    private boolean voiceReady;
    private boolean wantsPlayback;
    private boolean hasFocus;
    private boolean prepared;
    private boolean foreground;
    private List<Item> items = Collections.emptyList();
    private String queueId;
    private int index;
    private long generation;
    private long candidateGeneration = -1;
    private long focusVersion;
    private long playbackDeadline;
    private MediaPlayer player;
    private File speechFile;

    static void play(Context context, Plan plan, RouteInput.Snapshot route) {
        if (plan.selected.isEmpty() || plan.budgetSeconds <= 0 || isActive()) return;
        pending = new Request(plan, route);
        try {
            context.startForegroundService(new Intent(context, BriefingMediaService.class));
            notifyActivity("");
        } catch (IllegalStateException | SecurityException denied) {
            pending = null;
            notifyActivity("차량 미디어 재생을 시작하지 못했어요.");
        }
    }

    static boolean isActive() {
        return pending != null || (current != null && !current.items.isEmpty());
    }

    static boolean isPlaying() {
        return current != null && current.playing();
    }

    static void stop() {
        pending = null;
        if (current != null) current.finish("");
    }

    static void observe(Consumer<String> listener) {
        observer = listener;
    }

    private static void notifyActivity(String message) {
        if (observer != null) observer.accept(message);
    }

    @Override public void onCreate() {
        super.onCreate();
        current = this;
        audio = getSystemService(AudioManager.class);
        session = new MediaSession(this, ROOT);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumeBriefing(); }
            @Override public void onPause() { pauseBriefing(); }
            @Override public void onStop() { finish(""); }
            @Override public void onSkipToNext() { nextTopic(); }
            @Override public void onSkipToPrevious() { skipTo(Math.max(0, index - 1)); }
            @Override public void onSkipToQueueItem(long id) {
                if (id >= 0 && id < items.size()) skipTo((int) id);
            }
            @Override public void onPlayFromMediaId(String mediaId, Bundle extras) {
                for (int i = 0; i < items.size(); i++) {
                    if (id(i).equals(mediaId) && canControl()) {
                        wantsPlayback = true;
                        skipTo(i);
                        return;
                    }
                }
            }
        }, main);
        setSessionToken(session.getSessionToken());
        updateState();
    }

    @Override public BrowserRoot onGetRoot(String clientPackage, int uid, Bundle hints) {
        MediaSessionManager manager = getSystemService(MediaSessionManager.class);
        boolean trusted = uid == Process.myUid() || (manager != null && manager.isTrustedForMediaControl(
                new MediaSessionManager.RemoteUserInfo(clientPackage, -1, uid)));
        return trusted ? new BrowserRoot(ROOT, null) : null;
    }

    @Override public void onLoadChildren(String parent, Result<List<MediaItem>> result) {
        List<MediaItem> children = new ArrayList<>();
        if (ROOT.equals(parent)) for (int i = 0; i < items.size(); i++) {
            children.add(new MediaItem(description(i), MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(children);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Request request = pending;
        pending = null;
        if (request == null) {
            if (items.isEmpty()) stopSelf(startId);
            return START_NOT_STICKY;
        }
        items = new ArrayList<>(request.plan.selected);
        if (comparison()) {
            items.clear();
            String[] texts = ComparisonFixture.TEXTS;
            try {
                for (int i = 0; i < texts.length; i++) items.add(new Item(new org.json.JSONObject()
                    .put("id", "tts-fixed-" + i).put("title", "고정 음성 비교 " + (i + 1))
                    .put("summary", texts[i]).put("sourceId", "debug-fixture").put("estimatedSeconds", 30)));
            } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
            trial("fixture=v1 selected_in_debug_build_not_live_content");
        }
        queueId = UUID.randomUUID().toString();
        index = 0;
        wantsPlayback = true;
        session.setActive(true);
        List<MediaSession.QueueItem> queue = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) queue.add(new MediaSession.QueueItem(description(i), i));
        session.setQueue(queue);
        session.setQueueTitle("출근 브리핑");
        notifyChildrenChanged(ROOT);
        try {
            startForeground(NOTIFICATION, playbackNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            foreground = true;
        } catch (IllegalStateException | SecurityException denied) {
            finish("차량 미디어 재생을 시작하지 못했어요.");
            return START_NOT_STICKY;
        }
        updateState();
        playbackDeadline = SystemClock.elapsedRealtime() + request.plan.budgetSeconds * 1000L;
        main.postDelayed(expire, request.plan.budgetSeconds * 1000L);
        PleosVerificationTrace.record(this, "mediaPlan source=" + request.route.source
                + " etaSeconds=" + request.route.remainingSeconds.orElse(-1)
                + " budgetSeconds=" + request.plan.budgetSeconds + " tracks=" + items.size());
        if (request.route.source == RouteInput.Source.NAVI_HELPER) {
            LiveRouteSession.shared.attach(this, liveRouteObserver);
            if (items.isEmpty()) return START_NOT_STICKY;
        }
        main.postDelayed(unavailable, 4000L);
        restrictions = new CarRestrictions(state -> {
            State previous = carState;
            carState = state;
            if (state == State.UNKNOWN && previous != State.UNKNOWN) {
                finish("차량 상태를 확인할 수 없어 브리핑을 멈췄어요.");
            } else if (state != State.UNKNOWN) {
                main.removeCallbacks(unavailable);
                if (previous == State.UNKNOWN) {
                    if (state == State.PARKED_USE_ALLOWED) prepareIfReady();
                    else finish("주차 중에 브리핑을 시작해 주세요.");
                }
            }
        });
        restrictions.connect(this);
        if (!items.isEmpty()) {
            if (candidateMode()) { voiceReady = true; prepareIfReady(); }
            else engine = new TextToSpeech(this, status -> main.post(() -> initializeVoice(status)));
        }
        return START_NOT_STICKY;
    }

    private void onLiveRoute(RouteInput.Snapshot route) {
        if (items.isEmpty()) return;
        int budget = ContentCatalog.listeningBudget(route);
        if (budget == 0 || Duration.between(route.observedAt, Instant.now()).getSeconds() >= 15) {
            PleosVerificationTrace.record(this, "mediaRouteUnavailable");
            finish("현재 경로의 듣기 시간이 없어 브리핑을 멈췄어요.");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        playbackDeadline = Math.min(playbackDeadline, now + budget * 1000L);
        main.removeCallbacks(expire);
        main.postDelayed(expire, Math.max(0, playbackDeadline - now));
    }

    private void initializeVoice(int status) {
        if (items.isEmpty()) return;
        if (status != TextToSpeech.SUCCESS || !SpeechPlayback.selectOfflineKoreanVoice(engine)) {
            trial("baseline_initialization_unavailable_no_offline_korean_voice");
            finish("설치된 한국어 음성을 찾지 못했어요. 대본을 읽을 수 있어요.");
            return;
        }
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { main.post(() -> loadTrack(id)); }
            @Override public void onError(String id) {
                main.post(() -> { if (id.equals(utteranceId())) failPlayback(); });
            }
        });
        if (comparison()) trial("baseline_engine=" + engine.getDefaultEngine() + " voice=" + engine.getVoice().getName() + " offline=true");
        voiceReady = true;
        prepareIfReady();
    }

    private boolean canControl() {
        return !items.isEmpty() && carState != State.UNKNOWN;
    }

    private void prepareIfReady() {
        if (voiceReady && canControl() && speechFile == null && !prepared) prepareTrack();
    }

    private void prepareTrack() {
        clearTrack();
        if (candidateMode()) {
            prepared = true;
            session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id(index))
                .putString(MediaMetadata.METADATA_KEY_TITLE, items.get(index).title).build());
            stopCandidate(this::startIfReady);
            return;
        }
        Item item = items.get(index);
        try {
            speechFile = File.createTempFile("briefing-", ".wav", getCacheDir());
            trial("start_request timestamp_includes_file_synthesis");
            if (engine.synthesizeToFile(item.summary, new Bundle(), speechFile, utteranceId())
                    != TextToSpeech.SUCCESS) {
                failPlayback();
                return;
            }
            session.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id(index))
                    .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "도착 전에 출근 브리핑")
                    .build());
            updateState();
        } catch (IOException | RuntimeException error) {
            failPlayback();
        }
    }

    private void loadTrack(String id) {
        if (!id.equals(utteranceId()) || !canControl() || speechFile == null) return;
        try (FileInputStream file = new FileInputStream(speechFile)) {
            player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setDataSource(file.getFD());
            player.setOnPreparedListener(ready -> {
                if (ready != player) return;
                prepared = true;
                startIfReady();
            });
            player.setOnCompletionListener(done -> {
                if (done == player && !nextTopic()) finish("");
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (failed == player) failPlayback();
                return true;
            });
            player.prepareAsync();
        } catch (IOException | RuntimeException error) {
            failPlayback();
        }
    }

    private void startIfReady() {
        if (prepared && wantsPlayback && canControl()) {
            if (focus == null && !requestFocus()) {
                finish("오디오를 사용할 수 없어요. 대본을 읽을 수 있어요.");
                return;
            }
            try {
                if (candidateMode()) {
                    if (candidateStopping) afterCandidateStop = this::startIfReady;
                    else startCandidate();
                }
                else if (hasFocus && !player.isPlaying()) { player.start(); trial("MediaPlayer_start_return_proxy_not_verified_audible_onset"); }
            } catch (IllegalStateException error) {
                failPlayback();
                return;
            }
        }
        updateState();
    }

    private boolean requestFocus() {
        if (audio == null) return false;
        long version = ++focusVersion;
        AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (focus == null || version != focusVersion) return;
                    if (change == AudioManager.AUDIOFOCUS_LOSS) finish("");
                    else if (change < 0) {
                        hasFocus = false;
                        pauseTrack();
                        updateState();
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        hasFocus = true;
                        startIfReady();
                    }
                }, main)
                .build();
        int result = audio.requestAudioFocus(request);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return false;
        focus = request;
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return true;
    }

    boolean nextTopic() { return skipTo(index + 1); }

    private boolean skipTo(int next) {
        if (!canControl() || next < 0 || next >= items.size()) return false;
        trial("skip_command next=" + next);
        index = next;
        if (voiceReady) prepareTrack();
        return !items.isEmpty();
    }

    boolean pauseBriefing() {
        if (!canControl() || !wantsPlayback) return false;
        wantsPlayback = false;
        pauseTrack();
        abandonFocus();
        updateState();
        return true;
    }

    boolean resumeBriefing() {
        if (!canControl()) return false;
        wantsPlayback = true;
        if (candidateMode() && candidateStopping) afterCandidateStop = this::startIfReady;
        startIfReady();
        return !items.isEmpty();
    }

    static BriefingMediaService activeSession() { return current; }

    private void pauseTrack() {
        trial("pause_or_focus_stop_command");
        if (candidateMode()) { generation++; stopCandidate(null); }
        else if (prepared && player.isPlaying()) player.pause();
    }

    private void abandonFocus() {
        AudioFocusRequest previous = focus;
        focusVersion++;
        focus = null;
        hasFocus = false;
        if (previous != null) audio.abandonAudioFocusRequest(previous);
    }

    private void clearTrack() {
        generation++;
        prepared = false;
        if (player != null) {
            player.release();
            player = null;
        }
        if (speechFile != null) {
            if (engine != null) engine.stop();
            speechFile.delete();
            speechFile = null;
        }
    }

    private void failPlayback() {
        voiceTrace("error");
        finish(candidateMode() ? "Pleos 음성을 사용할 수 없어요. 주차 중 대본을 확인해 주세요."
                : "브리핑을 재생하지 못했어요. 대본을 읽을 수 있어요.");
    }

    private void finish(String message) {
        releasePlayback();
        updateState();
        stopSelf();
        notifyActivity(message);
    }

    private void releasePlayback() {
        main.removeCallbacksAndMessages(null);
        trial("stop_command");
        items = Collections.emptyList();
        LiveRouteSession.shared.detach(liveRouteObserver);
        wantsPlayback = false;
        carState = State.UNKNOWN;
        if (restrictions != null) {
            restrictions.close();
            restrictions = null;
        }
        clearTrack();
        afterCandidateStop = null;
        candidateGeneration = -1;
        if (candidate != null) {
            ComparisonSpeech previous = candidate;
            candidate = null;
            try { previous.stop(() -> {}); } catch (RuntimeException ignored) { voiceTrace("error"); }
            try { previous.release(); } catch (RuntimeException ignored) { voiceTrace("error"); }
        }
        candidateStopping = false; candidatePlaying = false;
        abandonFocus();
        voiceReady = false;
        if (engine != null) {
            engine.shutdown();
            engine = null;
        }
        session.setQueue(null);
        session.setMetadata(null);
        session.setActive(false);
        notifyChildrenChanged(ROOT);
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
    }

    @Override public void onDestroy() {
        boolean wasPlaying = !items.isEmpty();
        releasePlayback();
        session.release();
        current = null;
        if (wasPlaying) notifyActivity("");
        super.onDestroy();
    }

    private String utteranceId() { return "briefing-" + generation; }

    private String id(int position) { return queueId + "/" + position; }

    private MediaDescription description(int position) {
        return new MediaDescription.Builder().setMediaId(id(position))
                .setTitle(items.get(position).title)
                .setSubtitle("출근 브리핑").build();
    }

    private void updateState() {
        boolean hasQueue = !items.isEmpty();
        boolean playing = playing();
        long actions = hasQueue
                ? PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
                : 0;
        if (hasQueue && index + 1 < items.size()) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        int state = !hasQueue ? PlaybackState.STATE_NONE : playing ? PlaybackState.STATE_PLAYING
                : wantsPlayback && !prepared ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(state, prepared && player != null ? player.getCurrentPosition() : 0, playing ? 1 : 0)
                .setActiveQueueItemId(hasQueue ? index : MediaSession.QueueItem.UNKNOWN_ID)
                .build());
        if (foreground) getSystemService(NotificationManager.class).notify(NOTIFICATION, playbackNotification());
        if (hasQueue) PleosVerificationTrace.record(this,
                "mediaTrack=" + (index + 1) + "/" + items.size() + " state=" + state);
        notifyActivity("");
    }

    private Notification playbackNotification() {
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "브리핑 재생", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(items.get(index).title)
                .setContentText("도착 전에 출근 브리핑")
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()))
                .build();
    }
}
