# Seoyoon preparation and conservative callback boundary

Status: SOURCE_REVIEW_PENDING / BUILD_BLOCKED. Windows gate: WAITING_RUNTIME,
conditional on source acceptance and same-SHA CI. This checkout has no new APK,
real SDK compile, SDK callback or acoustic result. Cross-topic engine reuse is
**not implemented**; the requested single construction for three topics is unmet.
Do not call the crackle fixed or treat this as full task acceptance.

## Source behavior

Only ordinary Pleos playback prepares ON_DEVICE Korean `seoyoon`. The resumed,
visible parked screen requests one application-context engine. Initialization,
locale, speaker and media audio attributes are the only SDK operations before
playback. No dummy utterance, focus request, foreground service, network, permission
or alternate voice is added. Sample Android speech and explicit debug comparison
retain their existing dispatch. Both Pleos build types still use the same factory.
The genuine side-by-side and compact/expanded layout are unchanged.

A main-thread lease is attempted once per eligible screen lifetime. Idle preparation
expires at 15 seconds, releases on pause or eligibility loss, and releases on
initialization failure. Normal play transfers only a fully initialized/configured, listener-registered
lease into the pending service request; screen teardown then cannot release it. An unbound abandoned request
also expires. The service binds callbacks only when starting the first topic after
its existing vehicle and focus gates. Initializing preparation is released immediately on listen; it is never bound
to service callbacks. Late success/failure cannot resurrect that closed lease.
Readiness is checked again at bind. Expired/failed/not-ready preparation falls back to
ordinary construction in the same Pleos factory. A bound engine belongs exclusively
to the service and follows its cancellation/deadline/shutdown cleanup. No engine
is retained for a later playback session.

## Public callback limitation and deliberate non-reuse

The pinned 3.3.1 surface used here is `initialize(OnInitializeListener)`,
`addEventListener/removeEventListener`, `speak(String, boolean)`, `stop()` and
`release()`. `EventListener.onStart/onDone/onStop` carry no utterance ID or generation.
The supplied contract does not establish that all callbacks for one utterance have
been drained when onDone is delivered. A Handler post is a serial app handoff,
not a remote callback barrier. Changing listeners cannot label an event dispatched
late through the new listener. A quiet delay cannot prove such a boundary either.

For example: A done, B submitted, duplicate A start, duplicate A done. The last two
are indistinguishable from B start/done on the same SDK instance. Engine-local
phase guards alone cannot prove the requested stale-event isolation under this
sequence. Therefore only preparation-to-first-topic ownership is reused. Each
natural topic completion still releases the instance before constructing the next.
A second `start` on one adapter is rejected. Callback arrival before submission,
done before start, duplicate start/done, and events from stopped/released engines
cannot advance another topic. Duplicate initialize success cannot reconfigure.
Pause/skip/focus interruptions still require onStop, release the old instance, and
create a fresh engine. Missing stop acknowledgement retains the five-second
fail-closed timeout; startup retains its fifteen-second timeout. Route cancellation,
UNKNOWN stop, D media controls, focus/MediaSession and playback deadline stay in
the existing service paths.

Debug-only PleosSpeech timing records init, initialized, ready, speak, start and
done with monotonic milliseconds and engine instance generation. They contain no
article text, route, account or user data. `onStart` remains a software callback,
not proof of first audible speech. No codec, pre-roll or unsupported tuning added.

## Checks in this checkout

- `git diff --check`: passed.
- Source-set TTS permission isolation: passed (Python-only check, not merged
  manifest evidence). Exact task editPaths check: passed.
- Python AST parse of `check-tts-lifecycle.py`: passed. This is syntax only.
- `python3 android/validation/pleos-emulator/check-tts-lifecycle.py`: blocked before
  Java checks: JDK 17+ (`javac` and `java`) absent.
- `bash android/gradlew --offline -p android :app:testPleosDebugUnitTest
  :app:compilePleosReleaseJavaWithJavac`: blocked before Gradle by missing Java and
  unset JAVA_HOME. The wrapper is not executable, so it was invoked through bash.
- No dependencies downloaded, network/credential tools used, CI changed, signing
  changed, commit made, or AVD launched.

Added deterministic cases (written, **not executed**): silent bounded preparation,
one lease/one transfer, lifecycle release versus service ownership, expiration of
idle and claimed-unbound leases, synchronous/asynchronous warm failure and cold
fallback; actual service consumption of a prepared first engine; callback phase
and duplicate protection in the legacy SDK-double checker. Existing service tests
cover ordinary selected text, fresh instances for sequential topics, rapid skip,
stop acknowledgement, pause/resume, old engine callbacks, focus denial/loss,
UNKNOWN, route cancellation and deadline. The legacy checker retains five-second
stop timeout, release cancellation and owner-specific timer tests. These doubles
are not evidence of actual 3.3.1 callback delivery. No three-topic single-engine
construction test passes or is claimed. UI selection, time-based plan, persistence
and restriction regression suites also remain unexecuted here.

## Required source/CI and Windows follow-up

First compile both Pleos build types against the actual pinned AAR and run the
unit suites and lifecycle checker in an equipped runner. Check sample and explicit
comparison boundaries and merged release/debug manifests separately. This local
result cannot substitute for same-SHA CI or Astra acceptance. Signed release
installation/service grants remain separate; Gleo STT is deferred and not a gate
for this app-initiated TTS task.

After source/CI acceptance, verify the exact CI artifact hash and install that
build on Windows with mic off. Compare base 442c03fa4956 and candidate on the same
AVD using identical ordinary plan/text, speakers, volume and capture method.
Label at least two comparable runs per build, recording cold versus warm process,
SDK/service and screen preparation state. Collect raw app PCM/loopback and screen
output together, focus events, press-to-first audible speech, per-topic gap/dropout
metrics and restartIfDisabled/SDK underruns with monotonic timing. Separate delay
from crackle: absence of warnings or shorter initial latency alone proves neither
clean sound nor the cause. Baseline correlations 0.9982–0.9992 and prior cold/warm
observations are historical observations only, not candidate evidence or a speaker
defect diagnosis.

No candidate Windows run, human listening, physical-car listening or recording was
performed. Keep the previous demo video. Re-record actual app output and screen
(no dub) for side-by-side/detail/mixed content only if repeated controlled results
show improvement and safety holds. Otherwise retain the old video and investigate
raw PCM discontinuities aligned with SDK/AudioTrack timing. To enable cross-topic
reuse safely, obtain a documented callback drain barrier or correlated utterance
identity before replacing the current conservative instance boundary.


## Repair validation (2026-11-09, uncommitted working tree)

The review's initialization-in-flight defect is repaired: `take()` checks the
adapter's main-thread readiness snapshot and closes a not-ready engine before
returning null. The service then follows its existing ordinary same-factory
construction path. The production adapter reports ready only after initialization,
Korean/seoyoon/media configuration and event listener registration, while still
open and not stopping/terminal. Other implementations default to not ready;
sample and explicit comparison dispatch are unchanged. No SDK API was added.

Added deterministic tests (written, **not executed**): initializing lease rejection
and late failure/success isolation in the warmup suite; prepare → listen before
initialization → cold service request → old failure/start/done in the service
suite; and real production adapter with SDK doubles for old initialization failure,
success and event callbacks after replacement in the lifecycle checker. Existing
ready-lease transfer tests now provide an explicit ready fake. These are source
checks/test cases, not observed SDK or acoustic behavior.

Executed locally for this repair:

- `git diff --check`: passed.
- Python AST parse and source-set permission isolation: passed (not merged manifests).
- Lifecycle checker: exit 1, missing `java`/`javac`; no Java checks ran.
- Offline Gradle `testPleosDebugUnitTest`, `compilePleosDebugJavaWithJavac`,
  `compilePleosReleaseJavaWithJavac`: exit 1 before Gradle, missing Java/JAVA_HOME.

Acceptance 1: readiness/fallback repair and deterministic regression cases are in
source; execution remains blocked. Acceptance 2 remains **unmet**: no public
callback drain barrier or utterance correlation was established, so natural topic
transitions still create fresh instances. No single-engine three-topic success is
claimed. Prior CI #42610988285 debug compilation applies to the reviewed predecessor,
not this repair, and does not prove unit tests or release compilation. Same-revision
CI and Astra source acceptance remain required. Acceptance 3: **WAITING_RUNTIME**;
no Windows verification, artifact installation, A/B metrics, physical-car or human
listening was performed. Existing video is unchanged; no acoustic improvement is
claimed. The Windows procedure above remains outstanding, including route/D/UNKNOWN
and focus transitions. Gleo spoken commands remain deferred.
