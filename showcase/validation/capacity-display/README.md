# 보관함 전체 용량 표시 집중 검증

기준 후보: `7063d0200907090c52f5db810cec600ecbf4c262`. 커밋하지 않은 작업 트리를 검증한다.
`report.json`은 기준 후보 SHA와 검증 소스·생성 HTML·캡처 SHA-256을 연결한다.

실행 위치: showcase

```sh
node build.mjs
node verify.mjs
CHROMIUM_PATH="${CHROMIUM_PATH:-$HOME/.local/share/before-arrival-runner/bin/chromium}" node capacity-display-browser-verify.mjs
```

집중 범위: 0·199·200건의 전체 용량, 검색·출처·확인 필터 독립성과 새로고침, 저장·삭제·복원, 중복·한도·쓰기 실패 시 데이터 보존, 직접 삭제 후 수동 재시도, restricted/unknown의 이전 핸들러 무효화와 재생 중지.
주야간 320×960·390×1080에서 기본 도착 화면의 제목·첫 항목 제목 전체 노출과 가로 넘침, 가득 찬 용량 안내를 확인한다. 저장 거절 안내 화면은 별도 캡처한다.

전체 showcase 회귀 검사는 GitHub CI에 맡긴다. 이 실행은 CI 통과를 확인한 것이 아니다.
샘플 경로와 브라우저 저장에 대한 검증이며 APK 빌드·설치·에뮬레이터·실제 Pleos SDK 실행의 근거가 아니다.
Asta Sans/OFL 및 브라우저 한정 저장·기기 간 비동기화 안내를 유지한다.
