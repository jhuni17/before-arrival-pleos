# Android confirm-next finish label validation

2026-11-08

## Change

`showSavedDetail` uses one opening-time `nextUnread(item)` result for the
confirm-next label and preview. The two labels are string resources. Existing
button tag/style, unread-only insertion, click-time candidate recalculation,
storage retry, undo and lifecycle/access guards remain unchanged. The button has
no explicit content description, so its accessible name follows its current
text, including the storage failure text.

Three `confirmNextFinish*` tests use explicit one/three-item fixtures (fixture
sizes are not product limits). They cover single unread and all-other-confirmed
finish states, wraparound across equal titles with distinct keys, candidates
outside search/source/state filters, reopening, changed candidates at click,
confirmed-detail button absence, failed storage in both failure modes, retry,
last-confirm undo, and stale finish references across restrictions, unknown,
pause and recreation. Existing savedSourceClear* tests are unchanged.

## Local checks

- `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.confirmNextFinish*'`
  attempted; exit 1. Gradle could not start: JAVA_HOME is unset and no `java`
  command exists on PATH. Test execution: **not run**; no pass claim.
- `bash android/gradlew -p android :app:testSampleDebugUnitTest :app:lintSampleDebug`
  **not run**, exit code N/A. Full regression/lint is left to GitHub CI as
  requested; the local Java blocker also prevents this command.
- `git diff --check`: run, exit 0; no whitespace errors.
- Python XML parse and focused source assertions: run, exit 0. These only check
  resources, shared candidate use and preserved click guards; they do not compile
  Java or execute the added tests.
- Final `git status --short --untracked-files=all`: only the four permitted paths.

Previous candidate CI results do not validate this change. No browser check was
needed for this Android-only behavior. Actual rendered screens, assistive
technology, APK build/install, emulator execution and real Pleos SDK callbacks
remain unverified. This JVM fixture work is not evidence of device integration,
actual Pleos execution or submission readiness. No workflow, build/toolchain,
credentials, license acceptance or signing changes were made.

## Follow-up after GitHub CI

[The initial 61466f3 candidate CI](https://github.com/jhuni17/before-arrival-pleos/actions/runs/42384865534)
ran 89 Android tests and reported one failure at the preferences comparison in
`confirmNextFinishRejectsUnsafeAndStaleReferences`. The fixture used unregistered
source IDs and a zero default duration, so catalog restoration removed the saved item.
A focused Java host check reproduced removal with the old data and preservation
with valid sample-source data. It does not exercise the activity lifecycle.

The follow-up derives the test items from registered sample records. It checks
persistence before and after stale clicks, then exercises a new explicit click.
Use the CI run for the follow-up SHA to verify the full Android tests, lint and
unsigned sample build.
