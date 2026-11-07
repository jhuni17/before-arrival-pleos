# Android saved confirmation — local validation

Base HEAD: d9d2bc2940d38ba78b2edf3ebe87245a4c1d4bcd
Changes are uncommitted; no modified commit SHA exists. Source hashes identify this working tree.

- PASS: git diff --check.
- BLOCKED: focused CatalogTest, MainActivityTest, SpeechPlaybackTest; lintSampleDebug; assembleSampleDebug.
- Command: sh android/gradlew -p android --offline --no-daemon --console=plain :app:testSampleDebugUnitTest --tests "*CatalogTest" --tests "*MainActivityTest" --tests "*SpeechPlaybackTest" :app:lintSampleDebug :app:assembleSampleDebug
- Gradle exited before execution: JAVA_HOME is not set and no java command exists in PATH. No JDK found in /home/ubuntu, /tmp, /usr/local, or /usr/lib/jvm.
- Full regression and showcase browser regression deferred to GitHub CI; no browser capture produced.
- No APK, emulator, actual Pleos SDK callback, or submission-readiness evidence was produced.

Added JVM cases cover existing/legacy unread defaults, keyed saved-only state, failure and explicit retry, reload/recreation, reversal, removal/resave, unchanged content/order/planning, and stale handlers after restricted/unknown/pause transitions. These cases have not run locally.

## SHA-256

- android/app/src/main/java/com/beforearrival/briefing/ContentCatalog.java: b982a589e5885f92cfbec7be5dbcb0adb7c7ff494472422967ed6dd57ff61a51
- android/app/src/main/java/com/beforearrival/briefing/MainActivity.java: 7bfbe9df9e7d6a77e6301e48e42deba8dfdc11b2409b09f6a3ddb177418bbfea
- android/app/src/test/java/com/beforearrival/briefing/CatalogTest.java: b55ac944343dd9c246dff798c2fd3b5d194d7efa5b8cc4a9dbc4f296117bd8be
- android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java: a87872297484f73330f7d6dedb84417644b74e60acd0cdecb0d30d0e59458f99
