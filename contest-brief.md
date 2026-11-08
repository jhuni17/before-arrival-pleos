# 도착 전에 · 일반 부문 기획서 본문 초안

## 1. 앱 한 줄 소개

출근길에 듣고 싶은 소식과 도착 전에 알아야 할 일을, 목적지까지 남은 시간에 맞춰 골라주는 차량용 개인 브리핑 앱이다.

## 2. 기획 배경

대상은 자차로 출퇴근하며 업무 일정과 취미 소식을 여러 앱에서 확인하는 직장인이다. 출근 전에는 일정과 관심사를 확인할 여유가 적고, 정체가 생기면 이동 시간은 길어진다. 사용자가 평소 찾아보는 소식과 오늘 도착 전에 알아야 할 정보를 한 번에 준비하면, 출근길을 더 즐겁게 보내면서 차에서 내린 뒤 할 일도 쉽게 파악할 수 있다.

## 3. 앱 주요 기능 및 차량 특화 요소

사용자가 일정, 게임 공지, 채널, 커뮤니티 등 듣고 싶은 출처와 우선순위를 정한다. 현재 검토본에서는 Android와 브라우저에서 각각 출처를 등록·사용 설정·정렬하고 실제 편성에 반영한다. 휴대폰 모형은 브라우저 안에서 동작하며 두 앱의 설정·저장은 동기화하지 않는다. 차량 앱은 **사용자가 켠 출처, 목적지까지 남은 시간, 일정의 기한**으로 들을 목록을 구성한다. 기본 순서는 사용자가 정한다. 가까운 마감 때문에 순서가 달라지면 그 이유를 표시하고, 읽지 않은 소식은 다음 확인으로 미룬다.

Gleo와 Gemini는 차량에서 음성 요청과 개인화된 답변을 제공한다. 이 앱은 사용자가 정한 출처를 모아두고, 매번 같은 질문을 하지 않아도 경로와 마감에 맞춰 들을 목록을 구성하는 경험에 집중한다. 목적지가 없거나 경로를 읽지 못하면 그 상태를 표시하고 시간에 맞춘 편성은 보류한다.

## 4. 주요 사용 시나리오

운전자가 출근 전 ‘즐겨 하는 게임의 공식 패치’를 먼저 듣도록 설정한다. 오전 9시 팀 회의 장소가 바뀐 날, 회사까지 18분 남은 장면에서는 패치 소식과 일정 변경을 함께 본다. 4분 남은 장면에서는 곧 확인해야 할 회의 장소가 먼저 나오고, 게임 소식은 이어보기 후보에 남는다. 사용자가 저장한 소식은 도착 후 휴대폰에서 원문을 열 수 있도록 설계한다.

## 5. 현재 구현과 남은 검증

**차량 프로토타입.** 공통 출처 모델로10종의 공식 게임·채널·게시판 주소를 등록·활성화·정렬한다. 원래 일정·게임·커뮤니티 예시와 이전 설정·저장을 보존한다. 켜 둔 출처의 확인된 소식만 ETA 예산과 일정 마감에 따라 편성하고, 보류 항목은 사용자가 직접 저장한다. Android 저장은 해당 앱, 브라우저 저장은 같은 브라우저에만 남는다. 한국어 음성·제한 전환의 실기 검증은 남아 있다.

**공식 콘텐츠.** LoL·Valorant·메이플 공식 원문3건을 게시일·원문·확인 시각이 있는 가져온 스냅샷으로 제공한다. 별도로 Steam 한 제공자는 허용된 외부 실행 환경에서 실제 HTTPS→앱의 동일 갱신 함수·파서·모델→저장·디스크 재조회까지5개 공지를 처리했다. 전송은 curl 기반 fetcher가 담당했다. 후속 실제 브라우저 UI 조회에서 CORS 차단을 확인해 로컬 앱 서버의 동일 출처 조회로 수정했다. 후속6db 서버에서는 실제 브라우저 UI 조회·5개 항목·저장/재로드를 통과했다. 최신 도착 배치는 화면·독립 검토 근거를 따르며 Android 실기 네트워크는 미검증이다. 나머지 출처는 연결 설정 필요 또는 링크 저장 상태로 관리한다.

**현재 후보 빌드 근거.** 후보 `21c83fdf61768eba7407922791a82f290ac766ea`는 제공된 승인 보고서에 따라 [CI #42477729327](https://github.com/jhuni17/before-arrival-pleos/actions/runs/42477729327)의 실제 Pleos SDK 컴파일·unsigned debug APK 빌드 성공으로 기록한다. 이는 CI 빌드 환경의 근거이며 설치·signed release·현재 후보 런타임 성공을 뜻하지 않는다. 이번 문서 대조에서는 CI를 재조회하거나 빌드하지 않았다.

**과거 실제 실행 관찰.** `7bda357f3f6f2a286fa163aa11bf8eacfdbec3d2`의 Windows x64/WHPX·Pleos Connect 2.1.0/API 34·NaviHelper 3.0.5에서 원시 `remainTime` 콜백 `1920`·`1860`·`1680`을 관찰했다. 취소 후 앱을 다시 열어 새 조회한 값은 `0`이었다. 당시 앱은 보호 로직으로 ETA를 표시하지 않고 ‘도착 시간 미확인’을 표시했다. 같은 세션의 유효 ETA 소거를 입증한 기록이 아니다. [Windows 관찰 기록](android/validation/pleos-emulator/windows-pleos-2.1.0.md)과 [세션 JSON](android/validation/pleos-emulator/runtime-evidence.json)을 따른다.

`dc6cf91d678c3a7cb6ad38cbbeabb3c3c2a88ebc`의 Windows Pleos Connect 2.1.0/API 34에서는 샘플 경로 18분과 eSpeak NG 1.52.0으로 D 상태 재생·다음 주제·일시정지·이어듣기를 관찰했다. Gleo 스키마 읽기와 같은 UID 계측 명령 전달도 확인했으나 실제 한국어 Gleo 발화 성공은 아니다. [샘플 오디오 큐 시험](android/validation/pleos-emulator/commute-audio.md)은 현재 후보의 실측과 구분한다.

**차량 신호에서 사용자 결과까지 · 현재 소스 구현.** [NaviTimeContract.decode](android/app/src/main/java/com/beforearrival/briefing/NaviTimeContract.java)는 위 세션 JSON의 확인된 fingerprint와 정확히 일치하는 debug 앱에서만 원시 ETA를 초로 해석한다. release·다른 이미지는 거부하며 null·0 이하·2시간 초과도 거부한다. 이는 SDK 전체 범위 보증이 아닌 debug 시험 계약이다. [ContentCatalog.listeningBudget와 plan](android/app/src/main/java/com/beforearrival/briefing/ContentCatalog.java)은 남은 초에서 도착 준비 180초를 빼고 5로 나눈 값에 0~45초 한도를 적용해, 켠 출처와 일정 마감·추정 길이로 편성한다. 18분→45초, 4분→12초 예산은 구현·샘플 시나리오이며 실제 Pleos에서 18분→4분 변화를 관찰했다는 뜻이 아니다. [BriefingMediaService.onLiveRoute](android/app/src/main/java/com/beforearrival/briefing/BriefingMediaService.java)는 예산 0 또는 15초 이상 오래된 경로에서 활성 브리핑을 종료하고, 유효 갱신 시 재생 마감을 단축한다. ‘남은 시간에 맞춰 듣고 경로가 무효해지면 멈춤’은 현재 소스 설명이며 현재 후보의 런타임 입증은 남았다. 실제 입력 실패를 샘플 ETA로 대체하지 않는다.

**남은 검증과 안전 범위.** 편성과 재생 시작은 차량 화면 사용 가능 상태에서 한다. 주행 제한 중 설정·대본 화면은 잠그고 Pleos의 활성 오디오 큐만 차량 미디어로 제어한다. 차량 상태 미확인 시 화면 상호작용과 음성을 중지한다. 현재 후보의 실제 ETA 표시→예산·편성→재생·경로 취소, 재생 중 UNKNOWN 전환은 Windows 실측이 남았다. 실제 Gleo 발화, 음향 시험·오디오 포커스, signed release 빌드·설치와 최종 영상도 미완료다. 합성 로그·mock 검사는 소스 검사용이며 실제 SDK 실행 근거와 구분한다. `27d297b`의 서명 sampleDebug APK·독립 다운로드·JVM 24건은 [과거 고정 소스 기록](sources-notes.md)으로 유지한다.

## 6. 기대효과 및 발전 가능성

처음에는 게임 업데이트나 관심 뉴스를 이미 매일 확인하는 자차 통근자에 집중한다. 일주일간 소규모 파일럿을 진행해 앱을 다시 연 날의 수, 우선 항목을 확인하는 데 걸린 시간, 저장 항목 열람 비율을 기록한다. 참여자 중 다음 주에도 사용한 비율과 월간 활성 사용자 수를 초기 검증 지표로 삼는다. 출처 추가 요청과 실제 사용을 바탕으로 관심 뉴스, 개인 AI 작업 결과까지 확대한다.

## 7. 참고자료

사용자 제공 접수 조건에 따르면 **1차는 기획서와 서명 동의서가 필수**다. 프로토타입 및 자격을 충족하는 GitHub/APK 제출은 선택이며, **최종 PT에는 동작 앱 시연이 필요**하다. 선택 APK의 빌드 성공이 최종 시연이나 제출 자격 확인을 대신하지 않는다.

현재 제공물은 18초 HTML 자동 시연이며 녹화 영상은 없다. 아래 40초 구성은 향후 영상 편집안이다. 브라우저 재생은 음성 없는 상태 연출이고 저장은 같은 주소의 브라우저에 한정된다. Android 저장은 해당 앱에만 남고 기기 간 동기화는 연출이다. 브라우저 제한 장면은 재생 상태를 초기화하지만 현재 Pleos 소스는 주행 제한 중 활성 큐의 차량 미디어 제어를 허용한다. 상태 미확인 시 중단은 공통이다. [시연 안내](showcase/REHEARSAL.md)를 따른다.

현재 후보 `21c83fdf61768eba7407922791a82f290ac766ea`의 제공된 승인 보고서상 CI #42477729327 SDK 컴파일·unsigned debug APK 성공, 과거 Windows 원시 콜백과 샘플 오디오 시험, 현재 구현 및 미검증 범위는 5절처럼 구분한다. 공식 콘텐츠 연동 이력은 [기존 기록](sources-notes.md)을 유지한다. 실제 실행 근거를 합성 로그나 mock 결과로 대체하지 않는다.

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
