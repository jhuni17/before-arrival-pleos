# Android/Pleos 제출 증거 사전 점검 · 2026-11-06 KST

이 레인은 검증·문서만 변경한다. 차량 프로토타입, 브라우저 연출, 실제 Pleos 실행은 별도 증거다. 현재 HEAD는 `8e8695ee7f8fe0103bf4c91b15a3a405b06a6df9`. 명령별 실행 위치·시각·종료값·stdout/stderr는 [원본 로그](preflight-2026-11-06.txt)에 있다. 실행 위치는 저장소 루트이며 조회는 명령당 25초 상한, Maven HEAD는 연결 5초/전체 15초 상한이다. 이 문서의 기기 명령은 **미실행 절차**다.

## 제한적 점검표

| 항목 | 이번 세션의 현재 확인 | 판정 / 남은 일 |
| --- | --- | --- |
| 기존 도구 | `command -v` 개별 결과 java/javac/adb/emulator/apksigner 각 1, java/javac 버전 각 127 | 기존 JDK 없음. `verify-core.sh`는 미실행. 이 스크립트는 `android/build/core-check`에 쓰므로 허용 경로 밖에 생성하지 않음 |
| SDK·캐시 | JAVA_HOME/ANDROID_HOME/ANDROID_SDK_ROOT/GRADLE_USER_HOME 미설정. 로그의 표준·과거 경로 없음. `.gradle/caches`, Pleos `.m2` 경로 없음 | 전체 파일시스템 부재를 주장하지 않음. 숨은 다른 도구 위치는 미확인 |
| 런타임 | 표준 AVD 디렉터리와 `/dev/kvm` 없음. adb/emulator 없음 | `adb devices -l`, `emulator -list-avds` 실행 불가. 연결 기기/실행 중 emulator의 부재를 단정하지 않음. 설치·실행 미검증 |
| 로컬 산출물 | 저장소 깊이 8 이하 APK/AAR 검색 0개(.git/node_modules 제외), android/.github rg 검색 종료 1 | 서명·manifest·APK 해시 현재 재검사 불가. 첫 검색의 NameError는 로그에 보존하고 수정 조회를 추가함 |
| 소스 | 공식 Maven exclusive group과 NaviHelper 3.0.5 선언, Pleos NAVI_ROUTE 권한, decoder 전체 거부 확인 | 설정 확인은 의존성 resolve/컴파일 성공이 아님 |
| 공개 Maven 현재 접근 | 기록된 `curl -q --head ...NaviHelper-3.0.5.pom` 종료 56, CONNECT 403 `blocked-by-allowlist`; 도구도 도메인 정책 차단 보고 | 이 executor의 제한. 공개 Maven 전체 장애나 계정 미승인으로 해석하지 않음. 우회·재시도·다운로드 없음 |
| 환경 정책 | `/etc/codex/network-policy.json` 없음(종료 1), environment_status 도구 미제공 | 정책/자격증명 readiness 확인 불가. 비밀값·계정 파일 열람 없음 |
| 기존 host check | `python3 android/checks/verify-colors.py` 종료 0, 12개 비율 모두 4.5 이상, 주간 최소 6.28/야간 최소 8.81 | 리소스 계산만 확인. Java core/Android JVM/Gradle/lint/Chromium은 미실행 |

도구 설치, 라이선스 수락, 서명, 인증 다운로드, 유료 서비스, CI/계정 변경, commit/push/merge는 하지 않았다. Pleos artifact bytes는 변경에 포함하지 않았다.

## 사용자 제공 독립 검증 · 이번 세션 재검증 아님

2026-11-06 KST 공개 공식 저장소: <https://nexus-playground.pleos.ai/repository/maven-releases/>. 제공된 결과는 검증된 HTTPS에서 POM/AAR 다운로드 및 게시 SHA-256 일치다.

| 공식 상대 경로 | AAR SHA-256 |
| --- | --- |
| `ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.aar` | `15fadd11c7c611b1abdf7ebf9ce91f2a6ce2728e60b4e38b4343e11bb9c12e97` |
| `ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4.aar` | `7ca00e592134b53237a48cc3b7864063ecc5e2885465e3ce9c8c2a29789a2e2b` |

NaviHelper 3.0.5 POM은 `ai.pleos.playground:navi-data:3.0.4`를 선언한다. Java 17 `javap` 결과는 `initialize(Function0, Function1)`, `addListener(NaviHelperEventListener)`, `getDestinationInfo()`, `onDestinationInfo(ai.pleos.playground.navi.data.DestinationInfo)`를 확인한다. navi-data의 `javap -p -s` 결과는 `private final int remainTime`, `public final int getRemainTime()`, descriptor `()I`다. 이는 Java 타입을 확정하며 시간 단위·유효 범위·무효/sentinel 값은 확정하지 않는다.

독립 작업의 **다운로드·추출 전체 명령, 실제 로컬 AAR/classes.jar 경로, 원본 stdout/stderr·종료값은 제공되지 않았다**. `javap -p -s` 옵션 외에 실제 classpath 명령을 복원하지 않는다. 위는 Maven 경로이지 독립 작업자의 로컬 경로가 아니다. 원본 로그 확보가 남아 있으며 이번 환경은 이를 확보할 연결 도구가 없다.

기존 미추적 [public-sdk-javac.md](public-sdk-javac.md)는 API35 스텁/Kotlin 1.9.25를 이용한 어댑터 Java 컴파일 종료 0을 별도로 주장한다. 이 문서는 작업 시작부터 있었으며 수정하지 않았다. 완전한 명령·로컬 경로·원본 로그가 없는 별도 전달 기록으로 분류한다. 이번 세션의 컴파일 성공이나 사용자 제공 `javap` 검사와 합치지 않는다. 전체 Pleos Gradle 컴파일·DEX·패키징은 여전히 미검증이다.

[기존 VALIDATION](../VALIDATION.md)의 Maven 403·타입 미확인은 당시 환경 기록이다. 공개 Maven 접근과 `int` 타입은 위 독립 검증으로 갱신되었으나, 이 executor 접근은 별도 차단이다. `NaviTimeContract.decode()`는 그대로 모든 값을 거부한다. 초/분/밀리초 및 0/음수/최댓값의 뜻을 추측하거나 변환 계약을 만들지 않는다.

## 과거 제출 샘플과 현재 증거의 경계

[sources-notes.md](../../sources-notes.md)의 고정 소스 `27d297b2981826be52ee360c21c999379c82f776`, artifact `9bef7b49a0b88154d1f57fea8d8de47eb0df8554` 기록:

- `before-arrival-sample-27d297b29818.apk`, 2056299 bytes, SHA-256 `ae3118ea2a235d50d36e74b6f563071eca4ff37f178bc023b3d773f6d9fa7f4e`.
- Android Debug RSA2048/v2 검증 기록, 인증서 SHA-256 `6469377ec22e482c38f2f5eb0cb2538d3c413a34803a4cf2be7be2d9294216e4`, 패키지 `com.beforearrival.briefing.sample`, minSdk29/targetSdk35.
- sample assembly/core 및 Android JVM 24건 통과, lint 오류0/경고3, 41개 입력 대응은 과거 기록이다. 이번 세션의 현재 베이스 재빌드·재다운로드·서명 확인이 아니다.
- `.github/workflows/candidate-verification.yml`은 debug signingConfig를 null로 한 **unsigned sampleDebug**를 만든다. workflow 소스 확인은 CI 실행 성공 증거가 아니며 이 APK는 위 debug 서명 샘플·release 제출물·실제 Pleos 빌드를 대체하지 않는다.

과거 APK 검증 ≠ 현재 베이스 재빌드 ≠ Pleos SDK 전체 컴파일 ≠ 설치 ≠ 실제 callback/ETA 성공. 브라우저의 휴대폰 연출은 실제 기기 동기화가 아니다. 심사 자료에는 연락처·계정 정보를 넣지 않는다.

## 첫 기기 체크리스트 · 미실행

승인된 테스트 차량/환경에서 기존 도구와 검증 APK를 준비한 담당자가 아래를 실행한다. 이동 중 테스트하지 않는다. 검사 기록에는 시각·APK/소스 해시·기기 OS/빌드·명령·종료값·기대/관측 결과를 남기고 개인 목적지·기기 식별자·계정은 심사본에서 제외한다. 실패 시 중단하고 원인을 기록한다. 새 서명·라이선스·상태 우회·자동 샘플 대체는 절차에 없다.

1. **APK 해시·서명 먼저 확인.** 아래 경로 변수는 실제 보관 경로로 지정한다. 과거 고정 샘플만 위 해시/인증서와 비교한다. 다른 APK는 그 APK의 신뢰할 수 있는 manifest/해시 기록을 먼저 확보한다. CI unsigned 파일은 이 설치 절차의 대상이 아니다.

   ```sh
   APK=/absolute/path/to/before-arrival-sample-27d297b29818.apk
   APKSIGNER=/absolute/path/to/existing/build-tools/35.0.0/apksigner
   sha256sum "$APK"
   "$APKSIGNER" verify --verbose --print-certs "$APK"
   ```

   기대: 명령 종료0, 정확한 파일 해시/인증서 일치, v2 확인. 미일치·미서명이면 설치하지 않는다.

2. **연결·설치·패키지 확인.** `adb devices -l`에서 승인된 단일 테스트 기기의 `device` 상태를 확인한다. 여러 기기면 모든 후속 adb에 `-s <확인한 serial>`을 적용한다. unauthorized/offline이면 중단한다.

   ```sh
   adb devices -l
   adb install -r "$APK"
   PKG=com.beforearrival.briefing.sample
   adb shell pm path "$PKG"
   adb shell dumpsys package "$PKG"
   adb shell am start -W -n "$PKG/com.beforearrival.briefing.MainActivity"
   ```

   기대: install `Success`, pm path의 실제 APK 경로, dumpsys의 패키지·버전·권한/실제 granted 상태 확인, start 성공. 선언된 권한과 부여된 권한을 구분한다. sample 패키지는 NAVI_ROUTE 실권한이나 실제 SDK 성공을 증명하지 않는다. `install -r`의 인증서 충돌 시 삭제로 저장을 지우지 말고 중단한다.

3. **주차 상태·명시적 샘플.** 실제 차량 플랫폼이 사용 허용을 보고하는 주차 상태에서만 화면 조작한다. 일반 Android가 상태 미확인으로 잠기면 정상이며 우회하지 않는다. 화면에서 ‘샘플 경로’를 확인하고 18분/4분을 직접 선택한다. 세 기본 예시 출처 기준 18분 45초 예산·4분 12초 예산, 가까운 회의 우선 편성을 확인한다. 사용자 출처 활성/비활성·정렬·선택이 편성에 반영되는지, 예시와 공식 항목의 구분을 기록한다. 실제 현재 시각 ETA 검증으로 소개하지 않는다.

4. **저장·재실행.** 항목 저장/취소와 ‘도착해서 보기’의 제목·출처·원문/예시 표시를 확인한다. 저장 후 `adb shell am force-stop "$PKG"`와 위 `am start`로 다시 열어 선택·순서·저장이 유지되는지 확인한다. 자동 재생은 없어야 한다. 브라우저나 다른 기기와 동기화됐다고 기록하지 않는다.

5. **안전·음성.** 명시적 듣기에서 한국어 발음·실제 길이와 음량을 기록한다. 플랫폼이 제공하는 테스트 방식으로 제한 및 상태 미확인 callback을 각각 유발하여 UI 조작/대화상자/저장/원문 열기/음성이 즉시 중단되는지 확인한다. 차량은 이동시키지 않는다. 허용 복귀·앱 복귀 뒤 자동 재생 금지, 다른 오디오의 포커스 거부/손실 시 발화 중지·재개 금지, 늦은 TTS/클릭 callback 무효화를 확인한다. 소리를 들을 수 없거나 플랫폼 상태를 유발할 수 없으면 해당 항목은 통과 대신 차단으로 기록한다.

## 실제 Pleos 기기 단계의 선행 조건

- 공식 3.0.5 시간 계약의 단위·유효 범위·sentinel/cancel/arrival 의미를 버전 고정 원문과 fixture로 확보한다. 관측만으로 단위를 확정하지 않는다. decoder 해제는 이 문서 레인 밖의 별도 검토 변경이다.
- 승인된 준비 환경에서 실제 의존성을 resolve하고 전체 어댑터 및 Pleos APK를 컴파일/패키징한 로그를 확보한다. 추후 실행 명령은 `cd android && sh ./gradlew --no-daemon --console=plain :app:assemblePleosDebug`이며 **이번에는 실행하지 않았다**. javap 또는 부분 javac는 이를 대체하지 않는다.
- 검증된 Pleos APK의 해시/서명부터 위 설치 절차를 반복한다. 이 flavor 패키지는 `com.beforearrival.briefing`이다. `PKG=com.beforearrival.briefing`으로 실제 manifest/dumpsys를 확인하고 `pleos.car.permission.NAVI_ROUTE`의 실제 부여를 확인한다. 강제 grant를 가정하지 않는다.
- 계약 미확인 동안 실제 모드가 ‘도착 시간 단위·유효 범위 확인 필요’/ETA 미확인으로 막히는 것이 정상이다. 실제 ETA 시연 성공으로 제출하지 않는다.
- 후속 계약 검증 빌드에서는 승인된 관측 수단으로 원시 `remainTime`, callback 수신시각/세션, 내비 화면 잔여시간과 ETA 변화를 같은 시각에 기록한다. 현재 앱이 원시값을 logcat에 출력한다고 가정하지 않는다. 필요 계측은 별도 작업이다. `adb logcat -v threadtime BeforeArrival:V '*:S'`는 오류 보조 관측만 가능하며 raw callback 증거를 대신하지 않는다.
- 취소·목적지 변경·도착·권한 회수·서비스 단절/무응답 및 뒤늦은 callback 각각에 시각과 상태를 남긴다. 플랫폼의 지원된 테스트 방법으로 유발하고, ETA 무효화·UI/음성 차단·옛 세션 폐기·수동 재연결을 확인한다. 실패 시 샘플 ETA로 자동 대체하면 실패다. 조용한 단절 즉시 감지 계약은 아직 미확인이다.

남은 차단: 현지 JDK/SDK/런타임/검증 APK 없음, 현 executor Maven 정책 차단, 독립 검증 원본 명령·경로·로그 미제공, 시간 계약 미확인, 전체 Pleos 빌드·설치·실제 callback·한국어 음성/차량 포커스/제한 전환 미실행. 제출 가능성이나 실제 ETA 성공을 선언하지 않는다.
