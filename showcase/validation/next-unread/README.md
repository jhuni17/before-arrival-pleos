# 다음 미확인 소식 검증

기준 후보: `1b17033b02956a58e219b40e88fc9a74c2ef2650` + 커밋하지 않은 작업 트리 변경.
`source-report.json`에 소스·생성 HTML·캡처 12개의 SHA-256과 검사 결과를 보관한다.

통과한 검사:
- `npm run build`
- `npm run verify`
- `EVIDENCE_DIR=validation/next-unread node saved-source-browser-verify.mjs`
- `EVIDENCE_DIR=/tmp/next-unread-arrival npm run verify:arrival`
- `EVIDENCE_DIR=/tmp/next-unread-regression npm run verify:saved-undo`
- `git diff --check`

새 검사는 기존 `verify:saved-undo` → `saved-source-browser-verify.mjs` 경로에서 실행된다. CI 설정 변경 없음.
필터 교집합, 미확인 0·1·여러 건, 32건 목록, 순환·제목 초점, Enter/Space, 저장 데이터·순서·URL 불변, 제한 전환과 오래된 핸들러, 주야간 320·390px, 모션 감소를 검증했다.
초기 도착 화면 검사에서 첫 제목이 밀리는 문제를 발견해 버튼을 필터 줄로 옮겼고, 최종 회귀 묶음은 통과했다. 초기 페이지 로드/시연 대기 시간 초과 이후 최종 검사는 순차 실행했다.

브라우저 샘플 검증에 한정한다. APK 설치·에뮬레이터·실제 Pleos SDK 실행 또는 제출 준비 완료의 증거가 아니다.
