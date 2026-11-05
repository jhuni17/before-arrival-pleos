# Android 검증 기록 · 원본 차량 작업

> 이 기록과 validation/ 로그는 차량 입력 `479db2bc4e55bd58e9cda1f9408ea294e1c8ebf3` 작업 환경의 결과다. 이 통합 환경에서 APK 바이너리를 받거나 서명을 다시 검사한 기록이 아니다. Android 소스 입력 30개는 통합 후 해시 대조를 통과했다. 현재 재현 범위는 [통합 기록](../integration-notes.md)에 있다.

확인 시각: 2026-11-05T20:41:46+09:00 (Asia/Seoul). 환경: before-arrival-pleos. 저장소: 비공개 `jhuni17/before-arrival-pleos`.

**샘플 APK 빌드와 JVM 검증은 통과했으나 실제 NaviHelper ETA 연동은 완료되지 않았습니다.** SDK 의존성을 받지 못했고 단위 계약도 확인되지 않아 실제 값 변환을 차단합니다. emulator/실차에서 실행한 범위는 **없음**입니다.

## 기준과 변경 범위

- 시작 HEAD 및 당시 원격 main: `94fa9ca1324029c07a8cee25b7771bb4f007e215`.
- 독립 브랜치: `codex/android-live-route`. `android/`만 변경했습니다. main에 직접 반영하지 않습니다.
- 앱 소스·테스트·Gradle 입력 해시는 [source-sha256.txt](validation/source-sha256.txt)에 고정했습니다. 최종 커밋은 이 기록을 담은 PR의 HEAD로 확인합니다. Android 폴더에서 `sha256sum -c validation/source-sha256.txt`로 검증할 수 있습니다.
- 시연 결과 `3ff697d261e417fa6a2ff18ddcb91ebd83c32a6a`와 merge-base가 위 기준 커밋임을 확인했습니다. 시연을 합치지 않았으며 시간·분량·문구 차이는 [DEMO-CONTRACT.md](DEMO-CONTRACT.md)에 남겼습니다.

## 복원·설치 상태와 승인

| 항목 | 최초 관측 | 이번 작업 결과 |
| --- | --- | --- |
| Java | Java 21 JRE만 존재, javac 없음 | JDK 17.0.12를 /workspace/tools-before-arrival/jdk-17.0.12에 설치 |
| Gradle | wrapper 소스만 존재, 배포본·캐시 없음 | 고정 SHA-256의 Gradle 8.11.1 다운로드·실행 |
| Android SDK | SDK, adb, emulator, 라이선스 기록 없음 | API 35 revision 2, Build-Tools 35.0.0, Platform-Tools 37.0.1 설치 |
| Android 라이선스 | 미승인으로 설치 중단됨 | 사용자가 이 채팅에서 명시적으로 동의한 뒤 android-sdk-license 승인·설치 |
| Pleos SDK | 로컬 AAR·캐시·설치 없음 | NaviHelper 3.0.5 다운로드는 Maven CONNECT/GET 403으로 차단 |
| Emulator / AVD | 실행 파일·시스템 이미지·AVD 없음, /dev/kvm 미노출 | Connect 실행 안 함; adb devices 목록 비어 있음 |
| 계정 | 런타임 secret/identity 설정 없음 | Pleos 로그인·계정 승인·에뮬레이터 약관 동의 상태는 확인하지 못함 |

이전 온보딩의 성공은 현재 도구 복원 근거로 사용하지 않았습니다. Android 라이선스를 제외한 별도의 계정·약관 승인 작업은 수행하지 않았습니다. GitHub는 연결된 저장소 권한으로 private/push 가능 상태를 확인했습니다.

## APK와 재현 명령

- 절대 경로: `/workspace/before-arrival-pleos/android/app/build/outputs/apk/sample/debug/app-sample-debug.apk`
- 패키지: `com.beforearrival.briefing.sample`, minSdk 29 / targetSdk 35.
- 크기: 2014935 bytes.
- SHA-256: `d5be44601d9085ba2c863508472e5b97d333b3a4a7d930778f33b84e693d1e35`.
- 서명: Android Debug, RSA 2048, APK Signature Scheme v2 검증 통과. 배포·출품용 서명 검증과 다릅니다.

JDK 17과 SDK를 설정한 뒤 `android/`에서:

```sh
sh ./gradlew --no-daemon --console=plain :app:clean :app:assembleSampleDebug :app:testSampleDebugUnitTest :app:lintSampleDebug
sh checks/verify-core.sh
python3 checks/verify-colors.py
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/sample/debug/app-sample-debug.apk
sha256sum -c validation/apk-sha256.txt
```

이번 환경은 JAVA_HOME=/workspace/tools-before-arrival/jdk-17.0.12, ANDROID_HOME=/workspace/tools-before-arrival/android-sdk, GRADLE_USER_HOME=/workspace/tools-before-arrival/gradle-cache를 사용했습니다. 네트워크 명령은 세션 프록시를 유지했고 Java HTTPS 프록시와 해당 CA 신뢰 저장소를 적용했습니다. 인증값은 읽거나 기록하지 않았습니다.

처음 비교한 증분 APK와 clean APK의 해시는 달랐습니다. 최종 소스의 **두 clean 빌드**는 동일한 APK 해시를 재현했습니다. 이는 같은 작업 공간·도구·debug 키에서의 결과이며 다른 기기의 debug 키까지 같다는 보장은 아닙니다. [두 clean 결과](validation/reproducibility.json), [빌드 로그](validation/clean-build.txt), [반복 빌드 로그](validation/repeat-clean-build.txt), [서명](validation/apk-signature.txt).

## 테스트 결과

| 검증 | 결과 | 근거 / 한계 |
| --- | --- | --- |
| sample clean APK | 성공 | 실제 Android API 35 android.car.jar로 전체 sample Java 컴파일·DEX·패키징 |
| Android JVM | 11 통과, 실패/오류/skip 0 | [테스트 목록](validation/test-summary.json); Robolectric 4.16.1 / API 35 |
| 핵심 JVM | BriefingCheck / NavigationCheck 통과 | [결과](validation/core-checks.txt); 정규화한 Duration fixture이며 SDK 콜백 실측 아님 |
| Android lint | 오류 0 / 경고 2 | [전체 결과](validation/lint.txt); 기존 DataExtractionRules, MissingApplicationIcon |
| 색 대비 | 주간 최소 6.28:1 / 야간 최소 8.81:1 | [실제 리소스 계산](validation/color-contrast.txt); 기기 픽셀/잘림 검사는 아님 |
| debug 서명 | 통과 | [서명 결과](validation/apk-signature.txt) |
| pleos APK | 실패 | [SDK Maven 403](validation/pleos-build-blocked.txt); 실제 SDK API 시그니처 컴파일 전 중단 |

JVM에서 18분 게임 우선·4분 마감 회의 우선, 45/12초 예산, 초 단위 정밀도, 출처 비활성화, 마감 경과, ETA 부재, 취소·해제·권한 오류 뒤 늦은 세션 콜백, 실제 입력과 샘플 격리를 검사했습니다. UI에서는 주입한 제한/미확인 상태의 잠금·대화상자 종료, 편성 갱신, 저장의 Activity 재생성 보존, 실제 모드 실패 시 샘플 미대체를 검사했습니다. TTS는 Android 모형으로 포커스 거부·손실·반납, 완료/오류, 12초 상한, 선행 묵음, 옛 콜백 격리를 확인했습니다. 실제 소리·발화 길이·차량 포커스 매트릭스의 결과가 아닙니다.

마지막 바이트 재현 검사에서 app/build를 clean했습니다. 통과한 HTML 테스트/lint 보고서는 `/workspace/before-arrival-pleos/android/build/verified-reports/`에 보존했고, 요약·로그는 validation/에 동봉했습니다.

## Pleos 문서와 남은 차단 요인

[3.0.5 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [SDK 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk), [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions), [초기화·해제](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/initialization-and-release), [이벤트](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners)를 웹 조회로 확인했습니다. 사용자 제공 api-reference 소개 URL에는 읽을 수 있는 본문이 없어 현행 docs 경로도 확인했습니다.

- 문서는 3.0.5의 `remainTime`이 남은 경로 시간 필드로 변경된 이름임을 알려주지만, 접근 가능한 [remainTime 페이지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/remain-time)에서 타입·단위·유효 범위·sentinel 본문은 노출되지 않았습니다. 단위를 추측하지 않았습니다. `NaviTimeContract.decode()`가 모든 raw 값을 거부하므로 현재 실제 ETA는 브리핑을 열지 못합니다.
- 공식 설정대로 `ai.pleos.playground:NaviHelper:3.0.5`와 전용 Maven을 지정했으나 `:app:assemblePleosDebug`가 POM GET 403에서 중단됐습니다. SDK 후보 소스의 시그니처 정확성도 실제 AAR로 검증해야 합니다.
- 실행 환경 정책은 restricted/enforced이며 `document.pleos.ai`, `nexus-playground.pleos.ai`가 허용 목록에 없습니다. 두 호스트의 curl CONNECT가 403입니다. 웹 조회 도구로 읽은 문서는 Cloud executor에서 SDK를 받을 수 있다는 증거가 아닙니다. 프록시 우회를 시도하지 않았습니다.
- [프로젝트/SDK 신청 안내](https://document.pleos.ai/docs/pleos-only/create-my-project)는 NaviHelper를 별도 신청 없이 사용 가능한 SDK로 안내합니다. 따라서 현재 403을 계정 승인 부족으로 단정하지 않습니다. 실제 기기의 `pleos.car.permission.NAVI_ROUTE` 부여 방식·상태는 확인이 필요합니다.
- [Connect 설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)의 2.1.0/API 34 이미지 URL도 같은 호스트에서 차단됩니다. 에뮬레이터·실차 설치, 실제 내비 callback, ETA 갱신, 권한 회수, 서비스 단절, 제한 전환, 한국어 음성, 포커스 경쟁은 **미실행**입니다.
- SDK 초기화 결과는 최초 연결만 알립니다. 조용한 서비스 단절을 즉시 감지하는 계약은 확인하지 못해 조회 오류/시간 초과로 처리하는 후보입니다. 실제 단절·취소/도착 이벤트 시그니처·라우트 식별·콜백 순서 검증 전에는 실차 안전성을 주장하지 않습니다.
- 시연의 시간·듣기 분량·상태 문구 불일치는 [인계](DEMO-CONTRACT.md)대로 해결해야 합니다. [시연 PR #1](https://github.com/jhuni17/before-arrival-pleos/pull/1)을 이 작업에서 병합하거나 수정하지 않았습니다.
