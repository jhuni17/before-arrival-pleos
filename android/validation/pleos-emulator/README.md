# Pleos Connect 에뮬레이터 검증

기준은 PR #3을 병합한 main `9b293c420a786f9ca3a96c8233c2b88d6beb9488`이다. 차량 동작 검증은 별도 PR에서 진행한다.

제출 후보의 서명·해시·새 AVD 첫 실행 확인은 [제출용 APK 사전 점검](submission-apk-preflight.md)을 따른다.

## 현재 근거

- 공식 Windows/Linux 이미지 목록 요청은 HTTP 200이었다. API 34 x86_64 패키지는 `system-images;android-34;pleos_car_emulator;x86_64`이고, 목록은 약 4.09 GB 아카이브와 별도 `pleos-connect-sdk-license`를 지정한다.
- Oracle은 x86_64, 2 vCPU, 약 1 GB RAM이다. `/dev/kvm`은 있으나 현재 러너 사용자에게 읽기·쓰기 권한이 없다. Windows에서는 공식 Pleos Connect 2.1.0을 전용 AVD로 설치해 검증했다. [실제 콜백, 주행 제한·음성 중단 기록](windows-pleos-2.1.0.md)을 참고한다.
- 이전 공개 AAR 컴파일은 [별도 검증 기록](../public-sdk-javac.md) 범위다. 이번 PR의 별도 CI는 고정 SHA에서 Pleos flavor를 빌드해 unsigned APK, 로그, 입력·APK 해시를 보존한다. 설치와 실제 콜백 검증은 런타임 단계에서 한다.
- 실제 ETA는 검증한 Pleos Connect 2.1.0 에뮬레이터 이미지와 debug 앱에서만 해석한다. 배포용 앱에서 사용하기 전에 지원 버전의 단위·범위·무효값을 확인해야 한다. 진단 기록은 debug Pleos 앱에서만 별도 로그 수준 설정으로 켤 수 있다.

## 재현 절차와 종료 기준

1. 공식 Pleos Connect AVD와 Virtual Console의 버전, 사용한 테스트 앱 SHA와 설치 APK 해시를 기록한다. 회사나 집 주소 대신 공개 시험 목적지를 사용한다. `adb devices -l`에서 대상 serial을 고정한다.
2. 실제 경로로 전환해 NaviHelper 초기화와 목적지 조회 응답을 확인한다. 두 개 이상의 경로 상태에서 에뮬레이터 내비가 표시한 남은 시간과 `onDestinationInfo`의 원시 `remainTime`을 같은 시각에 기록한다. 단위·범위·도착 및 무효값을 SDK 버전에 묶어 확인한 뒤 변환과 테스트를 변경한다.
3. 목적지 변경·취소 및 권한·서비스 상실 뒤, 이전 ETA가 다시 쓰이지 않는지 확인한다. 경로와 요청을 구분할 수 없는 콜백은 확인된 범위만 증거로 사용한다.
4. 실제 ETA가 표시된 상태에서 브리핑을 시작하고 Virtual Console로 D 주행 제한을 재현한다. 첫 주제를 듣는 중 Gleo를 열어 “도착 전에 브리핑, 다음 주제로 넘어가”라고 말한다. D를 유지하면서 인식 문장, Gleo 발신, 앱의 성공 응답, 첫 주제의 자연 종료 전에 두 번째 주제가 재생되는 것을 순서대로 확인한다. ADB 방송이나 미디어 키만으로 이 조건을 통과 처리하지 않는다. 차량 상태 미확인, 내비 안내 및 통화의 오디오 우선권은 별도로 확인한다. [출근 브리핑 오디오 큐 검증](commute-audio.md)을 참고한다.
5. SDK/AVD 버전, 소스 SHA, APK 해시, 재현 단계, 개인정보를 가린 화면·로그 및 관찰 범위를 남긴다. Astra가 같은 SHA의 근거를 수용 기준과 대조한다. 필요한 경우 실패 항목만 최대 두 차례 수정하고 다시 검증한다.

테스트 AVD에서 `adb -s <serial> shell setprop log.tag.PleosValidation DEBUG`를 실행한 후 앱을 열고 `adb -s <serial> logcat -v epoch -s PleosValidation:D`로 앱 태그를 수집한다. 로그는 음성 출력 자체의 증거가 아니므로 화면 및 실제 재생 관찰과 대조한다. 완료 후 `adb -s <serial> shell setprop log.tag.PleosValidation INFO`로 진단을 끈다.

공식 참고 자료는 [에뮬레이터 설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [Virtual Console](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [NaviHelper 목적지 콜백](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)이다.

디버그 전용 고정 한국어 TTS A/B 절차와 미측정 항목은 [TTS 비교](tts-comparison.md)를 참고한다. 이 변경의 Windows 실행 증거는 아직 없다.

## 오프라인 경로 수명 검사 (2026-11-08)

저장소 루트에서 실행한 명령:

```sh
PATH=/home/ubuntu/.local/share/before-arrival-runner/pleos-validation-stage.FmMxBcJv/jdk17-validation.k3GzMAZw/runtime/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH python3 android/validation/pleos-emulator/check-route-lifecycle.py
```

종료 코드 **0**. JDK 17로 실제 `PleosRouteConnection`, `NaviTimeContract`, `NavigationRouteInput`, `RouteInput`, `RouteConnection`, `PleosVerificationTrace` 여섯 소스를 임시 디렉터리에 그대로 복사하고 바이트 일치를 확인해 컴파일한다. 실행 출력에 실제 컴파일 입력 경로를 표시한다. Android·NaviHelper·Kotlin 경계만 최소 대역으로 제공하며 경로 판단·변환·무효화 로직은 복제하지 않는다. SDK 바이너리와의 호환성 확인은 위의 공개 SDK 컴파일 기록과 별개다.

- **관찰값 재생** (`observed-value replay`): `runtime-evidence.json`의 fingerprint와 원시 값 1920·0·1860·1680을 읽어 각각 새 helper/독립 조회로 전달했다. 양수는 같은 초 값의 `NAVI_HELPER` snapshot, 0은 ETA 없음으로 끝났다. 이 값들은 기존 Windows 관찰 기록에서 가져왔으며 하나의 연속 사건으로 재구성하지 않았다.
- **합성 사건 순서** (`synthetic`): 검증 이미지의 debug 조건에서 양수 세 값을 수용하고 release 또는 다른 fingerprint에서는 모두 거부했다. 유효 ETA 뒤 취소하면 ETA를 비우고, 보관한 이전 리스너의 양수 응답은 ETA와 발행 횟수를 바꾸지 않았다. 모든 발행에서 `SAMPLE`이 없음을 검사했다.
- **합성 시계**: 실제 대기 없이 Handler 큐를 진행했다. 정상 응답 후 4,999ms에는 조회 1회, 5,000ms에는 2회였다. 두 번째 요청의 무응답 9,999ms에는 기존 ETA를 유지하고 10,000ms에는 비웠다. 이후 60초에도 재조회하지 않았고 늦은 응답도 무시했다.
- **합성 재연결**: 기존 연결을 닫고 같은 실제 `NavigationRouteInput`으로 새 어댑터를 시작했다. 이전 리스너는 발행하지 않고 새 리스너의 1680만 수용했다. 취소·시간 초과·연결 종료에서 해당 helper의 리스너 제거와 release가 각 1회이며 Handler 예약 작업이 남지 않음을 assertion으로 확인했다.

실패 경로도 별도 확인했다. PATH에서 JDK를 제거한 실행은 종료 코드 **1**, 메모리 안의 검사 harness에 강제 assertion을 넣은 실행도 종료 코드 **1**이었고 두 경우 모두 PASS를 출력하지 않았다. 소스 파일에는 강제 실패를 남기지 않았다. 검사한 범위에서 생산 코드 결함은 재현되지 않았다.

`git diff --check` 종료 코드 **0**. 공유 Git 인덱스는 읽기 전용이므로 `/tmp`의 임시 인덱스 복사본에 새 파일을 intent-to-add로 표시했다. 임시 object 디렉터리와 원래 objects를 읽는 alternate도 지정했다. 이 `GIT_INDEX_FILE`로 실행한 `git diff --name-only`의 변경 집합은 아래 두 경로와 일치했다. 원래 인덱스는 변경하지 않았다.

```text
android/validation/pleos-emulator/README.md
android/validation/pleos-emulator/check-route-lifecycle.py
```

이 결과는 오프라인 합성 어댑터 검사다. UI·편성·미디어 서비스 실행, 실제 SDK 콜백·음성·APK 빌드의 증거가 아니다. 이번 변경의 Windows 실행 증거는 없다. 현재 후보에서 실제 ETA 표시→편성→재생·취소를 같은 Windows/Pleos 세션으로 확인하는 시험과 signed release 검증은 여전히 필요하다. 기존 Windows 원시 값 관찰을 이 미검증 범위의 통과로 사용하지 않는다.
