# Pleos 시연 디자인 근거

기준 커밋: `94fa9ca1324029c07a8cee25b7771bb4f007e215` (`main`, 작업 시작 시 원격과 일치).
작업 브랜치: `codex/showcase-pleos-redesign-20261105`.
조회일: 2026-11-05, Asia/Seoul. 변경 범위는 `showcase/`다.

## 화면 구성

[공식 소개](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)의 즉시성과 대비 원칙, [Display Layout](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout)의 Application window·Status bar·GNB 구분을 적용했다. 시스템 영역은 별도 배경과 실선, 앱 끝은 점선·명시적 라벨로 구분한다. 시스템 버튼은 시연용 비조작 텍스트다. 실제 차량 상태·공조·내비게이션 UI로 주장하지 않는다.

차량 화면을 자르거나 휴대폰으로 덮지 않는다. ETA와 이번 브리핑 1건을 양쪽에 놓고 큰 기본 동작 하나와 낮은 강조의 저장 동작을 제공한다. 760px 이하에서는 세로로 재배치한다. 휴대폰은 관심사 설정과 도착 후 확인 장면에서만 배치하며 좁은 화면에서는 차량 아래로 이동한다. 문장은 높이 고정·말줄임 없이 자연스럽게 늘어난다. Stageshow `Stage`의 시퀀스와 축소 모션 poster를 사용하되, 이 시연의 CSS에서 고정 스테이지 크기·축소 transform을 해제해 글자 확대 시 재배치를 유지한다. `kit/` 원본은 수정하지 않았다.

## 글꼴과 배포

[공식 Typography](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography)가 System UI 폰트로 안내한 **Asta Sans**를 모든 화면 기본 글꼴로 사용한다. 한국어·영문 지원, 가변 weight 300–800. 원본 TTF, Google Fonts의 `METADATA.pb`, OFL, 출처 설명을 `assets/fonts/`에 동봉했다. HTML에는 폰트와 OFL 전문을 내장하여 네트워크 없이 열 수 있다.

- [Google Fonts 원본 디렉터리](https://github.com/google/fonts/tree/main/ofl/astasans)
- [공식 upstream 42dot/Asta-Sans](https://github.com/42dot/Asta-Sans)
- [동봉한 OFL의 공식 소스](https://github.com/google/fonts/blob/main/ofl/astasans/OFL.txt)
- `METADATA.pb`가 명시한 upstream 소스 커밋: `62b8301ac4b2e2ddf62eadca79b91fa944618848`.
- 원본 폰트를 수정하지 않았다. OFL은 웹 임베딩·앱 번들·문서/소프트웨어와 함께 배포할 수 있게 하며, 폰트 자체 단독 판매 금지, 저작권·라이선스 유지, 수정본의 Reserved Font Name 조건을 포함한다. 실제 동봉된 OFL이 기준이다.

현대차의 정확한 모빌리티 제품명은 **Hyundai Sans UI**다. **Hyundai Sans** 브랜드 서체와 구분한다. [현대차 공식 발표](https://www.hyundai.com/content/hyundai/worldwide/en/newsroom/detail/0000000287.html)로 제품명과 용도를 확인했지만, 이 저장소에 별도 제공된 폰트나 APK·웹 임베딩·제출물 재배포를 허용하는 이용조건은 없다. 따라서 적용하거나 번들하지 않았다.

## 주간·야간 역할 매핑

출처: [Pleos Colors의 System Color](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors). Background, Surface, Text, Button 역할을 아래 CSS 변수로 매핑했다. 아래 HEX는 이 시연에서 정한 불투명 색상값이다. 공식 이미지의 개별 토큰명/HEX를 그대로 추출한 값으로 주장하지 않는다.

| 역할 | 실제 CSS 토큰 | 주간 | 야간 | 적용 |
| --- | --- | --- | --- | --- |
| Background | `--background` | `#eef0f3` | `#111214` | 페이지·시스템 영역 |
| Surface | `--surface` | `#ffffff` | `#1d1f23` | 차량 앱·휴대폰 |
| Surface (raised) | `--raised` | `#e7eaf0` | `#282b30` | 리드 카드·설정 필드 |
| Text (primary) | `--text` | `#171a1f` | `#f5f6f7` | 제목·ETA·주요 아이콘 |
| Text (secondary) | `--muted` | `#505866` | `#b7bec8` | 설명·상태·출처 |
| Button (primary) | `--button` | `#20252d` | `#f5f6f7` | 기본 동작·선택 장면 |
| Button (on-primary) | `--on-button` | `#ffffff` | `#16181c` | 기본 버튼 글자·아이콘 |
| 구분선 | `--line` | `#808996` | `#565c66` | 앱 경계·시스템 구분 |

주야간 전환은 HTML의 수동 컨트롤이다. 차량 조도/SDK 콜백 연결을 의미하지 않는다. 글자와 주요 재생·저장·확인 SVG는 실제 렌더링된 전경색과 배경색으로 측정한다. 상세 결과와 최소 대비는 `evidence/browser-report.json`에 기록한다. 장식용 경로선과 테두리는 정보 텍스트 대비 측정 대상과 구분한다.
