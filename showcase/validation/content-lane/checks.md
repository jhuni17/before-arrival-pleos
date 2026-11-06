# Content lane checks — 2026-11-06

- `cd showcase && npm run build`: passed; generated HTML rebuilt.
- `cd showcase && npm run verify`: passed (see model.txt). Covers selection/order/enabled state, ETA planning, saved/reload, restricted/unknown UI guards, Steam captured-response replay and rejection cases. Added mixed retired/current v2 records, unaffected saved content/order/enabled preservation, repeated reload, retired registration rejection and unsafe archived URL rejection.
- `git diff --check`: passed.
- Android `sh validation/sources-v2/verify-model.sh /tmp/android-all-api35.jar`: blocked, exit 127: `javac: not found`. No JDK is exposed on PATH; the API35 jar argument is a placeholder, not a verified installed dependency. No Android compilation, JVM test success, APK build, emulator run or SDK callback is claimed. CatalogTest now checks the shared seven-provider picker and retired-provider rejection; it has not run here.
- Full browser suite intentionally not run while the Oracle main loop is active. These are model/static checks, not new browser/network evidence.
- Existing signed APK input identity no longer applies: shared/catalog.json changed. Prior artifact evidence remains historical.
- Credential/terms gaps and the single bounded summary follow-up are documented in sources-notes.md using the existing dated official-source research. No new live integrations or external credentials were used.
