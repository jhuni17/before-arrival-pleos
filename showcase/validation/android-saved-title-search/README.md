# Android 저장 제목 검색 검증

2026-11-07. 변경 범위는 MainActivity.java, strings.xml, MainActivityTest.java 및 이 보고서다.

## 이전 후보 근거

수용된 계획에서 후보 cbaaa7ac의 Android 입력 60개 CI 해시 일치, 테스트 69개 통과,
lint 오류 0개·경고 9개 및 unsigned APK 해시 일치를 확인했다고 전달받았다.
이번 작업에서 해당 CI 결과를 다시 조회하지 않았다. 이 근거는 새 검색 변경의 통과 증거가 아니다.

## 변경

주차 중 버튼으로만 검색 대화상자를 연다. 적용 전 입력은 초안이며 닫기와 취소 시 버린다.
제목과 검색어에 trim 및 Locale.ROOT 소문자 변환 후 리터럴 부분 문자열을 비교한다.
확인 상태 필터와 교집합을 취하며 전체 저장 순서를 유지한다.
검색어·표시 건수 및 전체 저장 기준 미확인 이동 안내를 표시한다.
검색은 Activity 임시 상태이며 저장소에 기록하지 않는다.
제한·미확인·pause에서 대화상자와 IME를 닫으며 생명주기·활성 대화상자 검사를 적용한다.

추가 JVM 테스트는 실제 검색 버튼과 입력·적용·지우기를 사용한다.
한글, 대소문자, 공백, 특수문자, 같은 제목의 다른 출처, 필터 교집합, 상세 대상,
전체/결과 건수, 저장소·편성 불변, 확인 저장 실패와 재시도, 확인/삭제 실행 취소,
초안 취소, 제한·pause 이후 오래된 콜백 및 재생성 초기값을 다룬다.
테스트 fixture 건수는 제품 제한이 아니며 제품 제한은 기존 500건을 유지한다.

## 이번 로컬 실행

- `./gradlew :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedTitleSearch*'`
  (android 디렉터리): 래퍼 실행 권한 없음(exit 126).
- `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedTitleSearch*'`:
  JAVA_HOME 미설정 및 PATH에 java 없음(exit 1). 테스트 컴파일·실행 전 차단.
- `bash android/gradlew -p android :app:lintSampleDebug`:
  같은 Java 부재로 차단(exit 1). 새 lint 결과 없음.
- `git diff --check`: 통과.
- Python 표준 XML 파서로 strings.xml 구문 검사: 통과.

전체 `:app:testSampleDebugUnitTest` 회귀 실행은 사용자의 집중 로컬 검사 지시에 따라
GitHub CI에 남긴다. 위 집중 테스트와 lint는 로컬 도구 부재로 검증하지 못했다.
새 APK 빌드나 해시 검증은 수행하지 않았다. 워크플로·빌드·SDK 설정, 라이선스,
자격증명 및 서명 키는 변경하지 않았다.

실제 화면·한국어 IME·보조기술·설치·에뮬레이터·Pleos SDK 콜백은 미검증이다.
JVM 테스트와 기존 CI 빌드는 이를 대체하지 않는다. showcase 브라우저 변경이나
검사는 없으며 샘플 경로·기기 간 연동 연출·실제 Pleos 실행을 동일시하지 않는다.
