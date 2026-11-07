# Android 저장 용량 확인 완료 바로가기 검증

기준 HEAD: `b4d47bceb666dcca1b34c3d2be4dbf67bb764f5d`. 이번 결과는 지정된 네 파일의 미커밋 변경에 대한 기록이다.

## 이전 후보의 근거

사용자가 제공한 수용 계획은 이전 후보의 입력 60개와 unsigned APK 해시 일치, 보강된 검사를 포함한 테스트 65개 통과, lint 오류 0개·경고 9개 및 후보 diff 검사 통과를 보고한다. 이는 이전 후보에 대한 제공된 근거이며 이번 변경의 실행 결과가 아니다. 이번 작업에서 CI 로그·해시를 독립 재검증하지 않았다.

기존 코드와 테스트에서 Android ContentCatalog.SAVED_ITEM_LIMIT 및 SAVED_CAPACITY_NOTICE, 저장 용량 표시, 필터별 전체 건수, 삭제·실행 취소, 저장 실패 처리를 확인했다. 기존 제한 전환, 오래된 상세 콜백, 음성 중지 및 원문 자동 실행 방지 검사는 유지했다. 코드 확인은 테스트 실행 통과를 뜻하지 않는다.

## 이번 변경

저장 용량 안내 아래에 `확인 완료 항목 보기 · 전체 N건` 버튼을 추가했다. savedAtCapacity()이고 savedItems.size() - unreadCount()가 양수일 때만 표시하며 N은 현재 필터와 무관하다. 클릭 시 canInteract()와 현재 조건을 재확인하고 기존 CONFIRMED 필터로 이동한다. 저장·확인·삭제·자동 저장 재시도·상세·원문·음성을 호출하지 않는다. 재생성 기본값 ALL과 Android 저장 한도는 유지한다. showcase fixture나 savedItemLimit은 참조하지 않는다.

기존 button()의 글자 크기와 최소 높이를 유지하며 전체 폭, 여러 줄, 말줄임 없음 및 전체 건수를 포함한 접근성 이름을 사용한다. MainActivityTest에 한도 직전, 가득 찬 전부 미확인·혼합 상태, ALL·UNREAD에서 이동, 순서·저장·편성·실행 취소 대상 보존, 삭제·실행 취소, 제한·UNKNOWN·pause 및 조건이 사라진 버튼 호출, 재생성 검사를 추가했다.

## 이번 로컬 실행 결과

다음 집중 검사와 lint는 각각 종료 코드 1로 Gradle 시작 전에 차단됐다.

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.capacityConfirmedShortcutNavigatesWithoutMutatingSavedState'
bash android/gradlew -p android :app:lintSampleDebug
```

원인: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` 따라서 새 테스트와 lint 결과는 미검증이다. 전체 `:app:testSampleDebugUnitTest`는 집중 로컬 검사 지시에 따라 실행하지 않고 GitHub CI에 남겼다. 이번 변경의 CI 실행 결과는 없다. Java 설치, 빌드·도구·workflow 설정, 라이선스, 자격 증명, 서명 키를 변경하지 않았다.

strings.xml의 Python XML 파싱 및 `git diff --check`는 통과했다. 최종 `git status --short --untracked-files=all`의 경로 검사는 지정된 네 파일만 변경됐음을 확인했다. 브라우저 검사·캡처는 수행하지 않았다.

APK 빌드·설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백, 기기 간 연동 및 제출 준비는 미검증이다. unsigned APK는 설치나 실제 SDK 사용의 증거가 아니다. Android 샘플 경로와 showcase 시연은 실제 Pleos 실행과 구분한다. 연락처·계정 정보는 포함하지 않았으며 커밋·푸시하지 않았다.
