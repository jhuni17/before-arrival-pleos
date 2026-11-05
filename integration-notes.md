# 통합 검증 기록

통합 입력은 차량 PR [#2](https://github.com/jhuni17/before-arrival-pleos/pull/2)의 `479db2bc4e55bd58e9cda1f9408ea294e1c8ebf3`와 시연 PR [#1](https://github.com/jhuni17/before-arrival-pleos/pull/1)의 `3ff697d261e417fa6a2ff18ddcb91ebd83c32a6a`다. 공통 기준은 `94fa9ca1324029c07a8cee25b7771bb4f007e215`, 기계적 통합 커밋은 `bad7d4adc20e288a017c0545b03428181a408005`다. 검토 브랜치는 `codex/cloud-integration-20261105`이며 main 병합·공모전 제출은 수행하지 않았다.

## 변경과 소스 근거

HTML을 Android 샘플 계약에 맞췄다. 기본 출처는 일정·게임·커뮤니티 세 가지이고, 시각은 08:40/08:54, 게임11초·일정8초·커뮤니티13초 추정치다. 18분은 45초 예산에 세 항목32초, 4분은 12초 예산에 일정8초를 편성한다. 일정이 꺼지면 게임11초가 들어가며 커뮤니티13초는 보류한다. 커뮤니티를 기본 우선순위로 선택할 수 있다. 회의·커뮤니티 제목도 앱과 맞췄다. 추정치는 한국어 음성 실측이 아니다.

예산에 맞는 항목이 없으면 HTML의 재생·저장을 막고 이유를 표시한다. 도착 poster에는 더 이상 들을 경로가 없어 재생·저장을 막는다. 제한·상태 미확인에서 조작을 차단하고 복귀 후 자동 재생하지 않는다. 주차 표시는 샘플 상태이며 실제 기어 P 판정이 아니다. Android 코드는 변경하지 않았고 [30개 소스·Gradle 입력 대조](validation/integration/android-source-check.txt)가 모두 통과했다.

HTML 저장·설정은 동일 브라우저의 localStorage, Android 저장은 해당 앱 SharedPreferences에서만 실제 동작한다. 기기 간 동기화와 자동 저장 장면은 연출이다. Steam은 원본 조회의403·샘플 fallback 기록을 유지한다. HTML은18초 자동 시연이고, 기획서의40초 영상은 미제작 편집안이다. 녹화·제출 영상이 있다고 주장하지 않는다.

## 이 통합 환경에서 확인한 결과

| 검사 | 결과와 근거 |
| --- | --- |
| 의존성/HTML 빌드 | Node24.22.1/npm11.9.0에서 [npm ci](validation/integration/npm-ci.txt), [build](validation/integration/build.txt) 통과 |
| 정적/편성 회귀 | [verify](validation/integration/verify.txt) 통과: 예산·출처 제외·커뮤니티 우선순위·도착 차단 |
| 실제 브라우저 | Chromium155.0.8059.79 [33캡처·12동작 검사](validation/integration/browser.txt) 통과. 주야간·320/390px·가로·200% 글자·긴 한글·축소모션. 최소 대비5.95:1, 수평 넘침/잘림/page error/외부 요청 없음 |
| 추가 브라우저 회귀 | 4분 커뮤니티 단독 예산 초과, 저장 후 재조회, 별도 컨텍스트 비동기화, 손상 저장 fallback, 제한/미확인 전환·재생 미복귀, 도착 차단 |
| 핵심 JVM | JDK17.0.12로 원래 `JAVA_HOME=... sh checks/verify-core.sh` [통과](validation/integration/core-jdk17.txt): 편성·ETA 없음·초 정밀도·취소/권한 오류/해제 뒤 늦은 콜백·세션 분리·제한/미확인 차단. 정규화 fixture이며 SDK 실측이 아님 |
| Android 색 대비 | [리소스 검사](validation/integration/colors.txt) 주간 최소6.28:1, 야간8.81:1. 기기 픽셀/확대 실기 검증 아님 |
| 통합 APK 재현 | JDK17/Gradle8.11.1과 프록시·기존 CA로 [빌드 시도](validation/integration/gradle-jdk17-proxy.txt). `SDK location not found`로 중단. 이 환경에 SDK·APK가 없으며 라이선스 동의/SDK 설치를 수행하지 않음 |

브라우저 검증은 `bad7d4a` 위 작업 파일에서 수행했다. [상세 보고서](showcase/evidence/browser-report.json)의 `gitHeadAtRun`/`workingTreeDirtyAtRun`이 이를 명시하고, `htmlSha256` 및 핵심 소스별 SHA-256이 검사한 바이트를 고정한다. 보고서 자체를 담는 나중 커밋에서 실행했다고 소급 주장하지 않는다. 최종 파일의 해시가 보고서와 일치함을 확인했다. 320px 주간 긴 회의 제목과390px 야간 도착 캡처를 직접 열어 확인했다.

독립 시연 원본 `3ff697d`에서도 같은 도구로 build/verify/브라우저33캡처·9검사를 먼저 통과했다. 그 원본은 ‘약2분’ 표시와4분 커뮤니티 재생을 허용했으며 [실제 관측](validation/integration/demo-baseline-observation.json)을 남겼다. 통합 수정이 이 기존 차이를 해결했다.

## 원본 차량 작업에서만 확보한 근거

[android/VALIDATION.md](android/VALIDATION.md)와 `android/validation/`은 차량 입력 커밋 환경의 기록이다. 원본은 API35/Build-Tools35.0.0에서 sampleDebug APK2014935바이트, 같은 작업 공간·debug 키의 두 clean 빌드 동일 SHA-256 `d5be44601d9085ba2c863508472e5b97d333b3a4a7d930778f33b84e693d1e35`를 보고한다. 원본 [서명 로그](android/validation/apk-signature.txt)는 Android Debug RSA2048/v2통과, 인증서 SHA-256 `6469377ec22e482c38f2f5eb0cb2538d3c413a34803a4cf2be7be2d9294216e4`다. Android JVM11건과 lint0오류/2경고도 원본 작업 결과다.

APK는 원본 worker의 로컬 경로에만 있고 이 통합 환경에 전달되지 않았다. 바이너리 다운로드·독립 해시/서명 확인·설치·통합 APK 재빌드는 완료하지 않았다. 소스 입력30개 일치는 동일 바이너리를 여기서 생성했다는 뜻이 아니다. Android JVM11건의 제한 전환·저장·오디오 포커스·묵음·상한 테스트는 원본의 Robolectric 모형 결과이며 실제 음성/차량 오디오 검증이 아니다.

실제 Pleos 변형은 SDK Maven403에서 빌드가 막혔다. `NaviTimeContract.decode()`는 미확인 단위의 raw ETA를 모두 거부하므로 실제 ETA 브리핑 기능은 완료되지 않았다. SDK 시그니처·remainTime 단위/범위·권한·경로 취소/변경·서비스 단절·실제 제한 전환·한국어 낭독/포커스 경쟁은 미검증이다. 에뮬레이터·실차 실행과 실제 기기 간 동기화도 없다.

## 검토와 제출 범위

[review-notes.md](review-notes.md)는 초기 기준 커밋의 과거 Astra 기록이다. 이 통합 결과의 독립 검토 판정은 정확한 Sol 결과 커밋을 대상으로 PR에 남겨야 한다. 현재 문서는 Astra 승인이나 제출 완료를 뜻하지 않는다.

2026-11-05에 확인한 [공식 심사 기준](https://appdevchallenge.auto-hmg.io/guide/judging-awards)은 차량 특화 가치·창의성·Pleos SDK 활용·사업성이다. [제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)는1차 접수양식을 필수로, 영상/GitHub/APK를 추가 자료로 안내한다. GitHub 제출을 선택할 때 요구되는 실행 가능한 release APK(`apk/팀명_앱이름_release.apk`)와 현재 원본 sample debug 빌드 기록을 구분한다. 이 검토본에는 release APK가 없다. 공식 대회 일정과 별개로 사용자가 지정한 이번 작업 마감은2026-11-06 09:00 KST(00:00 UTC)다. 계정·연락처·외부 공유 권한·접수는 변경하지 않았다.
