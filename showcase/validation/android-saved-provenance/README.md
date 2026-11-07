# Android 저장 상세 자료 구분 검증

## 이전 후보 근거

수용 계획이 제공한 후보 `3ce28ef`의 CI 근거는 Android 입력 60개·CI 해시 목록·unsigned APK 해시 일치, 테스트 66개 통과, lint 오류 0개·경고 9개, 후보 diff 검사 통과다. 이번 작업에서 해당 CI 로그와 해시를 독립 재검증하지 않았다. 이 결과는 아래 미커밋 변경의 실행 결과와 구분한다.

## 변경과 검사 범위

저장 상세의 기존 actions에 읽기 전용 출처명·저장된 provenance·공식 자료의 fetchedAt을 추가했다. 문자열은 strings.xml에 두며, 출처 없음·빈 이름·미지 자료 구분·시각 없음에 각각 대체 문구를 표시한다. 시각은 Instant.toString()의 UTC 표기이며 예시 콘텐츠에는 표시하지 않는다. 여러 줄, 말줄임 없음, 비클릭 TextView의 전체 텍스트를 접근성에서 읽는다.

MainActivityTest에 각 자료 구분과 시각 유무, 출처 대체 문구, 같은 제목의 서로 다른 출처, fixture 갱신·실패와 재생성 전후 저장 데이터 보존 검사를 추가했다. 갱신은 로컬 JSON fixture이며 네트워크·실제 SDK 콜백이 아니다. 공통 assertDetail에도 메타데이터 검사를 추가해 기존 필터·첫/다음 미확인 탐색 검사가 해당 항목의 메타데이터를 함께 확인하도록 했다. 열람 시 preferences·순서·확인 상태·편성·음성·원문 실행 불변을 검사한다. 기존 실행 취소·제한 전환·오래된 콜백 검사와 ALL 재생성 기본값, SAVED_ITEM_LIMIT은 유지한다.

## 이번 로컬 결과

다음 집중 검사는 종료 코드 1로 Gradle 실행 전에 차단됐다.

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedMetadata*' --tests 'com.beforearrival.briefing.MainActivityTest.savedDetailButtonsRejectQueuedAndStaleCallbacksAcrossLifecycle'
bash android/gradlew -p android :app:lintSampleDebug
```

두 명령의 원인은 `JAVA_HOME is not set and no 'java' command could be found in your PATH.`이다. 새 테스트·기존 제한/음성 회귀·lint는 실행 미검증이다. 전체 `:app:testSampleDebugUnitTest`는 집중 로컬 검사 및 전체 회귀 CI 위임 지시에 따라 실행하지 않았다. 전체 `:app:testSampleDebugUnitTest` 및 `:app:lintSampleDebug`의 새 변경 결과는 GitHub CI에서 확인해야 한다. 이번 CI 실행 결과는 없다.

strings.xml의 Python XML 파싱과 `git diff --check`는 통과했다. 최종 `git status --short --untracked-files=all`에서 지정된 네 파일만 변경됐음을 확인했다. 브라우저 검사와 캡처는 수행하지 않았다.

APK 빌드·설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백 및 기기 간 연동은 이번 작업에서도 미검증이다. unsigned APK 근거는 설치·실제 SDK 실행 근거가 아니다. Android 샘플 경로와 showcase 시연을 실제 Pleos 실행으로 주장하지 않는다. 도구·빌드·workflow 설정, 자격 증명, 라이선스, 서명 키를 변경하지 않았고 커밋·푸시하지 않았다. 연락처·계정 정보는 포함하지 않았다.
