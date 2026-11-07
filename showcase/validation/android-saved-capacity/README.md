# Android 저장 용량 보호 검증

기준 SHA: `c583ea2a83014ac68f931e4643275be444abe77d` (작업 시작 HEAD).
후보: 위 기준에 이 작업 트리의 다섯 파일 변경을 적용한 상태이며 커밋하지 않았다.

기존 복원 경계 500을 `SAVED_ITEM_LIMIT`로 명명하고 새 저장과 삭제 복구에도 적용했다. 중복 key는 용량 검사 전에 기존 성공 의미를 유지한다. 용량 거절은 쓰기 실패와 다른 안내를 표시하며, 자동 삭제 없이 사용자가 항목을 삭제한 후 재시도한다. 거절 경로에서는 목록과 실행 취소 대상을 변경하지 않는다. 브라우저의 `savedItemLimit`과 Android 복원 범위는 변경하지 않았다.

추가한 집중 테스트:

- CatalogTest: 상수 기준으로 유효하고 서로 다른 key의 sample fixture 생성, 경계 직전 성공/가득 찬 상태 거절, 저장 문자열·순서·payload·확인 상태 보존, 재생성 복원, 중복 저장/복구 우선 처리, 공간 확보 후 원래 위치와 확인 상태 복구.
- MainActivityTest: ALL·UNREAD·CONFIRMED의 실제 브리핑 저장 버튼으로 용량 거절, 버튼/접근성 설명·행·개수·필터·첫 미확인·실행 취소 보존, 삭제 실행 취소 용량 안내, 최신 삭제 우선 규칙, 직접 삭제 후 저장 재시도, Activity 재생성, 대본·음성·상세 상태 보존.
- 기존 commit false/쓰기 후 예외/동일 버튼 재시도와 RESTRICTED·UNKNOWN·onPause 실행 취소 만료 테스트는 유지했다. `AccessPolicy.canInteract`는 변경하지 않았다.

로컬 실행 결과:

- `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.CatalogTest --tests com.beforearrival.briefing.MainActivityTest`: 차단. `JAVA_HOME` 미설정이며 PATH에 `java`가 없어 Gradle 시작 전 종료했다. 새 테스트와 기존 집중 테스트 모두 실행되지 않았다.
- `bash android/gradlew -p android :app:lintSampleDebug`: 같은 Java 부재로 차단.
- `git diff --check`: 통과.
- 전체 `:app:testSampleDebugUnitTest` 회귀 실행은 사용자 지시에 따라 GitHub CI에 남겼다. 빌드/도구 설정 변경이나 설치는 수행하지 않았다.

기준 변경의 CI 결과와 이번 변경의 결과는 별개다. 수용 계획은 이전 CI에서 입력 해시 60개 일치, 테스트 60개 통과(실패·건너뜀 없음), lint 오류 0개·경고 9개를 보고했다. 이는 사용자 제공 기준 정보이며 이 작업에서 재검증하지 않았다. 이번 후보의 CI 실행 결과는 아직 없으며, 위 이전 결과를 새 용량 보호 변경의 통과 근거로 사용하지 않는다. 수정 전 실패 재현 실행 증거도 없다.

APK 빌드, 설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백, 기기 간 동기화, 기존 초과 저장 데이터 복구 및 제출 준비 여부는 검증하지 않았다. Android 샘플 경로와 showcase 시연 자료를 실제 Pleos 실행 증거로 간주하지 않는다. 브라우저 변경이나 브라우저 검사는 수행하지 않았다.
