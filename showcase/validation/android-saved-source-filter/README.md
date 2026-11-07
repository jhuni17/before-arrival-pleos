# Android 저장 출처 필터 검증

검증일: 2026-11-07. 이번 작업 트리의 변경에 대한 기록이다. 이전 후보 3c54d845의 CI 통과는 이번 변경의 통과 근거로 사용하지 않는다.

## 변경

- catalog.sources 전체를 선택 목록에 포함하며 source.id로 세션에서만 선택한다. 동명 출처에는 ID를 함께 표시한다. Activity 재생성 시 전체 출처로 돌아간다.
- 기존 제목 정규화와 저장 순서를 유지하며 출처·제목·확인 상태를 교차 적용한다. 출처만 적용해도 표시 건수와 전체 저장 순서 탐색 안내를 표시한다.
- 검색 지우기는 제목만 해제한다. 조건 모두 지우기와 빈 결과의 전체 보기는 모든 조건을 해제한다. 용량 안내의 확인 완료 바로가기도 출처와 제목을 해제한다.
- 선택 UI와 콜백에 canInteract, lifecycleGeneration, activeDialog 및 표시 상태 검사를 적용한다. 선택은 저장소·편성·음성·확인 상태를 변경하지 않는다.

## 로컬 검사 결과

| 명령 / 검사 | 결과 |
| --- | --- |
| `./android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedConditionsReset*'` | 차단: 기존 gradlew 실행 권한 없음 (exit 126). 파일 권한을 변경하지 않았다. |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.saved*'` | 차단: JAVA_HOME 미설정, PATH에 java 없음 (exit 1). 테스트 실행 전 종료. |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest` | 동일 Java 부재로 차단 (exit 1). 전체 JVM 테스트 미실행. |
| `bash android/gradlew -p android :app:lintSampleDebug` | 동일 Java 부재로 차단 (exit 1). lint 미실행. |
| `git diff --check` | 통과 (exit 0). |
| Python ElementTree 및 리소스 참조 검사 | strings.xml 파싱, 리소스 이름 중복 없음, 새 출처 필터 문자열 참조 존재 확인 통과. |

MainActivityTest에 실제 선택 목록 클릭과 검색·확인 상태 버튼을 통한 교차 결과, 동명·비활성·항목 없는 출처, 같은 제목, 표시 순서·건수, 빈 보관함, 초기화, 용량 바로가기, 전체 순서 탐색, 확인·삭제·복구 실패 후 재시도, 선택 유지, 제한·UNKNOWN·pause 및 닫힌 콜백, 재생성 기본값 검사를 추가했다. 기존 빈 검색 결과 검사는 전체 보기 버튼을 반영했다. 이 테스트들은 작성했지만 실행 결과는 확인하지 못했다. 전체 회귀 검증은 GitHub CI에 남긴다.

## 테스트 컴파일 오류 수정 후 재검증 (2026-11-07)

제공된 실패 CI 정보는 후보 `f39e0a3a78d50bd5fc6562e8dcf9bafcef3390e7`의 테스트 컴파일 실패다. 이번 로컬 HEAD도 해당 후보와 일치한다. `CatalogTest.capacityItem()`이 선언한 checked Exception을 호출 테스트가 선언하거나 처리하지 않은 코드와 일치하며, 이 CI 정보는 아래 로컬 실행 결과와 구분한다.

`savedSourceEmptyArchiveAndCapacityShortcutReset()` 선언에 `throws Exception`만 추가했다. 예외를 삼키거나 테스트를 비활성화하지 않았으며, `ContentCatalog.SAVED_ITEM_LIMIT` fixture와 기존 assertion을 그대로 유지했다. 빈 보관함 초기화 및 주차 상태의 용량 바로가기에서 출처·검색어 해제, 확인 완료 필터 선택과 항목 표시 검사도 유지한다. 제품 코드와 안전 보호 조건은 변경하지 않았다.

| 수정 후 로컬 명령 | 종료 코드 / 결과 |
| --- | --- |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedSource*'` | 1 / `JAVA_HOME` 미설정 및 PATH에 `java` 없음. Gradle 시작 전 종료하여 테스트 컴파일과 출처 필터 테스트 모두 미실행. |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest :app:lintSampleDebug` | 1 / 동일 Java 부재로 시작 전 종료. JVM 테스트와 lint 모두 미실행. |
| `git diff --check` | 0 / 통과. |

수정 후 확보한 결과는 선언 변경과 기존 fixture·assertion 유지에 대한 diff 확인 및 공백 검사 통과다. 컴파일 오류 해소와 이전 출처 필터 수용 기준의 실행 검증은 아직 입증되지 않았으므로 통과로 표시하지 않는다. 환경 해결을 위한 workflow나 Android 빌드·도구 설정 변경은 하지 않았다. 추가 전체 회귀 실행은 GitHub CI에 남긴다.

## 증거 한계

이번 변경의 APK 빌드 및 설치, 실제 Android 화면, 한국어 IME, 보조기술, 에뮬레이터 실행, 실제 Pleos SDK 콜백은 검증하지 않았다. 샘플 프로토타입의 코드와 JVM 테스트 작성은 실제 Pleos 실행이나 기기 간 연동의 증거가 아니다. unsigned APK의 과거 해시 일치도 이를 증명하지 않는다. 브라우저 검사는 Android 변경의 검증으로 사용하지 않았으며 캡처를 생성하지 않았다.

워크플로, Android 빌드·도구 설정, SDK 자격 증명, 라이선스, 서명 키는 변경하지 않았다. 커밋·푸시하지 않았다. 검증 보고서는 이 showcase/validation/ 경로에만 작성했다.
