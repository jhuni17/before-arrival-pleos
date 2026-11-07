# Android 저장 출처 해제 검증

2026-11-07 작업 트리 변경 기록.

- 출처 선택 버튼 바로 아래에 리소스 문구 `출처만 지우기` 버튼을 추가했다. 기존 button 스타일을 사용하며 접근성 이름은 표시 문구와 같다.
- 선택 출처가 있을 때만 표시한다. `canInteract()` 통과 시 출처 ID만 null로 바꾸고 기존 렌더링으로 목록·건수·안내를 갱신한다. 검색과 상태 선택은 유지한다.
- `savedSourceClear*` 테스트 4개를 추가했다. 비활성 동명 출처, 빈 출처, 서로 다른 제목과 확인 상태의 5개 항목(제품 제한과 무관한 fixture)을 사용한다. 검색 일치 범위의 전체/미확인/확인 건수는 3/2/1, 전체 보관함은 5/4/1로 직접 기대값을 지정했다.
- 세 상태에서 정규화 검색 유지, 검색 불일치, 빈 출처 복귀, 빈 보관함, 순서, 요약·용량·출처 선택창, 전체 순서 미확인 탐색을 검사한다. 저장 데이터·확인 상태·preferences·undo 대상과 토큰·편성·음성 보존, 확인/삭제 실행 취소, 제한·알 수 없음·pause 중 버튼 거부, 복귀 시 자동 실행 없음, 재생성 및 기존 초기화 동작도 검사한다.
- 기존 lifecycleGeneration·activeDialog·isShowing 기반 오래된 대화상자 콜백 거부 테스트는 유지했다.

## 명령과 결과

| 명령 | 실행 여부 / 종료 코드 | 결과 |
| --- | --- | --- |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest --tests 'com.beforearrival.briefing.MainActivityTest.savedSourceClear*'` | 실행 시도 / 1 | JAVA_HOME 미설정이며 PATH에 java가 없어 Gradle 시작 실패. 테스트는 미실행. |
| `bash android/gradlew -p android :app:testSampleDebugUnitTest :app:lintSampleDebug` | 미실행 / 해당 없음 | 전체 회귀는 요청대로 GitHub CI에 맡김. 로컬 Java 부재도 남아 있음. |
| `git diff --check` | 실행 / 0 | 공백 오류 없음. |

새 변경의 테스트·lint 통과를 주장하지 않는다. 이전 후보의 CI 결과를 새 변경의 검증으로 사용하지 않는다. 실제 화면, 보조기술, 설치, 에뮬레이터, 실제 Pleos SDK 콜백은 미검증이다. APK 빌드도 수행하지 않았으며 unsigned APK를 설치나 제출 준비의 근거로 사용하지 않는다. Android는 주차 상태용 차량 프로토타입이며 showcase의 이 보고서는 시연·검증 문서다. 실제 Pleos 실행이나 기기 간 연동을 입증하지 않는다.
