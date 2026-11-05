# 도착 전에 · 브라우저 시연 검증

기준 커밋은 `94fa9ca1324029c07a8cee25b7771bb4f007e215`다. `main`과 같은 커밋에서 독립 브랜치 `codex/showcase-pleos-redesign-20261105`를 시작했다. `android/`는 변경하지 않았다. 통합 범위와 공통 샘플은 [INTEGRATION.md](INTEGRATION.md), 공식 디자인·폰트·색상 역할의 근거는 [DESIGN-SOURCES.md](DESIGN-SOURCES.md)에 있다.

## 대표 정지 화면을 먼저 확인

기존 산출 HTML을 교체하기 전에 새로운 차량 중심 소스를 `evidence/representative-preview.html`로 임시 빌드하고 Chromium에서 확인했다. 1440px 폭에서 남은 시간, 브리핑 제목, 큰 기본 동작 순으로 위계를 확인한 [대표 캡처](evidence/representative-opening.png)를 보존했다. 임시 중복 HTML은 제거했다. 최종 HTML은 별도로 다시 빌드했다.

## 시연 순서와 실제 동작

`Stage`의 자동 재생은 18초 분량이다. 최종 상태에서 멈추며 다시 보기와 장면별 수동 탐색을 제공한다.

| 구간 | 장면 | 실제/연출 |
| --- | --- | --- |
| 0–5초 | 회사까지 18분, 선택한 게임 업데이트 | 샘플 경로·콘텐츠 |
| 5–8.5초 | 저장 완료 | 자동 시연 연출, localStorage 변경 없음 |
| 8.5–13.5초 | 4분, 9시 회의 장소 변경 우선 | 샘플 ETA·기한·일정 |
| 13.5–18초 | 도착 후 휴대폰 보관함 | 보조 화면·기기 간 연동 연출 |

관심사 설정 화면은 별도 장면 버튼으로 연다. 출처 선택과 기본 순서를 같은 브라우저에 실제 보존한다. 모든 출처를 끄면 재생을 비활성화하며, 게임·일정·커뮤니티 단독 선택과 게임/일정 우선순위 변경을 지원한다. 4분에서는 켜 둔 일정이 먼저다.

사용자가 차량의 저장 버튼을 직접 누르면 현재 항목 제목을 실제 localStorage에 저장한다. 새로고침 후 유지되고, 도착 후 확인 화면에 그 제목을 표시한다. 별도 브라우저/기기에는 전달하지 않는다. 저장 공간이 거부되면 성공 표시 대신 실패 안내를 표시한다. 자동 시연의 저장 완료는 실제 저장과 라벨을 구분한다.

기본 재생 버튼은 상태 연출이며 실제 음성은 없다. 주행 제한·상태 미확인에서는 화면 동작 두 개를 비활성화하고 재생 상태를 초기화한다. 앱 밖 장면 선택 버튼은 검증용 컨트롤이다. 시스템 Status bar·GNB는 앱 경계와 분리된 예시다. APK, 실제 Pleos 실행, SDK 콜백, 오디오 포커스, TTS를 검증한 자료로 해석하지 않는다.

## 공식 게임 소식 연결

`scripts/check-steam.py`는 Steam Dota 2 공식 공지 피드를 한 번 조회하고 성공 시 제목·원문 URL·게시 시각을 기록한다. 이 환경에서는 HTTPS 프록시의 목적지 제한으로 `403 Forbidden`이 발생했다. [조회 기록](evidence/steam-source.json)에 정확한 UTC 조회 시각, 요청 URL, 실패 원인과 샘플 fallback을 남겼다. 기록 시각의 한국 시간은 2026-11-05 20:26:11이다.

HTML은 가상 제목 ‘이번 주말, 협동 모드가 열려요’를 사용하며 게시 시각과 개별 원문을 만들어 넣지 않는다. 휴대폰의 링크는 [공식 소식 허브](https://store.steampowered.com/news/app/570)이며 이 샘플의 원문이라고 주장하지 않는다. 일정·커뮤니티 저장 항목에는 게임 허브 링크를 붙이지 않는다. HTML은 런타임에 게임 API를 호출하지 않는다.

## 재현 명령

저장소 루트에서:

```sh
cd showcase
npm ci
npm run build
npm run verify
CHROMIUM_PATH=/usr/bin/chromium npm run verify:browser
python3 scripts/check-steam.py
```

Node 24.22.1, Chromium 155.0.8059.79, Playwright Core 1.57.0에서 실행했다. Node 22 이상에서도 빌드/정적 검증을 실행할 수 있도록 기존 Node22 target을 유지했다. `playwright-core`는 브라우저 실행 파일을 내려받지 않으므로 Chromium이 별도 설치되어 있어야 한다. 브라우저 스크립트는 실행 동안 임시 로컬 HTTP 서버를 열고 종료 시 닫는다. 캡처와 보고서는 `evidence/`에 갱신된다.

완성본 `before-arrival-showcase.html`에는 JavaScript, CSS, Asta Sans 원본 TTF, OFL을 내장했다. 사용자 브라우저에서는 별도 서버 없이 열 수 있다. 이번 관리형 Chromium은 `file://` 탐색을 정책으로 차단하므로 브라우저 검증은 로컬 HTTP에서 수행했다. 저장은 동일 origin/파일의 브라우저 저장소 가용성에 따른다.

정지 화면 파라미터:

```text
?state=opening
?state=saved
?state=priority
?state=arrived
?state=settings
?state=restricted
?state=unknown
?state=priority&theme=day&long=1
```

`theme=day`/`night`는 테마, `long=1`은 긴 회의 제목 스트레스 검증이다. 축소 모션 환경의 자동 재생은 도착 후 정지 poster를 유지하고, 수동 장면 선택은 가능하다.

## 검증 근거

`npm ci`, `npm run build`, `npm run verify`를 통과했다. 정적 검증은 7개 장면의 핵심 정보, 휴대폰 노출 범위, 두 제한 상태의 버튼 비활성화, 시연 순서를 검사한다.

[브라우저 상세 보고서](evidence/browser-report.json)는 최종 HTML을 Chromium에서 실행해 다음을 검사한다.

- 주간/야간 × 7개 장면: 14개 캡처.
- 320px·390px × 주간/야간 × 긴 회의 제목·도착·설정: 12개 캡처.
- 844×390 가로 화면, 양 테마: 2개 캡처.
- 모든 computed 글자 크기를 200%로 확대, 1440px·390px, 양 테마: 4개 캡처. 브라우저 확대율을 바꾼 테스트와 구분한다.
- 축소 모션 poster: 1개 캡처. 5.5초 뒤에도 자동 전환하지 않는지 확인.

총 33개 최종 캡처와 별도 대표 화면 1개를 보존했다. 텍스트와 주요 SVG 아이콘은 실제 전경/불투명 배경으로 대비를 계산하며 최저 **5.95:1**로 4.5:1을 넘었다. 문서의 수평 스크롤, 텍스트 자체의 수평 넘침, 모든 clipping ancestor에서의 글자 잘림이 없음을 검사했다. 세로 스크롤은 작은 화면·확대에서 허용한다. 일반 390px 회의 장면과 주간 도착 장면, 200% 확대 390px 캡처를 이미지를 직접 열어 시각 확인했다.

브라우저 동작 검증은 저장·설정 새로고침 보존, 다른 브라우저 컨텍스트의 비동기화, 출처별 선택, 기본 순서와 4분 우선순위, 재생→제한 전환, 저장 거부 안내, 실제 Stage 타임라인을 포함한다. 자동 저장 연출이 localStorage를 변경하지 않는 것도 확인했다. 브라우저 page error 및 외부 네트워크 요청이 없어야 통과한다.

자동 접근성 인증이나 실차의 물리적 글자 크기 검증을 의미하지 않는다. 공식 색상 역할에 연결한 이 시연의 HEX와 Asta Sans 출처는 `DESIGN-SOURCES.md`에서 확인한다.
