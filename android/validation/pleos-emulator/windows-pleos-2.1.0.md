# Windows Pleos Connect 2.1.0 실측

2026년 11월 8일 KST에 PR #4의 코드 `7bda357f3f6f2a286fa163aa11bf8eacfdbec3d2`를 검증했다. 이 기록을 추가한 커밋과 검증 APK의 소스 커밋은 구분한다. [같은 소스의 APK 빌드](https://github.com/jhuni17/before-arrival-pleos/actions/runs/42389947998)에서 추적 입력 63개를 대조했다. 검증용으로 서명한 APK의 내용은 CI 산출물과 일치하며, 에뮬레이터에서 다시 꺼낸 `base.apk`의 SHA-256은 `0930eae0f961ccb0c51ad1aec26e5b13b86b1753a26e4e401e287e30ab0f3312`였다.

Windows x64에서 공식 Pleos 이미지 v2.1.0과 Android Emulator 37.2.13.0을 WHPX로 실행했다. 앱은 API 34 차량 이미지에서 NaviHelper 3.0.5를 사용했다. SDK 도구가 이미지 목록을 인식하지 못해 공급사가 제공한 기기 프로필로 전용 AVD를 구성했다. 이미지 아카이브의 SHA-1은 공식 목록과 일치했다. Virtual Console은 `emulator-5562`에서 로컬로 연결했다. [Pleos 설치 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) · [Virtual Console 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

## 실제 NaviHelper 콜백

기본 내비에서 공개 장소를 선택해 안내를 시작한 뒤 검증 앱을 열어 실제 경로를 조회했다. 앱은 `pleos.car.permission.NAVI_ROUTE` 권한을 부여받았다. 표의 시각은 콜백 수신 UTC다.

| 시각 | 내비 안내 | 원시 `remainTime` |
| --- | --- | ---: |
| 22:41:11.200 | 공개 장소 A, 32분 | `1920` |
| 22:42:53.019 | 내비에서 안내 종료 후 앱을 다시 열어 조회 | `0` |
| 22:46:32.207 | 공개 장소 A, 안내 재시작, 31분 | `1860` |
| 22:48:00.693 | 공개 장소 B, 28분 | `1680` |

양수 세 건은 내비 표시 분을 초로 환산한 값과 일치했다. 안내 전에는 콜백이 없는 조회도 있었으며, 안내를 취소한 뒤 새 요청에는 `0`이 왔다. 기존 `NaviTimeContract` 보호 로직 때문에 앱은 원값을 기록한 뒤 `도착 시간 미확인`을 표시했다. 실제 도착 시의 값, SDK 전체 유효 범위, 기타 무효값, ETA가 표시된 상태에서 서비스·권한 상실 뒤의 갱신 여부는 이 실행으로 확인하지 못했다. [NaviHelper 목적지 콜백](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)

## 주행 제한과 실제 음성 중단

기본 이미지에서는 앱이 사용할 수 있는 한국어 오프라인 음성을 찾지 못했다. 전용 AVD에 eSpeak NG 1.52.0을 설치하고 내장 음성 데이터를 초기화한 뒤 샘플 브리핑으로 재생을 검증했다. [F-Droid eSpeak 패키지](https://f-droid.org/packages/com.reecedunn.espeak/)

1. P에서 `브리핑 듣기`를 눌렀다. 앱이 `playing=true`를 기록했고, Android는 음성 트랙 `383`을 `USAGE_MEDIA / CONTENT_TYPE_SPEECH` 및 `state:started`로 기록했다.
2. Virtual Console에서 P→D로 전환했다. 0 km/h에서도 앱이 `RESTRICTED`를 받고 브리핑 화면을 차단했다. 같은 음성 트랙이 `22:44:20.275` UTC에 정지하고 `22:44:20.276`에 해제됐다. 앱은 `1794091460.282`에 `interactive=false playing=false`를 기록했다.
3. D→P로 돌아오자 화면을 다시 쓸 수 있었고 `playing=false`가 유지됐다. 복귀 뒤 활성 음성 트랙이 없었다. 에뮬레이터 영상의 오디오에서도 D 전환 뒤 브리핑이 끊겼고, P 복귀 시점의 짧은 효과음 뒤 브리핑이 재개되지 않았다.

앱 시작 중 `UNKNOWN`에서 화면 조작을 차단한 로그도 확인했다. 이 음성 검증은 샘플 브리핑과 테스트 엔진 조합이다. 실제 경로의 브리핑 음성, 재생 중 `UNKNOWN` 전환, 실차 기본 음성은 별도 검증 항목이다.

[세션 시각·해시 및 오디오 트랙 근거](runtime-evidence.json)를 함께 보존한다. 화면과 오디오 원본은 테스트 PC에 보존했다.

## Astra High 재검수

검수 상태는 `NEEDS_REPAIR`다. 검수는 동일한 소스 SHA에 대해 제공한 런타임 자료와 코드·빌드 로그를 대조했다. 실제 콜백과 샘플 브리핑의 재생 중단 및 무자동재생을 인정했다. 앱에서 실제 ETA를 표시한 뒤 같은 세션에서 취소·도착·권한 또는 서비스 상실로 ETA가 지워지는지, 재생 도중 차량 상태가 `UNKNOWN`으로 바뀔 때 오디오가 끊기는지는 검증이 남았다. NaviHelper 3.0.5의 `remainTime` 공개 항목과 릴리즈 노트에는 검증에 필요한 유효 범위나 도착·무효값 정의가 명시되지 않았다. [remainTime API 항목](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/remain-time) · [SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)
