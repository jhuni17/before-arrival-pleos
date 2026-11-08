# Debug Korean TTS comparison (fixture v1)

Implementation, not trial evidence. No Windows run, audible onset, voice preference,
real Gleo recognition, or SDK callback has been observed for this change. Local
Gradle compilation and core checks were attempted but blocked: no Java/JDK is
installed. Do not describe this checkout as a successfully built APK until built.
The supplied official 3.3.1 javap contract is the source for the adapter:
https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
No credentials, server mode, speaker ID, or assistant voice identity are used.
SDK default ON_DEVICE voice identity remains unverified. SDK initialization,
onReady ordering, stop-completion behavior and service availability require AVD validation.

## Selection and rollback on Windows

Use a dedicated Windows Pleos Connect 2.1.0 AVD with visible vehicle display and
Virtual Console. Record source revision plus uncommitted diff hash, Windows/AVD/
SDK versions, serial, APK SHA-256 and baseline engine/version/voice. Use public test
routes; never record private routes, human speech or accounts in telemetry.

With JDK 17 and Android SDK available, from `android` in PowerShell:

```powershell
.\gradlew.bat :app:assemblePleosDebug -PttsComparison=baseline
Get-FileHash .\app\build\outputs\apk\pleos\debug\app-pleos-debug.apk -Algorithm SHA256
adb -s SERIAL shell am force-stop com.beforearrival.briefing
adb -s SERIAL install -r .\app\build\outputs\apk\pleos\debug\app-pleos-debug.apk
adb -s SERIAL logcat -c
adb -s SERIAL logcat -v epoch -s TtsComparison:D
```

Install eSpeak NG with Korean voice locally on the AVD, or identify another
installed offline Korean Android engine in system TTS settings. Select it as the
Android default *before* launching. The app selects an installed offline Korean
voice; record the logged engine package and voice name rather than assuming the
engine/voice. An unavailable offline voice is a failed baseline, not a trial.

Build/install identically with `-PttsComparison=candidate` for Pleos ON_DEVICE.
Verify merged Pleos debug manifest has `pleos.car.permission.TTS_SERVICE` and the
AVD authorizes access. No baseline fallback occurs for candidate failure.
After each failed/finished run stop the queue and force-stop before changing builds.
Do not switch engines in a running queue. For rollback rebuild/install with
`-PttsComparison=off` (or omit the property). No persistent preference is written.
Release always embeds `off`; sample flavor does not opt in. TTS dependency stays
in Pleos flavor; the new permission is in `pleosDebug` only.

In P, open the app, choose content and a real route with enough live ETA/budget
for three topics, and start via the existing parked screen. Comparison replaces
only the media queue content with three fixed Korean fixture topics; the script
panel labels this clearly. Both arms use the exact `ComparisonFixture.TEXTS` v1
strings in the same order. Do not compare the normal selected article transcript.
User selection, saving and time-based planning remain normal outside this debug
fixture; selected items and route budget are still prerequisites. Route/focus and
unknown-vehicle-state guards remain active. Android media actions and Gleo schema
and protected receiver are unchanged.

Pleos directly speaks after successful initialization, listener registration and
granted audio focus. `initialize()` must return before `addEventListener()`;
`onReady` is diagnostic only and is not a prerequisite for the first ON_DEVICE
request.
Each topic has its own SDK instance. Skip waits for onStop, releases that instance,
then starts the next instance. A missing stop callback fails the queue after five
seconds; initialization/start without onStart fails after fifteen seconds. Old
instance callbacks cannot complete a new topic. Pause/focus interruption stops
Pleos; resume restarts that topic from the beginning (there is no SDK pause API).
Baseline retains file synthesis and MediaPlayer pause/resume. Compare uninterrupted
starts and explicit skips; do not treat differing resume semantics as a latency result.

## Windows trial sheet (all results PENDING)

Repeat at least three trials per arm, counterbalance arm order, and use identical
route, volume, audio output, fixture and skip point. Record fresh-process trials
separately from repeated queues in the same process; cold boot/service/voice cache
state is separate and must be described. Candidate creates a fresh SDK instance per
topic, so warm does not mean a reused SDK instance. Make no unsupported cold-cache claim.

`TtsComparison` logs use monotonic `SystemClock.elapsedRealtime()` milliseconds,
including sleep. Logcat epoch is a different clock. Candidate start_request precedes
initialization/listener registration; SDK onStart is a callback proxy. Baseline start_request
precedes file synthesis; MediaPlayer_start_return is a software proxy, NOT verified
playback. Compare request-to-proxy only under these definitions. Stop/skip commands,
stop completion and next topic start are logged; baseline file release has no
verified audible stop callback. Collect app audio/loopback only, with a synchronized
marker mapping monotonic logs to captured audio. Never capture microphone speech.

Record separately for each arm/topic/trial:

| Evidence | Result |
| --- | --- |
| Build/APK hash, engine/version, voice or explicit unknown | pending |
| Initialization/readiness success, error or unsupported | pending |
| Cold/warm definition and request-to-start proxy ms | pending |
| Captured audible onset ms (aligned audio, not callback) | pending |
| Skip command to next audible onset gap ms | pending |
| Any old-audio overlap after stop/skip and duration | pending |
| Korean pronunciation (numbers/times/names), intelligibility | pending |
| Blinded listener preference, listener count and rationale | pending |
| Focus loss, unknown vehicle state, route cancellation silence | pending |

For live handoff: begin in P with real live ETA, start the queue, switch to D and
HOLD D through actual Gleo utterance recognition, protected receiver dispatch and
next topic playback. Keep vehicle display visible. Retain redacted recognition /
response evidence and app event ordering; record no human speech audio. Verify
actual navigation route cancel stops audio. Repeat relevant focus/unknown-state
checks. ADB injected broadcasts/media actions prove neither real recognition nor
Gleo permission dispatch. Compare Gleo response voice separately by ear if available;
never infer it is the ON_DEVICE voice. Windows audio metrics, preference and real
Gleo are pending until measured. Linux checks, when available, prove compilation
or tested logic only, never Windows playback.

Before acceptance also rerun normal (`off`) selection, time-based plan, persistence,
P-to-D setup/script locking and unknown-state transitions for both flavors; verify
release is `off`, baseline pause/resume and existing live ETA/cancel behavior.

## Deterministic candidate lifecycle regression check

From the checkout root, with Python 3 and JDK 17+ on PATH:

```sh
python3 android/validation/pleos-emulator/check-tts-lifecycle.py
```

This compiles the actual Pleos `ComparisonFactory.java` and `ComparisonSpeech.java`
against temporary, dependency-free Android/SDK doubles. It does not copy the
candidate lifecycle implementation. The double rejects listener registration before
initialization and speech before successful initialization, listener registration
and configuration. Both synchronous and asynchronous initialization callbacks are
covered, including text supplied before and after success. No `onReady` is sent
before the first request; duplicate success/readiness must not resubmit. Stop waits
for `onStop`, duplicate stop completes once, and callbacks from a released instance
cannot affect its successor. Late initialization after release cannot speak.
Temporary sources/classes are removed on exit; no Gradle, SDK download, AVD or
microphone is required. The doubles check adapter ordering only, not real SDK
compatibility, Android scheduling, stop timeout timing or acoustic behavior.

For this scoped change, Python syntax and harness source-path checks passed. The
lifecycle command was attempted but blocked because this checkout environment has
no `java` or `javac`; no lifecycle assertions or APK build ran locally. Existing
hosted CI build/compile evidence is separate and does not establish that this new
standalone check ran. Run the command in a JDK-equipped checkout before relying on
its result.

The visible-vehicle Gleo utterance test, actual route audio and acoustic follow-up
are deferred at the user's request. Windows Pleos callback/order, audible onset,
stop/skip overlap, pronunciation and preference evidence remain pending. Injected
commands are not voice trials. No Windows evidence is added by this check.
