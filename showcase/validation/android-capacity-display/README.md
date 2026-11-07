# Android 저장 용량 표시 검증

기준 SHA: `1322d6b27689a395495310c5bf691ffc2f3caccb` (작업 시작 HEAD).
후보는 이 기준에 지정된 MainActivity.java, strings.xml, MainActivityTest.java와 이 보고서만 수정한 미커밋 작업 트리다.

## 이전 기준의 근거

사용자가 제공한 수용 계획은 HEAD와 CI source-sha 일치, Android 입력 해시 60개 및 APK 해시 일치, 현재 CI의 전체 단위 테스트·lint·assemble 집계 성공을 보고한다. 이는 기준 SHA에 대한 제공된 근거이며 이번 변경의 CI 결과가 아니다. 이번 작업에서 CI 원본 로그와 해시를 독립적으로 재확인하지 않았다. 개별 테스트 XML이 없어 테스트별 통과나 실행 내용을 독립 확인하지 못했다.

코드에서 이전 수용 기준을 확인했다. ContentCatalog는 SAVED_ITEM_LIMIT=500, 저장 상한 거절, 실패 시 저장 목록·확인 상태와 STORAGE_KEY 복구를 구현한다. MainActivity는 저장 성공 시 다른 항목의 삭제 기록을 보존하고 용량 거절 시 조기 반환한다. 기존 capacityKeepsFiltersButtonsAndUndoUntilUserDeletesAndRetries는 삭제 기록, 만료 콜백, ALL·UNREAD·CONFIRMED 반복, 순서·확인 상태·저장 문자열, 재시도 및 Activity 재생성 검사를 포함한다. newSaveFailureKeepsFiltersAndUndoUntilSameButtonRetry는 commit 실패·쓰기 후 예외를 검사한다. 이 검사는 삭제하지 않았다. AccessPolicy의 주차·resumed 조건, RESTRICTED·UNKNOWN에서 content 숨김 및 onPause의 상호작용 차단·음성 중지는 변경하지 않았다. 이는 코드 확인이며 로컬 테스트 통과 주장이 아니다.

## 이번 변경

기존 제목과 기본 ALL 필터를 유지하고 제목 바로 아래에 `전체 저장 N/L건 · 추가 저장 가능 R건` 일반 TextView를 추가했다. N은 필터와 무관한 savedItems.size(), L은 Android SAVED_ITEM_LIMIT, R은 max(0, L-N)이다. savedAtCapacity()일 때만 별도 줄바꿈 가능한 TextView로 SAVED_CAPACITY_NOTICE를 지속 표시한다. 표시 계산은 저장·삭제·확인·상세 열기·원문 실행·음성 재생을 호출하지 않는다. showcase의 savedItemLimit은 사용하지 않는다.

추가 테스트는 빈 저장함, L-1, L, 모든 필터에서 전체 수 유지, 필터 변경 시 저장 문자열·순서·확인 상태 보존, 직접 삭제와 실행 취소, 저장 성공, 재생성, 제한 상태에서 content 숨김을 검사한다. 기존 용량 거절 및 commit 실패·쓰기 후 예외 테스트에 용량 표시 검사를 추가했다. 테스트는 작성했으나 아래 환경 제약으로 실행 결과는 미검증이다.

## 실제 로컬 검증

다음 명령을 실행했으며 둘 다 종료 코드 1로 Gradle 시작 전에 차단됐다.

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.CatalogTest --tests com.beforearrival.briefing.MainActivityTest
bash android/gradlew -p android :app:lintSampleDebug
```

원인: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` Java 설치·Android 도구 설정·라이선스·자격 증명은 변경하지 않았다. 따라서 새 동작, 단위 테스트 및 lint의 독립 로컬 재실행은 미검증이다. 전체 `:app:testSampleDebugUnitTest`는 사용자의 집중 로컬 검사 지시에 따라 GitHub CI에 남겼으며 이번 후보의 CI 결과는 없다.

strings.xml의 XML 파싱과 `git diff --check`는 통과했다. 최종 `git status --short --untracked-files=all`과 경로 검사는 지정된 네 파일만 변경됐음을 확인했다. 브라우저 변경·검사·캡처는 수행하지 않았다.

APK 빌드, 설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백, 기기 간 동기화 및 제출 준비를 입증하지 않는다. unsigned APK 자체도 이 항목들의 증거가 아니다. Android 샘플 경로와 showcase 시연은 실제 Pleos 실행과 구분한다. 연락처나 계정 정보는 포함하지 않았다. workflow, 빌드/도구 설정, 서명 키를 변경하지 않았으며 커밋·푸시하지 않았다.
