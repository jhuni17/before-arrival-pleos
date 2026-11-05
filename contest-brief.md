# 도착 전에 · 일반 부문 기획서 본문 초안

## 1. 앱 한 줄 소개

출근길에 듣고 싶은 소식과 도착 전에 알아야 할 일을, 목적지까지 남은 시간에 맞춰 골라주는 차량용 개인 브리핑 앱이다.

## 2. 기획 배경

대상은 자차로 출퇴근하며 업무 일정과 취미 소식을 여러 앱에서 확인하는 직장인이다. 출근 전에는 일정과 관심사를 확인할 여유가 적고, 정체가 생기면 이동 시간은 길어진다. 사용자가 평소 찾아보는 소식과 오늘 도착 전에 알아야 할 정보를 한 번에 준비하면, 출근길을 더 즐겁게 보내면서 차에서 내린 뒤 할 일도 쉽게 파악할 수 있다.

## 3. 앱 주요 기능 및 차량 특화 요소

휴대폰에서 일정, 게임 공지, 커뮤니티 등 듣고 싶은 출처와 우선순위를 정한다. 차량 앱은 **사용자가 켠 출처, 목적지까지 남은 시간, 일정의 기한**으로 들을 목록을 구성한다. 기본 순서는 사용자가 정한다. 가까운 마감 때문에 순서가 달라지면 그 이유를 표시하고, 읽지 않은 소식은 다음 확인으로 미룬다.

Gleo와 Gemini는 차량에서 음성 요청과 개인화된 답변을 제공한다. 이 앱은 사용자가 정한 출처를 모아두고, 매번 같은 질문을 하지 않아도 경로와 마감에 맞춰 들을 목록을 구성하는 경험에 집중한다. 목적지가 없거나 경로를 읽지 못하면 그 상태를 표시하고 시간에 맞춘 편성은 보류한다.

## 4. 주요 사용 시나리오

운전자가 출근 전 ‘즐겨 하는 게임의 공식 패치’를 먼저 듣도록 설정한다. 오전 9시 팀 회의 장소가 바뀐 날, 회사까지 18분 남은 장면에서는 패치 소식과 일정 변경을 함께 본다. 4분 남은 장면에서는 곧 확인해야 할 회의 장소가 먼저 나오고, 게임 소식은 이어보기 후보에 남는다. 사용자가 저장한 소식은 도착 후 휴대폰에서 원문을 열 수 있도록 설계한다.

## 5. 개발 계획

**차량 프로토타입.** Android Automotive에서 가상 경로, 일정, 패치 소식, 커뮤니티 예시를 사용한다. 출처 선택과 사용자 우선순위를 보존하고, 항목별 음성 예상 길이와 일정 기한을 반영해 목록을 고른다. 주차 상태에서 한국어 읽어주기와 저장 흐름을 검증한다.

**Pleos 검증.** NaviHelper의 목적지 조회 API와 `NAVI_ROUTE` 권한을 사용한다. Connect Emulator에서 `DestinationInfo.remainTime`의 단위, 갱신과 오류를 실제 콜백으로 확인한다. 제한 상태에 들어가면 상세 화면과 TTS를 중단하는 주차용 버전을 먼저 확인한다. 주행 중 오디오 재생은 앱 분류와 오디오 포커스 요건을 검증하는 다음 단계에 둔다.

**휴대폰과 콘텐츠 연동.** 휴대폰 설정과 저장 항목을 계정 단위로 동기화한다. 첫 실제 콘텐츠 후보는 Steam의 게임별 공식 소식이다. 출처와 게시 시각을 유지하고, 조회에 실패했거나 갱신되지 않은 항목에는 상태를 표시한다. 실제 콘텐츠와 기기 동기화는 연동을 검증한 이후 시연에 포함한다.

## 6. 기대효과 및 발전 가능성

처음에는 게임 업데이트나 관심 뉴스를 이미 매일 확인하는 자차 통근자에 집중한다. 일주일간 소규모 파일럿을 진행해 앱을 다시 연 날의 수, 우선 항목을 확인하는 데 걸린 시간, 저장 항목 열람 비율을 기록한다. 참여자 중 다음 주에도 사용한 비율과 월간 활성 사용자 수를 초기 검증 지표로 삼는다. 출처 추가 요청과 실제 사용을 바탕으로 관심 뉴스, 개인 AI 작업 결과까지 확대한다.

## 7. 참고자료

시연 영상과 소스 저장소의 링크는 완성된 결과물의 접근 권한을 확인한 후 기재한다. 현재 제공물은 18초 HTML 자동 시연이며 녹화 영상은 없다. 아래 40초 구성은 향후 영상 편집안이다. HTML 저장은 같은 브라우저에서만, Android 저장은 해당 앱에서만 유지되며 두 기기 사이 동기화는 연출이다. 차량 원본 작업은 sample debug APK 빌드 로그를 남겼지만 실제 Pleos SDK·콜백·에뮬레이터 실행은 미검증이다. [현재 근거](integration-notes.md)를 참조한다.

### 40초 영상 편집안 · 미제작

- **0~8초.** 출근 전 휴대폰에서 ‘게임 패치’를 먼저 듣도록 고른다.
- **8~19초.** 주차한 차량 화면에서 18분 남은 예시를 연다. 게임 패치와 일정이 함께 표시된다.
- **19~29초.** 회사까지 4분 남은 예시로 전환한다. 곧 처리해야 할 일정 변경과 미뤄진 소식이 구분된다.
- **29~40초.** 도착 후 휴대폰에서 저장한 게임 소식을 다시 본다. 마지막 프레임에서 앱 이름과 구현 범위를 보여준다.

## 8. 숏폼 링크

숏폼을 별도로 게시할 경우 기재한다.

## 참고 자료

- [공모전 심사 기준](https://appdevchallenge.auto-hmg.io/guide/judging-awards)
- [공식 접수양식 DOCX](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx)
- [Gleo AI 사용법](https://ownersmanual.hyundai.com/ivi/Connect-S/AVNT/KOR/Korean/003_Corefeatures_1.html)
- [Gemini 차량 개인화](https://blog.google/products-and-platforms/platforms/android/android-auto-cars-with-google-built-in-gemini-personal-intelligence/)
- [Pleos NaviHelper 목적지 정보](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [Pleos NaviHelper 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [Pleos 주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Steam 게임 소식 API](https://partner.steamgames.com/doc/webapi/ISteamNews?language=english)
