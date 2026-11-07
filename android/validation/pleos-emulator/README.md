# Pleos Connect 에뮬레이터 검증

기준은 PR #3을 병합한 main `9b293c420a786f9ca3a96c8233c2b88d6beb9488`이다. 차량 동작 검증은 별도 PR에서 진행한다.

## 현재 근거

- 공식 Windows/Linux 이미지 목록 요청은 HTTP 200이었다. API 34 x86_64 패키지는 `system-images;android-34;pleos_car_emulator;x86_64`이고, 목록은 약 4.09 GB 아카이브와 별도 `pleos-connect-sdk-license`를 지정한다.
- Oracle은 x86_64, 2 vCPU, 약 1 GB RAM이다. `/dev/kvm`은 있으나 현재 러너 사용자에게 읽기·쓰기 권한이 없다. Windows에서는 Android Studio 경로는 확인했으나 설치된 Pleos AVD와 허용 기록은 확인하지 못했다.
- 이전 공개 AAR 컴파일은 [별도 검증 기록](../public-sdk-javac.md) 범위다. 이번 PR의 별도 CI는 고정 SHA에서 Pleos flavor를 빌드해 unsigned APK, 로그, 입력·APK 해시를 보존한다. 설치와 실제 콜백 검증은 런타임 단계에서 한다.
- 앱의 ETA 변환은 SDK 계약을 확인할 때까지 계속 차단한다. 진단 기록은 debug Pleos 앱에서만 별도 로그 수준 설정으로 켤 수 있다. 원시 남은 시간, 시각, 권한·세션·주행 상태와 재생 여부만 기록한다.

## 재현 절차와 종료 기준

1. 공식 Pleos Connect AVD와 Virtual Console의 버전, 사용한 테스트 앱 SHA와 설치 APK 해시를 기록한다. 회사나 집 주소 대신 공개 시험 목적지를 사용한다. `adb devices -l`에서 대상 serial을 고정한다.
2. 실제 경로로 전환해 NaviHelper 초기화와 목적지 조회 응답을 확인한다. 두 개 이상의 경로 상태에서 에뮬레이터 내비가 표시한 남은 시간과 `onDestinationInfo`의 원시 `remainTime`을 같은 시각에 기록한다. 단위·범위·도착 및 무효값을 SDK 버전에 묶어 확인한 뒤 변환과 테스트를 변경한다.
3. 목적지 변경·취소 및 권한·서비스 상실 뒤, 이전 ETA가 다시 쓰이지 않는지 확인한다. 경로와 요청을 구분할 수 없는 콜백은 확인된 범위만 증거로 사용한다.
4. 주차 상태에서 브리핑이 실제로 재생되는 것을 확인하고 Virtual Console로 제한 상태와 복귀를 재현한다. 화면 조작 중지, 재생 전후 상태와 실제 음성 중단, 복귀 후 자동 재생 없음, 상태 미확인 처리의 근거를 나눠 기록한다.
5. SDK/AVD 버전, 소스 SHA, APK 해시, 재현 단계, 개인정보를 가린 화면·로그 및 관찰 범위를 남긴다. Astra가 같은 SHA의 근거를 수용 기준과 대조한다. 필요한 경우 실패 항목만 최대 두 차례 수정하고 다시 검증한다.

테스트 AVD에서 `adb -s <serial> shell setprop log.tag.PleosValidation DEBUG`를 실행한 후 앱을 열고 `adb -s <serial> logcat -v epoch -s PleosValidation:D`로 앱 태그를 수집한다. 로그는 음성 출력 자체의 증거가 아니므로 화면 및 실제 재생 관찰과 대조한다. 완료 후 `adb -s <serial> shell setprop log.tag.PleosValidation INFO`로 진단을 끈다.

공식 참고 자료는 [에뮬레이터 설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [Virtual Console](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [NaviHelper 목적지 콜백](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)이다.
