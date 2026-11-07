# 주제별 출근 브리핑 검증

소스는 `dc6cf91d678c3a7cb6ad38cbbeabb3c3c2a88ebc`이다. [Pleos APK CI](https://github.com/jhuni17/before-arrival-pleos/actions/runs/42423826767)의 입력 69개와 unsigned APK 해시를 대조하고 기존 전용 테스트 키로 서명했다. Windows의 Pleos Connect 2.1.0/API 34에서 같은 앱을 설치했다. 설치용 APK SHA-256은 `d815cc97609f898b026f9ec1d16b3ffd61b8281c2923cc29190868ab39b8e945`다.

주차 상태에서 샘플 경로 18분을 선택했다. 기존 우선순위와 45초 듣기 예산으로 편성한 세 항목을 사용했다. 합성 음성은 전용 AVD에 설치한 오프라인 eSpeak NG 1.52.0으로 생성했다.

| 시험 | 관찰 |
| --- | --- |
| 재생 중 Virtual Console P→D | 앱의 로그에 `RESTRICTED interactive=false playing=true`가 찍혔다. 브리핑 화면은 잠겼고 Android에서 `USAGE_MEDIA / CONTENT_TYPE_SPEECH` 음성 트랙이 재생됐다. |
| D 상태에서 다음 주제 | 1번 트랙 재생을 확인하고 `cmd media_session dispatch next`를 보냈다. 2번 트랙이 시작됐고 Android MediaSession의 활성 항목 ID는 `1`이었다. |
| D 상태에서 일시정지 후 이어듣기 | 3번 트랙 3,313ms에서 일시정지했고 같은 위치에서 재생을 이어갔다. |
| Gleo 스키마와 명령 전달 | Gleo가 설치된 APK의 `schema.json`을 읽었다. 별도의 계측 APK로 테스트 앱과 같은 UID에서 세 명령을 보내 수신기가 다음·일시정지·이어듣기를 실행하는 것을 확인했다. |

에뮬레이터의 영상·음성 녹화는 테스트 PC에 보존했다. 녹화 SHA-256은 `22dd313215e5639e02af6e5fcce7548f87d6ac88c1c8cbfc374085657d88e4ee`다. 이 검증은 샘플 경로와 오프라인 시험 음성으로 진행했다. 실제 ETA로 편성한 재생, 실차 음성 엔진 및 Pleos App Market 심사는 별도 검증 항목이다.

이번 Windows AVD에서 음성 비서 세션은 열렸으나 에뮬레이터가 인증된 합성 음성 입력 서비스를 열지 않았다. 실제 한국어 발화가 Gleo에서 인식돼 앱 명령으로 전달되는 경로는 확인이 남아 있다. [Gleo 스키마 제공 가이드](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema)
