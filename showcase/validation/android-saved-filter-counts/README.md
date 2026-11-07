# Android 저장 필터 건수

## 기준 후보 확인

기준 후보 `5a47b3b6fb8537b4c0d9513a1ebb67c78c7dba6c`의 제공된 CI 자료
`42350114952-1/`를 직접 읽었다. source-sha.txt 일치, android-input-sha256.txt의
60개 입력을 해당 Git blob으로 계산해 모두 일치함을 확인했다. unsigned APK 실제
SHA-256은 apk-sha256.txt와 일치한다:
`22012862c9ffbfc6992abefa427dd418c5e22ae38398e0ebe43658fda22f3ee7`.

CI XML 파싱 결과 CatalogTest 14개, MainActivityTest 44개, SpeechPlaybackTest 7개는
failures/errors/skipped 모두 0이다. lint XML은 오류 0개, 경고 9개다.
코드에서 첫 미확인은 전체 저장 순서를 사용하며 접근은 resumed와 주차 허용을 함께 요구한다.
이 결과는 기준 후보의 결과이며 이번 건수 변경의 실행 결과가 아니다.
[이전 미리보기 보고서](../android-first-unread-preview/README.md)의 당시 로컬 Java 부재는
기준 후보의 CI 통과와 별개의 기록으로 유지한다.

## 이번 변경

renderSaved()에서 전체 저장 수, unreadCount(), 두 값의 차이로 세 버튼을 함께 갱신한다.
문자열 리소스로 이름·N건·선택됨을 표시하며 별도 저장 상태는 없다. 20sp 크기를 유지하고
줄바꿈을 허용하며 말줄임을 제거했다. 필터 선택, 빈 상태와 전체 보기, 첫 미확인 미리보기,
전체 저장 순서 탐색, 저장 용량 정책 및 SAVED_CAPACITY_NOTICE는 유지한다.

기존 미리보기·용량 검사에 버튼 텍스트와 접근성 설명·선택 상태·줄 수·글자 크기 검사를
연결했다. 빈 저장함의 0건 필터 선택과 재생성 검사도 보강했다. 기존 시나리오의 전부 미확인,
혼합, 전부 확인 완료, 저장·확인·미확인 복귀·확인하고 다음·삭제·실행 취소를 관찰한다.
CatalogTest.failingCommits의 commit 실패·쓰기 후 예외 시나리오에서 기존 저장 순서·확인 상태·
STORAGE_KEY 검사와 건수 검사를 함께 유지하며 성공 재시도도 검사한다. 용량 경계는
ContentCatalog.SAVED_ITEM_LIMIT을 사용한다. 필터 렌더링의 저장·상세·원문·음성 부작용 부재 및
RESTRICTED·UNKNOWN·pause·직접 콜백 차단 회귀 검사는 유지했다.

## 새 변경의 로컬 검증

다음 두 명령은 각각 종료 코드 1로 Gradle 시작 전에 차단됐다:

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.MainActivityTest
bash android/gradlew -p android :app:lintSampleDebug
```

원인: `JAVA_HOME is not set and no 'java' command could be found in your PATH.`
테스트와 lint 통과를 주장하지 않는다. 도구·빌드·workflow·자격 증명 설정은 변경하지 않았다.
전체 회귀 실행은 GitHub CI에 남겼으며 새 변경의 CI 결과는 아직 없다.
Python ElementTree 문자열 XML 파싱 및 `git diff --check`는 통과했다.
최종 untracked 포함 git status에서 지정된 네 파일만 변경됨을 확인했다.
Android 변경이므로 showcase 브라우저 검사는 실행하지 않았다.

새 APK 빌드·설치·에뮬레이터·실기기·실제 Pleos SDK 콜백은 미검증이다.
기준 unsigned APK 해시 일치는 기기 실행·동기화·제출 준비의 증거가 아니다.
Android 샘플 경로와 showcase의 연동 시연은 실제 Pleos 실행과 구분한다.
