# Arrival spacing validation

Candidate base: `ee95a0b6ea5926ff1ab1b5b2cbd9b49749483303`. Tested with an uncommitted CSS fix and regenerated HTML; no new commit was created. `validation-report.json` ties this working revision to source, HTML, reports and capture SHA-256 hashes. Historical reports elsewhere were not rewritten.

Only the narrow arrival pocket top padding changes from 20px to 4px. Fonts, controls, counts, guidance and application behavior are unchanged. The existing official-source registration → 4-minute save → arrival test passes in both themes with scrollY=0: the first title ends at 951.171875px on 320×960 and 901.171875px on 390×1080. Empty, opening and desktop regressions pass. Source counts, 32 items, duplicate long names, keyboard, review/delete/undo/reload and stale-handler safety checks pass.

The complete existing CI showcase chain passed; see `checks.txt`. Browser checks used the runner CHROMIUM_PATH. Arrival and source-count reports and their captures are retained here; other suite captures were temporary. No assertions or test scripts changed.

These are browser sample/replay and local storage checks, without cross-device synchronization. Asta Sans/OFL assets and guidance remain unchanged. Android build, installation, emulator and actual Pleos SDK callbacks were not tested in this CSS-only run; this is not submission-readiness evidence.
