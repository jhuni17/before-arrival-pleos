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
