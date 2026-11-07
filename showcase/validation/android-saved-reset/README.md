# Android 저장 조건 모두 지우기 검증

변경: 검색어가 있거나 ALL 이외 필터이면 `조건 모두 지우기`를 표시한다. 버튼은 `canInteract()` 확인 후 검색어와 필터만 초기화하고 기존 저장 순서로 다시 표시한다. 문구는 strings.xml에서 관리한다. 기존 검색 지우기는 검색어만 초기화한다.

추가한 JVM 테스트는 실제 검색 적용·필터·초기화 버튼을 통해 검색만, 필터만, 각 조합, 일치/불일치 검색, 빈 저장 목록과 초기 숨김을 검사한다. 초기화 후 안내/두 지우기 버튼 숨김, ALL 선택, 저장 순서·저장소·확인 상태·건수·용량·편성·음성 유지 및 대화상자 자동 실행 부재를 검사한다. 용량 assertion은 기존 ContentCatalog.SAVED_ITEM_LIMIT 기반 helper를 사용하며 fixture 크기를 제한으로 취급하지 않는다. 확인/삭제 실행 취소 대상 유지와 실제 실행 취소, RESTRICTED/UNKNOWN/pause에서 보관한 버튼 호출 거절, 재생성 기본 조건과 저장 유지도 포함한다.

## 로컬 검사

다음 명령은 각각 exit 1로 Gradle 시작 전에 차단됐다.

```sh
bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedConditionsReset*' --tests 'com.beforearrival.briefing.MainActivityTest.savedTitleSearch*'
bash android/gradlew -p android :app:lintSampleDebug
```

공통 원인: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` 테스트 컴파일·실행 및 lint 결과는 미검증이다. 빌드/도구 설정, 라이선스, 자격 증명, 서명 키, workflow는 변경하지 않았다.

전체 `bash android/gradlew -p android :app:testSampleDebugUnitTest`는 사용자의 집중 로컬 검사 및 전체 회귀 CI 위임 지시에 따라 실행하지 않았다. 전체 테스트와 `:app:lintSampleDebug`의 새 변경 통과 결과는 GitHub CI에서 확인해야 한다. 이전 후보의 73개 테스트·lint·APK 결과를 이번 변경의 통과 근거로 사용하지 않는다. 기존 검색 지우기, 빈 필터 전체 보기, 용량 도달 확인 완료 보기, 전체 순서 미확인 이동 및 안전 전환 회귀는 CI 확인 대상이다.

`git diff --check`: PASS. 마지막 상태 검사에서 지정된 네 파일만 변경됨을 확인했다.

실제 화면, 한국어 IME, 보조기술, APK 빌드·설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백은 이번 변경에서 검증하지 않았다. Android는 주차용 차량 프로토타입이며 showcase 시연 자료·샘플 경로·기기 간 연동 연출은 실제 Pleos 실행 증거가 아니다. 새 캡처는 없다.
