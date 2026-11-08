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
