# Android 저장 취소 실행 취소 검증

2026-11-07 로컬 작업 기록. 대상은 `android/` 주차용 프로토타입이다.

## 변경 및 검사 범위

- 브리핑과 저장 상세의 삭제는 동기 commit 성공 후에만 최근 한 건의 제목과 `삭제 실행 취소`를 표시한다. 실패한 삭제는 이전 기회를 유지한다.
- 복구는 삭제 당시 항목·확인 상태·인덱스를 사용하며 현재 목록 길이로 위치를 제한한다. 같은 key가 있으면 기존 항목을 보존하고 기회를 종료한다. 삭제·복구 실패 시 메모리와 기존 저장값 복원을 시도하고 재시도를 허용한다.
- 같은 항목의 확인 실행 취소는 삭제 성공 때 무효화한다. 제한·상태 미확인·pause·재생성 이후에는 이전 삭제 버튼 콜백도 무효다. 삭제·복구는 상세·원문·음성을 실행하지 않는다.
- CatalogTest에 전체 데이터·위치·중간 추가/확인/출처 변경·재저장·저장값 변경 후 false/예외·재시도·새 catalog 재조회 검사를 추가했다.
- MainActivityTest에 상세 삭제, 연속 삭제, 실패 시 이전 기회 유지, 복구 재시도, 확인 실행 취소 공존/무효화, 세 필터의 목록·수·첫 미확인 상태, 안전/수명 전환, 편성 대본 유지 검사를 추가했다.

## 재현 명령과 결과

저장소 루트에서:

```sh
cd android
sh ./gradlew --no-daemon --console=plain :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.CatalogTest --tests com.beforearrival.briefing.MainActivityTest
sh ./gradlew --no-daemon --console=plain :app:lintSampleDebug
```

두 명령 모두 exit 1: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` Gradle이 시작되지 않아 테스트와 lint 결과가 없다. 기존 안내의 `/workspace/tools-before-arrival` 및 일반 설치 경로에서도 사용 가능한 JDK를 찾지 못했다. 도구 설치, 라이선스 수락, 빌드 설정 변경은 하지 않았다. 최초 `:app:testSampleDebugUnitTest :app:lintSampleDebug` 결합 명령도 같은 이유로 시작 실패했다. 전체 회귀 실행은 GitHub CI에 남긴다.

```sh
git diff --check
git status --short --untracked-files=all
```

공백 검사 통과. 상태 확인에서 지정한 Java 네 파일과 이 보고서만 변경/추가되었음을 확인했다.

## 증거 한계

새 테스트는 작성했으나 실행되지 않았다. 사용자 선택·ETA 편성·저장·확인 실행 취소·음성의 기존 회귀와 새 삭제 복구 동작은 실행 검증이 남아 있다. APK 빌드, 에뮬레이터 실행, 실제 화면·설치·접근성, 실제 Pleos SDK 콜백은 검증하지 않았다. Robolectric 검사를 실제 기기 또는 제출 준비 완료 증거로 취급하지 않는다. 브라우저는 변경하거나 실행하지 않았으며 기기 간 연동을 검증하지 않았다. 캡처는 생성하지 않았다.

## CI 실패 기대값 수정 및 재검사 (2026-11-07)

후보 기준 SHA: `da4f35d6a7e745b28c17fef7292f8dd022486d2d` (수정 전 HEAD).
수용 계획에 제공된 CI XML 확인 결과는 57개 테스트 중 1개 실패이며,
`deletionUndoUpdatesEveryFilterAndRestoresConfirmedDetailDeletion`에서
`expected:<1> but was:<2>`였다. CI Android 입력 해시 60개도 일치했다는
계획의 근거를 사용했으며, 이번 작업에서 CI XML을 새로 내려받아 확인하지는 않았다.
다른 미확인 저장 항목이 남은 CONFIRMED 빈 결과는 안내문과 `전체 보기` 버튼의
두 자식 뷰로 구성된다. 저장 항목 수와 빈 결과 뷰 수를 혼동한 테스트 기대값을 수정했다.
제품 코드와 안전 정책은 변경하지 않았다.

세 필터 각각에서 삭제·복구 후 선택 유지, 제목과 순서, 전체·미확인 수 표시,
첫 미확인 진입 버튼과 대상, 빈 안내 상태를 검사하도록 보강했다.
ALL·UNREAD의 행 수는 fixture의 `items.size()`로 계산한다.
복구 후 CONFIRMED의 확인 완료 항목 표시와 빈 안내·전체 보기 버튼 제거를 검사하며,
같은 항목의 확인 실행 취소가 되살아나지 않는 기존 검사도 유지했다.

`android/`에서 아래 명령을 각각 실행했다:

```sh
sh ./gradlew --no-daemon --console=plain :app:testSampleDebugUnitTest --tests com.beforearrival.briefing.MainActivityTest.deletionUndoUpdatesEveryFilterAndRestoresConfirmedDetailDeletion
sh ./gradlew --no-daemon --console=plain :app:testSampleDebugUnitTest
sh ./gradlew --no-daemon --console=plain :app:lintSampleDebug
```

세 명령 모두 exit 1: `JAVA_HOME is not set and no 'java' command could be found in your PATH.`
Gradle 시작 전 차단되어 집중 검사·전체 단위 테스트·lint 모두 미검증이다.
이번 CI의 lint 결과도 제공된 근거에 없으므로 lint 통과 증거는 없다.
테스트 비활성화나 assertion 제거는 하지 않았다. 실제 회귀 통과 확인은 GitHub CI에 남는다.
JDK 설치, 빌드·도구체인·workflow 설정, 자격 증명, 라이선스, 서명 키는 변경하지 않았다.

이번 변경의 `git diff --check`는 통과했다. 최종 untracked 포함 상태 검사에서
계획의 `MainActivityTest.java`와 이 보고서 두 파일만 변경되었음을 확인했다.
앞 절의 Java 네 파일 언급은 이전 작업 기록이며 이번 diff 범위가 아니다.
저장 실패·재시도, 확인 실행 취소, 사용자 선택·ETA 편성·음성·안전 전환의 기존 회귀는
유지했으나 이번 환경에서 실행 검증하지 못했다. APK 빌드·설치·에뮬레이터·실제 Pleos SDK
실행 및 제출 준비 완료의 근거는 추가되지 않았다. Robolectric이나 unsigned APK를
그 근거로 표현하지 않는다. 브라우저 검사와 캡처는 수행하지 않았다.
