# Android saved unread filter — local validation

Base HEAD: b5d29a352f9f7339b571c345f411faa495c814df. Changes are uncommitted.

- PASS: `git diff --check`.
- BLOCKED before test execution: `sh android/gradlew -p android --offline --no-daemon --console=plain :app:testSampleDebugUnitTest --tests '*MainActivityTest'`.
- Gradle reports JAVA_HOME is not set and no java command exists in PATH.
- Added MainActivity JVM coverage: mixed saved order and total counts, selection/accessibility, unchanged narration and detail notice, empty library, all confirmed, failed confirmation and retry, reversal, recreation, restriction/unknown/pause guards, and no automatic dialog or speech on return. Existing official URL test also asserts opening does not confirm.
- Tests have not executed locally. CatalogTest and SpeechPlaybackTest are unchanged. Full regression, sampleDebug lint/assemble, and showcase browser regression are left to GitHub CI; no new CI result is available for this uncommitted tree.
- No browser check or capture was run because showcase behavior is unchanged.
- No APK build, installation, emulator run, or actual Pleos SDK callback was verified. This is not submission-readiness evidence.

## Working-tree SHA-256

- `android/app/src/main/java/com/beforearrival/briefing/MainActivity.java`: `929325491070efbe6ff92bc956f62c676d78a0f65205ccff6192ea040f74f9dd`
- `android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java`: `1b9a46f4fca7806ac8c387693ca2273288f9151e215e585b378c7a6cae339b12`
