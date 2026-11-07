# Android 새 저장 commit 확인

기준 SHA: `ea32ffd8444f948e721857d91d31d25c85a85ef7`

## 이전 수용 기준과 이번 변경의 구분

사용자가 제공한 승인 계획에 따르면 기준 HEAD의 CI Android 입력 해시 60개가
일치했고, 이전 실패 테스트를 포함한 57개 테스트가 실패·건너뜀 없이 통과했다.
그 CI lint 결과는 오류 0개·경고 9개다. 이번 작업에서는 해당 CI 원본을 다시
조회하지 않았으며, 이 결과는 아래 새 검사들의 통과 근거가 아니다.

## 변경

새 저장만 기존 `changeSaved`에 연결했다. 같은 key가 있으면 기존 항목과 확인
상태를 보존한다. 새 항목은 끝에 미확인으로 추가하며 commit 성공 후에만 UI와
같은 항목의 삭제 실행 취소를 갱신한다. 실패하면 메모리 상태를 되돌리고
`저장 실패 · 다시 시도`를 표시한다. 이전 STORAGE_KEY 값 복원은 최선 노력이며
실제 저장소의 영구 복구를 보장하지 않는다. 일반 persist 경로는 변경하지 않았다.

추가한 CatalogTest는 false 반환·쓰기 후 예외, 이전 저장 값 복원, 순서·확인
상태, 중복 방지, 새 catalog의 전체 항목 payload 복원을 검사한다.
MainActivityTest는 실제 브리핑 저장 버튼에 failingCommits를 주입해 ALL·UNREAD·
CONFIRMED의 실패·동일 버튼 재시도, 카운트·행·접근성·첫 미확인 대상, 같은 항목과
다른 항목의 실행 취소, 재생성 payload, 제한·UNKNOWN·pause 차단을 검사한다.
기존 삭제·복구와 확인 실패·재시도 및 실행 취소 만료 검사는 유지했다.
수정 전 apply 경로라면 실패 주입 후에도 저장되어 실패 안내/미저장 assertion을
충족하지 못한다. 다만 아래 환경 차단으로 수정 전 실패 재현과 수정 후 통과를
실행으로 확인하지 못했다.

## 이번 로컬 검사 (2026-11-07)

- `cd android && bash ./gradlew :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.CatalogTest --tests com.beforearrival.briefing.MainActivityTest`: 시작 차단, exit 1.
- `cd android && bash ./gradlew :app:testSampleDebugUnitTest`: 시작 차단, exit 1.
- `cd android && bash ./gradlew :app:lintSampleDebug`: 시작 차단, exit 1.
- 세 명령 모두 `JAVA_HOME is not set and no 'java' command could be found in your PATH`.
  새 테스트의 컴파일·실행과 lint는 미검증이며 통과로 간주하지 않는다.
  toolchain·라이선스·credentials 설정은 변경하지 않았다. 전체 회귀 검증은 GitHub CI에 남긴다.
- `git diff --check`: 통과.
- 최종 `git status --short --untracked-files=all`: 허용된 다섯 파일만 변경.

브라우저 대상 변경은 없으며 브라우저 검사는 수행하지 않았다. APK 빌드·설치,
에뮬레이터 실행, 실제 Pleos SDK 콜백, 기기 간 동기화, 제출 준비 여부는 각각
별도 미검증이다. 샘플 차량 상태 주입은 실제 SDK 콜백의 증거가 아니다.
