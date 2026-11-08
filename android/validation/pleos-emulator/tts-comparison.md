# Debug Korean TTS comparison (fixture v1)

Implementation, not trial evidence. No Windows run, audible onset, voice preference,
real Gleo recognition, or SDK callback has been observed for this change. Earlier local
Gradle compilation and core checks were blocked by a missing JDK. The scoped
offline lifecycle run recorded below used a supplied JDK 17; it is not an APK
build. Do not describe this checkout as a successfully built APK until built.
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

The Handler double uses a deterministic shared simulated clock, ordered due tasks
(insertion order for equal deadlines), and owner-specific removal for
`removeCallbacksAndMessages(null)`. `drain()` runs only currently due work;
`advanceBy()` runs due work in deadline order without sleeping. SystemClock reads
that simulated clock. Delayed callbacks are no longer discarded.
For both initialization modes, the regression checks assert:

- A submitted request stopped without `onStop` has no failure or continuation at
  4,999 ms, then exactly one failure and no next-topic continuation at 5,000 ms.
- Successful `onStop` cancels the timeout; advancing beyond five seconds produces
  no failure and duplicate stop still completes only once.
- Explicit release before the deadline prevents a later timeout failure, while
  preserving another candidate's pending timeout and its own deadline.
- After timeout and explicit release, late stop/error callbacks cannot continue
  the queue or add failures; subsequent time advances add no failures.

Source inspection (not service execution): `Candidate.stop()` schedules the
5,000 ms failure callback after `sdk.stop()` for a submitted request. Its `onStop`
handler releases the instance before calling the stop continuation.
`BriefingMediaService.startCandidate()` wires failure to `finish()`, which invokes
`releasePlayback()` and clears `afterCandidateStop`; `stopCandidate()` advances
only through the stop completion callback. The harness explicitly calls release
after observing timeout to model that cleanup. It does not compile or run the
service, so it does not prove service integration or Android scheduling behavior.
No production defect was observed in these cases; runtime code is unchanged.

Local execution for this scoped regression (2026-11-08), from the checkout root:

```sh
PATH=/home/ubuntu/.local/share/before-arrival-runner/pleos-validation-stage.FmMxBcJv/jdk17-validation.k3GzMAZw/runtime/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH python3 android/validation/pleos-emulator/check-tts-lifecycle.py
```

Exit status 0; the harness invoked `javac --release 17` on the actual adapter and
interface plus temporary doubles, then ran `LifecycleCheck` with `java`. Output:

```text
PASS: candidate lifecycle (sync/async initialization, no onReady, duplicate/stale callbacks, five-second stop timeout, success/release cancellation, owner-specific cleanup)
```

Temporary sources/classes are removed on exit; no Gradle, SDK download, AVD or
microphone is required. This is adapter-level mock evidence only, not a full
service test, real SDK compatibility/callback evidence, Pleos emulator execution,
APK build, audible stop timing or acoustic behavior. The earlier missing-JDK
attempt did not execute assertions; this supplied-JDK run did.

The visible-vehicle Gleo utterance test, actual route audio and acoustic follow-up
are deferred at the user's request. Windows Pleos callback/order, audible onset,
stop/skip overlap, pronunciation and preference evidence remain pending. Injected
commands are not voice trials. No Windows evidence is added by this check.

## Offline trace summary (no trial evidence)

From the checkout root, using only Python 3's standard library:

```sh
python3 android/validation/pleos-emulator/summarize-tts-traces.py baseline.log candidate.log
python3 android/validation/pleos-emulator/test-tts-trace-summary.py
```

Supply one complete fixture-v1 queue capture per file, starting before the fixture
marker. Preserve `TtsComparison` lines and their monotonic timestamps; the guide's
`logcat -v epoch` prefix is supported. Other tags are ignored. Output identifies
inputs by ordinal, never by filename. No raw lines, briefing text, voice names,
transcripts, error details or epoch timestamps are emitted. Baseline engine package
is reported when present and syntactically valid; candidate reports Pleos ON_DEVICE
with identity unverified. Missing baseline engine metadata remains `unknown`.

The JSON separates the initial topic-0 `start_request` to arm-specific start proxy
from each `skip_command` to its matching next topic start proxy. Baseline uses
`MediaPlayer_start_return_proxy_not_verified_audible_onset`; candidate uses
`sdk_onStart_proxy`. Intervals include synthesis/initialization or stop/next startup
as applicable. They are **software proxies, not audible timing**, and do not measure
stop silence, overlap, voice quality or listener preference. `skip_command` also
occurs on automatic topic advancement: this format cannot identify a user skip.

`complete` means the requested initial and at least one skip interval were paired;
it does not certify a full three-topic trial or successful playback. `incomplete`
means a required boundary/measurement or readable input is missing. `failure`
means an explicit error/failure record was observed; any retained intervals are
partial observations, not a successful trial. `invalid` means attribution is unsafe
(mixed arms/processes, repeated fixture markers, backward clock, malformed records,
unpaired/repeated requests or interrupted playback); its measurements are cleared.
Exit status is 0 only when every input is complete, otherwise 1. Without opt-in, the tool does not
compare arms or rank engines.


### Optional paired software-proxy comparison

```sh
python3 android/validation/pleos-emulator/summarize-tts-traces.py --compare baseline.log candidate.log
```

Use exactly two separate captures: one complete baseline and one complete Pleos
candidate fixture-v1 queue. Input order does not matter. The existing positional
CLI, reports and JSON shape remain unchanged when `--compare` is omitted. Opt-in
adds a `comparison` object while retaining the individual reports.

The comparison keeps `first_request_to_start_proxy_ms` separate from the ordered
`skip_to_next_start_proxy_ms` entries. Each contains `baseline_ms`, `candidate_ms`
and `candidate_minus_baseline_ms`: negative means the candidate software-proxy
interval is shorter, positive means longer, and zero means equal. For example,
30 ms baseline and 20 ms candidate yield -10 ms. These are different arm-specific
software boundaries, never audible latency or an engine quality ranking. Skips
are matched by ordered topic sequence, including repeated occurrences; they are
not averaged or combined with the first request.

Comparison status is `refused` with a fixed issue code and no comparison values
unless there are exactly two complete inputs, one per arm, with identical ordered
skip-topic sequences. Incomplete, unreadable, failed, invalid and same-arm pairs
exit 1, as do mismatched skip sequences. Successful comparison exits 0. Individual
reports retain their existing semantics, including partial values on incomplete
or failed inputs; those values are never used in a refused comparison. Input
paths and free-form source text are not included in comparison output. Completeness
still has the limited definition above; matching traces cannot establish matching
trial conditions or real playback.

Paired-comparison local regression evidence: all nine unittest methods passed on
synthetic fixtures, including both input orders, signed/zero deltas, multiple skips,
same-arm and wrong-count refusal, incomplete/failed/invalid inputs, mismatched skip
topics, text/path redaction and unchanged non-opt-in JSON. The existing lifecycle
check was also attempted but blocked by missing `java`/`javac`; no lifecycle
assertions executed. This is offline utility evidence only. No APK build, Windows
AVD run, actual SDK callback, audible timing or Gleo voice evidence was added.

There is no queue/run ID in the current trace format. A concatenation with missing
boundaries, monotonically increasing time and the same arm/PID may be impossible to
detect. Keep original captures separate and review capture provenance; this tool
cannot prove matching build, fixture text, route, cache state or device conditions.
Truncated failures that emit no failure record cannot be reconstructed.

Local utility checks actually run: four standard-library unittest methods covering
both arms, missing callbacks, errors, mixed runs/processes, malformed and interrupted
records; CLI subprocess checks for valid arms, empty/mixed/error/unreadable inputs
and exit codes 0/1; `git diff --check`. All passed on synthetic non-personal inputs.
These checks add no APK, emulator, real SDK or acoustic evidence. Windows Pleos
trials and actual Gleo utterance remain deferred; all Windows evidence listed above
remains pending. Speech runtime, vehicle restrictions and production defaults are
unchanged.
