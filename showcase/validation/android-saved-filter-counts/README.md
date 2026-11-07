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

## 확인하고 다음 직후 검증 보강 (2026-11-07)

위 기록은 당시 결과로 보존한다. 이번 기준 후보는
`0af491e5063d0c1cb756fe177c313177498698b9`이며 제공된 CI 자료
`42351424996-1/`를 다시 확인했다. source-sha.txt가 후보와 일치하고,
android-input-sha256.txt의 60개 입력을 해당 후보의 Git blob으로 계산해 모두 일치했다.
unsigned APK 실제 SHA-256과 apk-sha256.txt도 일치했다:
`3ad7d9293c4ecb8c06493cbb8aabd2b3684328a77c26b000a91ceb28a3961dc7`.
CI XML은 CatalogTest 14개, MainActivityTest 44개, SpeechPlaybackTest 7개로 총 65개이며
failures/errors/skipped 모두 0이다. lint XML은 오류 0개, 경고 9개다.
이는 보강 전 후보의 CI 결과이며 아래 테스트 수정의 실행 결과가 아니다.

기존 ALL·UNREAD 시나리오에는 ‘확인하고 다음’ 성공 직후 세 필터 건수 검사가 빠져 있었다.
제품 결함으로 단정하지 않고 검증 누락을 보완했다. 각 성공 클릭 직후 별도 renderSaved나
필터 전환 없이 assertSavedFilterCounts()로 텍스트·접근성 설명·선택 상태를 검사한다.
전체 건수 유지, 미확인 감소, 확인 완료 증가를 별도로 확인한다. commit 실패와 쓰기 후 예외
양쪽에서 실패 전후 건수·순서·확인 상태·STORAGE_KEY·현재 상세를 검사하고, 정상 preferences로
같은 버튼을 재시도했을 때 정확히 한 항목만 확인되는지 검사한다. 후보 재탐색 검사는 보존했다.
마지막 확인과 실행 취소 직후 및 재생성 후의 건수·저장된 확인 상태·ALL 선택도 검사한다.
fixture 건수는 items.size()에서 얻으며 제품 용량을 뜻하지 않는다.
기존 안전·오래된 콜백·음성·원문 자동 실행 방지 회귀 검사와 제품 코드는 변경하지 않았다.

이번에 실행한 집중 검사는 다음과 같다:

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.confirmNext*' --tests 'com.beforearrival.briefing.MainActivityTest.undoConfirmationAfterLastConfirmNextRestoresUnreadControls'
bash android/gradlew -p android :app:lintSampleDebug
```

두 명령 모두 종료 코드 1이며 Gradle 시작 전에
`JAVA_HOME is not set and no 'java' command could be found in your PATH.`로 차단됐다.
테스트 컴파일·실행 및 lint 통과를 주장하지 않는다. 사용자 실행 지침에 따라 전체
`:app:testSampleDebugUnitTest` 회귀 실행은 GitHub CI에 남겼다. 보강 후 CI 결과는 아직 없다.
CI 근거의 Python XML 파싱·60개 입력 및 APK 해시 검증과 `git diff --check`는 통과했다.
최종 untracked 포함 git status에서 이번 edit_paths의 두 파일만 변경됨을 확인했다.
브라우저 검사는 Android 테스트와 보고서만 수정하여 실행하지 않았다.

이번 변경의 APK 빌드·설치·에뮬레이터·실기기·실제 Pleos SDK 콜백은 미검증이다.
기존 APK 해시 확인은 설치나 실행의 증거가 아니다. Android 샘플 경로와 showcase 연동 시연은
실제 Pleos 실행과 구분한다. 빌드·도구·workflow·자격 증명·라이선스·서명 설정은 변경하지 않았다.
