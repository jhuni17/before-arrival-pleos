# 숨겨진 지난 위치 복귀 검증

기준 후보: `8e8695ee7f8fe0103bf4c91b15a3a405b06a6df9` + 미커밋 작업 트리 변경. `saved-position-report.json`은 검증 소스 5개, 생성 HTML, 캡처 16개의 SHA-256을 이 기준 SHA와 연결한다.

실행 위치: `showcase/`

```sh
npm run build
CHROMIUM_PATH="${CHROMIUM_PATH:-$HOME/.local/share/before-arrival-runner/bin/chromium}" EVIDENCE_DIR=validation/position-reveal node saved-position-browser-verify.mjs
```

결과: PASS. 검색·출처·확인 필터 각각 및 7가지 조합, 삭제/없는/표시 중 대상의 버튼 숨김, 필터 저장 실패 안내와 현재 화면 적용, 저장 데이터 보존, 새로고침 후 자동 이동 없음, 키보드 Enter/Space, reduced-motion, restricted/unknown 전환과 이전 핸들러·대기 이동 무효화를 확인했다. 주야간 320×960·390×1080에서 가로 넘침 없음과 도착 제목·첫 항목 제목의 첫 화면 전체 표시를 확인했다. 첫 실행의 320px 제목 잘림은 모바일 배치 보정 후 재검증하여 통과했다.

전체 회귀 검증은 GitHub CI에 맡긴다. 이번 작업에서는 CI 실행 결과를 새로 확인하지 않았다. 브라우저 샘플 검증이며 APK 설치·에뮬레이터 실행·실제 Pleos SDK 콜백·제출 준비 완료의 증거가 아니다.
