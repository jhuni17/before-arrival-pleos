# Android 첫 미확인 제목 미리보기

기준 SHA: `9e84362e172f6ad317137f8b4001a117c5710d96` (작업 시작 HEAD).
새 후보는 지정된 네 파일만 수정한 미커밋 작업 트리다.

## 기준 후보 증거 확인

로컬에 제공된 CI 아티팩트 `9e84362e172f6ad317137f8b4001a117c5710d96/42348854769-1/`를 읽어 확인했다. `ci-evidence/source-sha.txt`가 HEAD와 일치하고, `android-input-sha256.txt`의 60개 입력을 수정 전 HEAD의 Git blob으로 SHA-256 계산해 모두 일치함을 확인했다. unsigned APK의 실제 SHA-256도 `apk-sha256.txt`와 일치한다:
`f3030190c7a66e1416d8f8d934c56fd89aa846658435eda918699a1493568f65`.

CI 테스트 XML을 직접 파싱했다. CatalogTest 14개, MainActivityTest 42개, SpeechPlaybackTest 7개 모두 failures/errors/skipped 0이다. lint XML은 오류 0개, 경고 9개다. 이전 보고서의 XML 부재는 이번 기준 후보 검토에서 해소됐다. 이 결과는 새 미리보기 변경의 실행 결과가 아니다.

기준 코드에서 firstUnread()는 전체 catalog.savedItems 순서로 선택하고, 클릭은 현재 후보를 재조회한다. AccessPolicy는 resumed와 PARKED_USE_ALLOWED를 함께 요구한다. 용량 안내와 ContentCatalog.SAVED_ITEM_LIMIT 및 SAVED_CAPACITY_NOTICE를 유지했다.

## 변경과 검사 범위

첫 미확인 보기 버튼 바로 아래에 문자열 리소스를 사용하는 비클릭 TextView를 추가했다. renderSaved()에서 같은 firstUnread() 결과로 버튼과 제목을 갱신하며 후보가 없으면 텍스트를 비우고 GONE으로 숨긴다. 전체 제목을 표시하고 줄 수 제한·말줄임·별도 저장 상태를 추가하지 않았다. 선택 규칙, ALL 기본값, 재생성 초기화, 클릭 재조회 및 접근 제한 코드는 유지했다.

기존 테스트에 전체 필터, 재생성, 표시 후 삭제·확인된 후보의 재조회, 빈 저장함·전부 확인 완료, 삭제 실행 취소, 용량 거절, 저장 commit 실패·쓰기 후 예외 상황의 제목 검사를 보강했다. 긴 한국어 제목과 렌더링·필터 변경의 저장/확인/상세/원문/음성 부작용 부재, 확인 실패·성공 재시도·확인 실행 취소·삭제 실행 취소·재생성 검사를 추가했다. 기존 직접 콜백의 RESTRICTED·UNKNOWN·pause 차단 및 용량 회귀 테스트를 보존했다. 테스트 실행은 아래 사유로 미검증이다.

## 새 변경의 실제 로컬 검증

다음 명령을 각각 실행했으나 모두 종료 코드 1로 Gradle 시작 전에 차단됐다:

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.CatalogTest --tests com.beforearrival.briefing.MainActivityTest
bash android/gradlew -p android :app:lintSampleDebug
```

원인: `JAVA_HOME is not set and no 'java' command could be found in your PATH.`
도구 설치·설정 변경은 하지 않았다. 새 변경의 테스트와 lint 통과를 주장하지 않는다.
전체 `bash android/gradlew -p android :app:testSampleDebugUnitTest`는 사용자의 집중 로컬 검사 지시에 따라 실행하지 않고 GitHub CI에 남겼다. 새 후보 CI 결과는 아직 없다.

Python ElementTree로 변경된 strings.xml 파싱 통과. `git diff --check` 통과. 최종 untracked 포함 git status에서 지정된 네 파일만 변경됨을 확인했다. 브라우저 변경·검사는 없다.

APK 빌드·설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백, 실기기 검증, 기기 간 동기화 및 제출 준비는 이번 작업에서 입증하지 않았다. 기준 unsigned APK 해시 일치는 설치나 실제 SDK 실행의 증거가 아니다. Android 샘플 경로와 showcase 시연 자료는 실제 Pleos 실행과 구분한다. 로컬 실행 및 실기기 검증 공백은 유지된다.
