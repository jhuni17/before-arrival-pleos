# Android 저장 출처 건수 검증

출처 선택창을 열 때 현재 `catalog.savedItems`에서 저장·미확인 건수를 계산한다.
전체 출처는 보관함 전체, 개별 출처는 ID가 일치하는 항목을 집계한다.
검색 및 확인 필터와 독립적이며 출처 순서·동명 ID·비활성/빈 출처 선택과 기존 안전 검사를 유지한다.
`ContentCatalog.SAVED_ITEM_LIMIT`은 변경하지 않았다.

## 이번 변경의 로컬 검증

2026-11-07 실행:

| 명령 | 종료 코드 | 결과 |
| --- | --- | --- |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedSource*'` | 1 | 미실행: JAVA_HOME 미설정, PATH에 java 없음. 테스트 컴파일 전 중단. |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest :app:lintSampleDebug` | 1 | 미실행: 같은 Java 부재로 테스트 및 lint 전 중단. |
| `git diff --check` | 0 | 통과: 공백 오류 없음. |

추가·갱신한 `savedSource*` 테스트는 선택창 재열기, 전체/출처별 집계, 검색·확인 필터 독립성,
빈 보관함, 저장·확인·미확인 전환·확인 되돌리기·삭제·복구와 commit 실패 후 재시도,
Activity 재생성 후 집계를 검사한다. 기존 교차 필터·순서·초기화·용량 바로가기와
RESTRICTED/UNKNOWN/pause 전환 및 주차 복귀 후 오래된 콜백 거부 검사를 유지했다.
Java 부재로 이 테스트들의 실행 결과는 확보하지 못했다. 전체 회귀 검증은 GitHub CI에 맡긴다.

## 기존 후보 증거와 한계

수락된 계획에 제공된 후보 `3b95437a5adbe957169fd55e828fc6af9fa1e809` CI 로그는
테스트 컴파일·전체 단위 테스트·lint·샘플 APK 빌드 성공을 보고했다.
이는 이전 후보의 증거이며 이번 변경의 통과 근거가 아니다. 이번 작업에서 APK를 빌드하지 않았다.

실제 화면·IME·보조기술·설치·에뮬레이터 실행·실제 Pleos SDK 콜백은 미검증이다.
Android는 주차 상태용 차량 프로토타입이며 샘플 콘텐츠/경로 및 기기 간 연동 연출은 실제 Pleos 실행 증거가 아니다.
unsigned APK를 설치 또는 제출 준비의 근거로 사용하지 않는다.
워크플로·빌드/도구 설정·자격 증명·라이선스·서명 키는 변경하지 않았다.
