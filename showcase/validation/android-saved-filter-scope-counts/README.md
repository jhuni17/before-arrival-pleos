# Android saved filter scope counts

- 세 상태 버튼은 현재 출처 ID와 정규화한 제목 검색에 맞는 항목을 상태 선택과 독립적으로 집계한다. 조건 적용 시 건수 기준 안내를 표시한다.
- 전체 보관함 요약, 용량, 출처 선택창 집계, 전체 저장 순서 탐색과 주차 상태 상호작용 정책은 유지한다.
- `MainActivityTest.savedFilterScope*`는 동명/비활성/빈 출처, 검색만·출처만·교차 조건, 한글·대소문자·공백·정규식 모양 리터럴, 세 상태와 빈 보관함을 검사한다. 조건 적용 중 변경과 실패 롤백/재시도, 재생성 및 초기화의 건수도 검사한다. 기존 안전 상태와 오래된 콜백 회귀 테스트는 유지한다.

## 로컬 검증

- `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedFilterScope*'`: 종료 코드 **1**. `JAVA_HOME is not set and no 'java' command could be found in your PATH.` Java 부재로 테스트 미실행.
- `bash android/gradlew -p android :app:testSampleDebugUnitTest :app:lintSampleDebug`: **미실행**, 종료 코드 없음. 사용자 지시에 따라 전체 회귀와 lint는 GitHub CI에 맡긴다. 로컬 Java 부재도 해소하지 않았다.
- `git diff --check`: 종료 코드 **0**, 공백 오류 없음.

기준 후보 CI 결과를 이번 변경의 통과 근거로 사용하지 않는다. 실제 화면, 보조기술, APK 설치, 에뮬레이터 및 실제 Pleos SDK 콜백은 미검증이다. APK 빌드도 이번 작업에서 수행하지 않았으며 unsigned APK를 설치나 제출 준비의 근거로 사용하지 않는다. 브라우저 검사는 Android 변경 범위에 해당하지 않아 수행하지 않았다.
