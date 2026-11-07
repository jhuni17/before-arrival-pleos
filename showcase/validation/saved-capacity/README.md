# 저장 한도 집중 검증

기준 후보: `3e46755f8a0bd136dd003354ab49b17913a8ddbf`. 커밋되지 않은 수정본을 검증했다. `report.json`에 소스·생성 HTML·주야간 캡처 SHA-256을 기록한다.

- 기준 후보의 유효한 서로 다른 200건에 신규 저장: 201건 → 다시 읽으면 200건으로 잘리는 현상을 재현했다.
- 수정본: 199→200 저장과 새로고침 유지, 가득 찬 저장·복원 거절 시 쓰기 없음, 중복 저장 시 쓰기 없음, 직접 삭제 후 키보드 재시도, 복원 대기 유지와 공간 확보 후 복원, 저장소 실패 시 데이터·초점 보존을 확인했다.
- restricted/unknown 진입 및 주차 복귀 후 이전 저장·삭제·복원 핸들러가 무효이며 재생이 중지되고 자동 작업이 없음을 확인했다.
- 주야간 320×960·390×1080에서 안내·키보드 초점·가로 넘침 없음·도착 첫 항목을 확인했다. 캡처는 한도 오류 장면이다.

실행 (showcase 디렉터리):

```sh
node build.mjs
node verify.mjs
CHROMIUM_PATH="${CHROMIUM_PATH:-$HOME/.local/share/before-arrival-runner/bin/chromium}" node saved-capacity-browser-verify.mjs
```

전체 showcase 회귀 검사는 GitHub CI에 맡겼다. CI 결과를 새로 확인한 것은 아니다. 이 검사는 샘플 경로·브라우저 저장 검사이며 Android 설치, 에뮬레이터, 실제 Pleos SDK 실행의 근거가 아니다. Asta Sans/OFL과 브라우저 한정·기기 간 비동기화 안내를 유지했다.
