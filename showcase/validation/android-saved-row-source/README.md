# Android 저장 목록 출처 표시 검증

## 이전 후보 근거

수용 계획이 제공한 후보 `44d0f554`의 CI 근거는 Android 입력 60개 해시 일치, 테스트 68개 통과(메타데이터 검사 포함), lint 오류 0개·경고 9개, unsigned APK 해시 일치 및 후보 diff 검사다. 이번 작업에서 해당 CI 로그와 해시를 독립 재검증하지 않았다. 이 근거는 아래 미커밋 변경의 실행 결과가 아니다.

## 변경 범위

각 저장 항목의 기존 단일 버튼에 제목·확인 상태 다음 줄로 `출처 · 출처명`을 표시한다. 목록과 상세는 같은 함수를 통해 catalog.source(item.sourceId).name을 사용하며, 출처 없음·공백 이름은 saved_source_unknown으로 대체한다. 표시 형식과 상태 문구는 strings.xml에서 관리한다. 줄 수 제한·말줄임 없이 전체 텍스트를 접근성 설명에도 제공한다.

MainActivityTest는 같은 제목·상태의 서로 다른 출처와 키, 출처 없음·공백 이름·긴 제목/출처를 로컬 fixture로 검사한다. 실제 savedRows 버튼의 표시와 클릭 대상의 요약·URL·메타데이터를 확인하며 ALL/UNREAD/CONFIRMED, 저장 순서·건수·확인 상태·preferences·편성 불변을 검사한다. 기존 메타데이터 재생성 검사를 실제 목록 버튼 클릭으로 확장했다. 보관한 버튼의 RESTRICTED/UNKNOWN/pause 차단도 검사한다. fixture 개수는 제품 제한이 아니다. SAVED_ITEM_LIMIT, ALL 재생성 기본값, canInteract/canOpen, 상세 세대·활성 대화상자 검사 및 실행 취소 로직은 변경하지 않았다. 기존 오래된 콜백·음성 중지 회귀 테스트도 유지한다.

## 이번 로컬 결과

다음 명령은 모두 종료 코드 1로 Gradle 시작 전에 차단됐다.

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedRows*' --tests 'com.beforearrival.briefing.MainActivityTest.saved*' --tests 'com.beforearrival.briefing.MainActivityTest.confirmedFilter*'
bash android/gradlew -p android :app:lintSampleDebug
```

원인: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` 새 테스트 및 기존 회귀 검사의 통과 여부와 lint 결과는 미검증이다. 전체 `:app:testSampleDebugUnitTest`는 집중 로컬 검사 및 전체 회귀 CI 위임 지시에 따라 실행하지 않았다. 전체 `:app:testSampleDebugUnitTest`와 `:app:lintSampleDebug`의 새 변경 결과는 GitHub CI에서 확인해야 한다. 이번 CI 실행 결과는 없다.

strings.xml의 Python XML 파싱 및 `git diff --check`는 통과했다. 최종 `git status --short --untracked-files=all`에서 지정된 네 파일만 변경됐음을 확인했다. 브라우저 검사·캡처는 수행하지 않았다.

실제 화면·보조기술 사용, APK 빌드·설치, 에뮬레이터, 실제 Pleos SDK 콜백·기기 간 연동은 이번에도 미검증이다. JVM fixture 및 Android 샘플 경로는 실제 Pleos 실행 증거가 아니며 showcase 시연과도 구분한다. 네트워크·음성·원문 실행을 추가하지 않았다. workflow·빌드·도구 설정, credentials·라이선스·서명 키 변경 및 커밋·푸시는 수행하지 않았다. 연락처·계정 정보는 포함하지 않았다.
