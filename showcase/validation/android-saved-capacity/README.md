# Android 저장 용량 보호 검증

기준 SHA: `df7b255109f99cb91195b0a43fd53eb08e65c28c` (작업 시작 HEAD).
후보: 위 기준에 지정된 테스트와 이 보고서 두 파일만 수정한 미커밋 작업 트리.

## 기준 후보 CI와 로컬 재현

사용자가 제공한 수용 계획에 따르면 HEAD와 CI source-sha가 위 SHA로 일치하고 Android 입력 해시 60개도 일치한다. 해당 CI는 62개 테스트 중 `MainActivityTest:1150`의 버튼 객체 동일성 검사에서 1개 실패했다. 이 CI 증거는 이번 작업에서 독립적으로 재확인하지 않았다. 실패 뒤 필터 반복·Activity 재생성의 실행 완료 및 해당 후보 lint 통과는 입증되지 않았다.

수정 전 로컬 단독 재현 명령:

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.MainActivityTest.capacityKeepsFiltersButtonsAndUndoUntilUserDeletesAndRetries
```

종료 코드 1. `JAVA_HOME is not set and no 'java' command could be found in your PATH.`로 Gradle 시작 전에 차단됐다. 따라서 로컬에서 1150행 assertion 실패를 재현한 것은 아니다.

## 수정 내용

`capacityKeepsFiltersButtonsAndUndoUntilUserDeletesAndRetries`에서 직접 삭제한 `removed`의 `undoDeleted`를 저장 재시도 전에 캡처한다. 저장 성공 후에는 재생성된 버튼의 객체 동일성을 요구하지 않고 같은 삭제 기록 및 대상 key 보존을 검사한다. 현재 화면의 삭제 실행 취소 버튼을 다시 조회해 가득 찬 상태에서 누르고 용량 안내, 복원 거절, 삭제 기록·저장 문자열·항목 순서·확인 상태 보존을 검사한다. 이전 삭제 대상의 만료된 콜백도 다시 호출해 상태가 유지되는지 검사한다.

ALL·UNREAD·CONFIRMED 반복, `SAVED_ITEM_LIMIT` 기준 fixture, 기존 저장 거절의 버튼·접근성 설명·필터·개수·첫 미확인·두 실행 취소 보존, 성공 후 중복 없는 target 추가 및 Activity 재생성 검사를 유지했다. 기존 commit 실패·쓰기 후 예외·중복 저장·복구 재시도·RESTRICTED·UNKNOWN·onPause·실행 취소 만료 회귀 검사는 변경하지 않았다. 제품 코드, AccessPolicy, 음성·상세·원문 자동 실행 방지 동작도 변경하지 않았다.

## 수정 후 로컬 검증

실행한 집중 검사와 lint 명령:

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.CatalogTest --tests com.beforearrival.briefing.MainActivityTest
bash android/gradlew -p android :app:lintSampleDebug
```

두 명령 모두 종료 코드 1로 위와 같은 Java 부재 때문에 시작 전에 차단됐다. 수정 후 테스트와 lint는 미검증이며, 모든 필터 및 재생성 검사의 실행 완료를 주장하지 않는다. `git diff --check`는 통과했다.

전체 `:app:testSampleDebugUnitTest` 회귀 실행은 사용자의 이번 실행 범위 지시에 따라 GitHub CI에 남겼다. 이번 수정 후 CI 결과는 없다. 빌드/도구 설정, workflow, 자격 증명, 라이선스 또는 서명 키는 변경하지 않았다.

APK 빌드·설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백, 기기 간 동기화 및 제출 준비는 입증하지 않았다. Android 샘플 경로와 showcase 시연 자료를 실제 Pleos 실행 증거로 간주하지 않는다. 브라우저 변경이나 브라우저 검사는 수행하지 않았다.
