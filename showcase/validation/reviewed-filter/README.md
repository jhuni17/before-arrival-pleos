# 확인 완료만 필터 검증

브라우저 샘플 보관함의 확인 상태 필터를 검증한다. APK 설치·에뮬레이터 실행·실제 Pleos SDK 콜백이나 제출 준비 완료의 근거가 아니다.

- `npm run build`, `npm run verify`
- `EVIDENCE_DIR=validation/reviewed-filter node reviewed-filter-browser-verify.mjs`
- `EVIDENCE_DIR=/tmp/before-arrival-review-undo node review-undo-browser-verify.mjs`

브라우저는 runner의 CHROMIUM_PATH를 사용하며, 없으면 ~/.local/share/before-arrival-runner/bin/chromium을 명시한다. 전체 회귀 체인은 GitHub CI에 맡기며 기존 verify:saved-undo → verify:review-undo 체인에 새 검사를 연결했다.

report.json은 기준 후보 HEAD SHA와 수정 중인 소스·생성 HTML·캡처 SHA-256을 기록한다. 커밋하지 않은 작업 트리의 검증이므로 후보 SHA만으로 변경 결과를 식별하지 않는다. 주야간 320×960·390×1080 캡처는 합성 32건 fixture이며 원문 URL을 요청하지 않는다. Asta Sans/OFL, 샘플 경로, 브라우저 저장 및 기기 간 비동기화 안내를 유지한다.
