# Android saved-data migration repair

Only ContentCatalog and the existing CatalogTest regression were changed for this finding. Retired sources remain outside the active catalog and provider registry. Valid saved items from SOOP, DCInside and FM Korea are persisted as `historical_saved`, with migration-only provider/registration metadata for subsequent reloads. They cannot open links or import refresh snapshots. Existing item fields are retained.

Historical records must pass item bounds/provenance checks, explicit string/numeric type checks, and the retired provider's former HTTPS URL pattern for both registration and original URL. Unknown sources without validated historical metadata, malformed records, unsafe URLs and duplicates are rejected. This does not recover data already discarded by a prior reload.

Added `CatalogTest.retiredSavedRecordsSurviveRepeatedReloadWithoutRegistrationOrRefresh`: mixed current/sample/three-retired saved records, duplicate and invalid records, two constructor/persist cycles, current source order/enabled state and saved content, historical provenance/content, and no retired registration/source/link opening/snapshot import. The existing retired registration rejection test remains.

Validation in this environment:

- `git diff --check`: passed.
- `sh validation/sources-v2/verify-model.sh /tmp/android-all-api35.jar`: exit 127, `javac: not found`. The jar path was a placeholder, not a verified dependency; compilation never started. This model script does not execute CatalogTest.
- Added CatalogTest regression: not executed; Java/Javac are unavailable on PATH. No passing Android compilation or JVM test result is claimed.
- No full Gradle, browser, APK build, emulator, or actual Pleos SDK callback validation was run for this repair. No commit, push or merge was performed.
