> Follow-up source changes and unexecuted warm-up checks are tracked in
> [seoyoon-warm-reuse.md](seoyoon-warm-reuse.md). Earlier runtime observations
> below do not validate this candidate. Cross-topic reuse remains disabled.

# Seoyoon production playback: source change and pending evidence

Status: SOURCE_CI_REVIEW_PENDING. No new APK, CI run, SDK callback, Windows
execution, audio recording or acoustic result is established by this checkout.
After Astra approves source and same-SHA CI, use WAITING_RUNTIME until Windows
executes the checklist below. Do not reuse older CI as evidence for this diff.

## Source boundaries

- SDK remains pinned to TextToSpeech 3.3.1. Both Pleos build types select the
  flavor factory with comparison off (debug default; release forced off).
  ON_DEVICE initialization configures Locale.KOREA, setSpeakerType("seoyoon")
  and MEDIA/SPEECH attributes before speak(text, true).
- Ordinary 브리핑 듣기 dispatches the selected Plan to BriefingMediaService.
  Pleos flavor also refuses Android fallback on non-automotive hosts. Sample
  keeps installed offline Korean Android TTS. Explicit debug baseline/candidate
  remain fixture comparisons; production never injects ComparisonFixture.TEXTS.
- Per-topic instances, focus gating, stop acknowledgement, latest skip target,
  generation checks, route deadline and UNKNOWN termination are retained. Error
  callbacks from an instance being stopped still fail closed; stale callbacks
  cannot finish its successor. Cleanup attempts listener removal and release
  independently. Resume reads the interrupted topic from its beginning.
- Debug PleosSpeech trace contains only engine, mode, requested voice and
  engine/start/done/error events. A requested speaker and onStart are software
  evidence, not proof of audible identity/onset. No item text or route data is
  added to this trace. Existing opt-in route validation is separate.
- Title comes first in collapsed and expanded news cards. 소식 labels the item
  area. Metadata has category, catalog publisher and sample/snapshot distinction;
  URL and technical timestamps stay in expanded detail. 출처 remains attribution,
  registration and filtering terminology. No window or vehicle policy changed.

## Permission scope and evidence (2026-11-09)

The existing `pleos.car.permission.TTS_SERVICE` declaration now lives in
`src/pleos/AndroidManifest.xml`; the redundant pleosDebug manifest is removed.
Gradle flavor inheritance therefore requests it for default Pleos debug and
release, while sample has no declaration. No microphone, window, navigation,
signing or vehicle-policy change was made. A declaration does not prove a
production service grant or successful playback.

Contract reference supplied with the task:
https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions .
No network tools were used under this task's restriction; that page was not
independently retrieved here. The change moves the existing permission verbatim.
Pinned SDK binary compatibility still requires a real SDK compile.

## Local checks on this working tree (2026-11-09)

- Reused installed OpenJDK 17.0.20.1; no JDK installation/download.
- `check-tts-lifecycle.py`: passed. Compiles unchanged Pleos factory and shared
  ComparisonSpeech/ComparisonFixture source against deterministic doubles with
  setSpeakerType and TTS_COMPARISON. The double requires ON_DEVICE, Korean,
  seoyoon, MEDIA attributes and listener registration before speak. Five cases:
  debug/off, release/off, debug/baseline, debug/candidate, release/candidate.
  Checks fixture/package and diagnostic isolation, sync/async initialization,
  stop acknowledgement/timeout, duplicate/stale events and cleanup. The release
  candidate case checks defense in depth; Gradle release still forces off.
- Source-set XML permission check passed for Pleos/sample debug/release.
  This is NOT Android manifest merger output or APK inspection.
- `check-route-lifecycle.py` and `check-route-handoff.py`: passed deterministic
  adapter/session replay, gating, cancellation, timeout and cleanup checks.
  These are not fresh SDK callbacks or Windows observations.
- `test-tts-trace-summary.py`: nine synthetic utility tests passed.
- `git diff --check`: passed.
- Offline Gradle testSampleDebugUnitTest, testPleosDebugUnitTest,
  assemblePleosDebug and assemblePleosRelease were attempted with JDK 17 and a
  temporary Gradle home. No cached Gradle 8.11.1 distribution was found. The wrapper
  attempted distribution bootstrap despite --offline and failed with
  `SocketException: Operation not permitted`, before Gradle tasks ran. No retry
  or external tools were used. No APK, SDK compilation, Robolectric execution or
  merged manifest is established by this attempt. Explicit comparison APK builds
  are likewise pending.
- Reviewed ordinary Plan dispatch, SDK lifecycle/failure handling, mixed-source
  labels, selection/persistence tests and existing motion/audio/ETA gates. Apart
  from the permission and stale checker defects reported by Astra, no additional
  actionable defect was established in this focused review. Product code and
  existing content settings/demo profile were left intact. UI/service unit tests
  remain unexecuted locally; source review does not pass their acceptance gate.

Evidence stages must remain separate:
1. New same-SHA CI unsigned default pleosDebug + actual pinned SDK compilation
   and required tests. Base CI #42603905314 only supports base 7aadeb0, not this diff.
2. Offline release APK build and Android merged-manifest inspection for both
   Pleos variants and sample, plus generated BuildConfig off/default/opt-in checks.
   No release signature or production permission grant follows from this stage.
3. Exact reviewed APK on Windows Pleos, with hashes/certificate and synchronized
   screen/device-output loopback, microphone off. Runtime acceptance remains
   WAITING_RUNTIME after source/CI approval. No Windows evidence exists here.

Required offline-capable CI checks with installed SDK/JDK/dependencies:
`testSampleDebugUnitTest`; `testPleosDebugUnitTest --tests '*BriefingMediaServiceTest'
--tests '*MainActivityTest.ordinaryButtonSubmitsSelectedPlanToMediaWithoutFixture'`;
`assemblePleosDebug assemblePleosRelease` with no comparison property.
Inspect generated BuildConfig, merged manifests and pinned SDK compilation for
both default build types. Existing MainActivity sample-route tests assume sample
flavor and are not a complete Pleos runtime suite. Also compile explicit debug
baseline/candidate and inspect sample boundary. Run normal project checks required
by CI; do not modify CI or signing for this task.

## Repeatable mixed demo (no preference reset)

Fresh installation already selects game11s, schedule8s, community13s = 32s at
sample 18min/45s budget. All three are labeled demo examples. At sample 4min/12s,
only schedule8s is selected. These are estimates, not measured speech durations.
Do not clear an existing user's data to reproduce this.

For a mixed official/demo Windows take, use a dedicated demo profile/install.
If reusing preferences, record current enabled sources/order first and restore
those manually afterward; never overwrite saved items or reset app data.

1. While stationary, open 상세 보기. Keep 일정 변경 예시 and 커뮤니티 예시 enabled;
   disable 게임 패치 예시 and other enabled sources for this take only.
2. 출처 추가: provider 리그 오브 레전드, name 리그 오브 레전드 패치,
   URL https://www.leagueoflegends.com/ko-kr/news/game-updates/ . Reuse this source
   if already registered. Move it above schedule/community, then schedule before
   community. Do not use live feed refresh or invent a headline.
3. Check selected titles and provenance: 리그 오브 레전드 26.22 패치 노트,
   official_snapshot fetched 2026-11-05T12:12:22Z (9s); 9시 팀 회의가 3층으로
   바뀌었어요, demo_sample (8s); 출근길에 듣기 좋은 10분짜리 팟캐스트,
   demo_sample (13s). Total estimate 30s. Samples are not personal calendar or
   community data. Snapshot is historical official material, not a live headline.
4. With actual navigation ETA, normal plan must permit all 30 estimated seconds
   (budget floor((ETA-180)/5), capped45). Retain actual ETA; do not inject sample
   ETA to claim a real route. If plan defers a topic, document this and choose a
   sufficiently long public test route while stationary. Urgency ordering remains
   normal; an expired sample deadline must not be presented as a live appointment.
5. Collapse details, show 소식/title/compact metadata, press ordinary 브리핑 듣기,
   let all three complete. Then show detail and honest publisher/original URL.
   Save a demo item if demonstrating saved content. Phone is consistently labeled
   휴대폰 연동 시연 and uses the same interests/item; it is a demonstration, not
   a shipped phone app or proof of cross-device sync.

## Windows evidence gate (not executed here)

Only after Astra source/CI approval, record the exact CI run/job/artifact name,
source SHA, signed APK SHA256/signing certificate and installed package/version.
Install that artifact with comparison off, no fixture injection or rebuild.
Record app button, logcat and live device-output loopback on one video timeline,
mic off. No separately recorded or dubbed TTS. Capture PleosSpeech engine/start/
done/error and prove matching audible content. No emulator is opened from Oracle.

Start with actual system side-by-side layout. Then, stationary, expand briefing
with no map visible. Vertical system stacking is a separate capability check;
never embed a fake map or change system window settings. Exercise portrait/narrow
and expanded states, accessibility, real ETA budget changes, saved selection
persistence, D restrictions (only active media queue controls), pause/resume,
rapid next, focus competition, route cancellation and UNKNOWN. Log only executed
conditions and results; retain failure evidence.

For sound, assess first syllable, topic joins, peak/clipping, dropout, crackle,
volume and focus handoff from matching loopback. Existing source holds granted
MEDIA/SPEECH focus across topic joins and waits for stop acknowledgement before
replacement. These are supported sequencing safeguards, not acoustic fixes.
No audible dummy utterance or invented Pleos PCM/pre-roll API was added. Android
sample's actual silent utterance is separate; a sleep would not be PCM padding.
Do not claim SDK pre-roll or an acoustic improvement without official supported
API guidance and actual loopback evidence. Gleo spoken-command/STT testing remains deferred by the user; this app-initiated
TTS task adds no microphone permission or real-utterance acceptance gate.

Record hardware/output/sample-rate
and any limitation; passing callbacks alone cannot pass the sound assessment.
