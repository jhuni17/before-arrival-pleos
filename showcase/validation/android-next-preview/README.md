# Android 다음 미확인 제목 미리보기

대상 기준 SHA: `5b4bc8329044d3cdf427e8fbc38d851da6889c87`. 변경은 미커밋 working tree이며 새 커밋 SHA는 없다.

상세를 열 때 기존 `nextUnread(item)` 결과의 전체 제목을 읽기 전용 TextView로 표시한다. 줄 수 제한과 말줄임 없이 기본 접근성 텍스트를 사용한다. 클릭은 후보를 다시 조회하며 후보가 없어지면 미리보기 텍스트도 지운다. 저장 순서, 확인 저장, 실행 취소, lifecycle/access 검사에는 변경이 없다.

기존 Robolectric 테스트를 확장했다: ALL/UNREAD/CONFIRMED의 전체 저장 순서, 확인 완료 건너뛰기, 순환, 확인 완료 상세, 긴 제목, 상세 재열기 갱신, 클릭 전 확인/삭제 변경, 자기 자신 제외 및 후보 소진 시 숨김. fixture의 items.size()는 테스트 데이터 개수이며 제품 한도가 아니다. 기존 저장 실패 재시도·마지막 확인 후 닫기·이전 콜백 무효화 테스트는 유지한다.

## 로컬 검사

- 실행: `sh android/gradlew -p android --offline --no-daemon --console=plain :app:testSampleDebugUnitTest --tests '*MainActivityTest.nextUnread*'`
- 결과: BLOCKED. Gradle이 테스트 실행 전에 `JAVA_HOME is not set and no 'java' command could be found in your PATH`로 종료했다. `java -version`도 command not found였다. 테스트 통과 또는 수정 전 실패 재현을 주장하지 않는다.
- 실행: `git diff --check` — PASS.
- 전체 단위 테스트, lint, sample APK 빌드와 showcase 회귀는 사용자 지시에 따라 기존 GitHub CI에 맡긴다. 이번 작업의 실행 결과·APK·브라우저 캡처는 없다. 브라우저 검사를 실행하지 않았으며 showcase 파일도 변경하지 않았다.

## CI 증거와 남은 공백

기존 workflow의 산출물 이름은 `before-arrival-${github.event.pull_request.head.sha}`이다. 내부 `ci-evidence/source-sha.txt`, `android-input-sha256.txt`, `apk-sha256.txt`, `app-sample-debug-unsigned.apk`, `android.log`, `showcase.log`, `showcase-html-sha256.txt`, `browser/` 및 `android/app/build/test-results/testSampleDebugUnitTest/`, `android/app/build/reports/lint-results-sampleDebug.*`에서 새 후보의 SHA·입력·APK 해시와 결과를 확인해야 한다. workflow는 변경하지 않았다.

수용된 계획은 기준 SHA에서 Android 테스트 50개 통과, lint 경고 7개·오류 0개, sample APK 및 showcase CI 성공을 보고한다. 이는 이전 후보에 대한 제공 정보이며 이번 변경의 검증 결과가 아니다. 이 작업에서 CI 실행 URL이나 해당 입력/APK 해시 원본을 독립적으로 확인하지 않았으므로 그 값은 미확인으로 남긴다. 새 working tree의 전체 CI 결과와 APK 해시도 아직 미확인이다.

수정 전 실패 재현 증거는 없다. 설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백 및 제출 준비 완료도 미검증이다. unsigned sample APK와 Robolectric 결과는 이들의 증거를 대신하지 않는다. android는 차량용 주차 상태 프로토타입이고 showcase는 시연 자료이며 실제 Pleos 실행과 구분한다.

## 변경 입력 SHA-256

- `android/app/src/main/java/com/beforearrival/briefing/MainActivity.java`: `8cc683f83e76c13df75196292d420e4dbbfb95d8e0b1b4d3bdccb26c5c9b3aa7`
- `android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java`: `9e055f613212f85386be681cc5e9378319e35c73dcd0070d2eff6f3c65b835a2`

위 값은 변경 소스의 해시이며 CI 전체 입력 manifest 또는 APK 해시가 아니다.
