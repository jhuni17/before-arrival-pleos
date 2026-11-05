# 도착 전에 · 차량 앱

차량 UX 제한이 해제된 상태에서 사용자가 등록한 출처의 소식과 명시된 예시 콘텐츠를 편성하는 프로토타입입니다. 공통 모델로 출처 추가·사용 여부·순서·저장을 실제 편성·낭독 대본에 반영합니다. `sample`은 샘플 경로로 실행할 수 있고, `pleos`는 NaviHelper 3.0.5 연결 후보입니다. **실제 ETA 연동은 아직 완료되지 않았습니다.** Pleos Maven 접근이 차단됐고 `DestinationInfo.remainTime`의 단위·유효 범위를 확인하지 못해 실제 값 변환을 차단했습니다. 실제 SDK 콜백·Connect 실행 성공으로 소개하면 안 됩니다.

## 빌드

JDK 17, Android API 35, Build-Tools 35.0.0을 설치하고 Android SDK 라이선스에 동의합니다. `JAVA_HOME`, `ANDROID_HOME`을 해당 설치 위치로 설정한 뒤 이 폴더에서 실행합니다.

```sh
sh ./gradlew --no-daemon :app:assembleSampleDebug
sh ./gradlew --no-daemon :app:testSampleDebugUnitTest :app:lintSampleDebug
sh checks/verify-core.sh
python3 checks/verify-colors.py
```

APK: `app/build/outputs/apk/sample/debug/app-sample-debug.apk`
패키지: `com.beforearrival.briefing.sample`
테스트: `app/build/reports/tests/testSampleDebugUnitTest/index.html`
lint: `app/build/reports/lint-results-sampleDebug.html`

`assembleDebug`는 두 flavor를 모두 빌드하므로 Pleos 저장소 접근도 필요합니다. Pleos SDK가 없는 환경에서는 `assembleSampleDebug`만 선택합니다. 이 샘플 빌드에도 Android SDK는 필요합니다. 샘플 APK에는 Pleos SDK나 내비 조회 권한이 들어가지 않습니다. 실제 연결 후보는 `sh ./gradlew :app:assemblePleosDebug`로 별도 빌드하며 패키지는 `com.beforearrival.briefing`입니다. 이 후보는 실제 AAR 컴파일과 API 시그니처 검증이 남아 있습니다.

## 동작과 제한

- 기본 우선순위는 게임 패치입니다. 18분 · 08:40에서는 45초 예산으로 게임→일정→커뮤니티, 총 약 32초를 선택합니다. 4분 · 08:54에서는 마감 08:58인 일정 변경 8초를 우선하고 예산은 12초입니다. 일정 출처를 끄면 게임 패치 11초가 들어갑니다.
- 모델에는 남은 **초**와 관측 시각, SAMPLE/NAVI_HELPER 출처를 전달합니다. 예산은 `min(45, max(0, 남은초 - 180) / 5)`입니다. 시각은 마감 우선권 판단에 쓰고, ETA에서 주차·주행 상태를 추론하지 않습니다.
- 경로·설정으로 실제 편성이나 예산이 바뀌면 재생을 멈추며 사용자가 다시 눌러야 합니다. 같은 편성의 반복 갱신은 진행 중인 재생을 끊지 않습니다. 표시된 8/11/13초는 추정치이며 실제 한국어 음성 길이를 실측한 값이 아닙니다. TTS에는 전체 듣기 예산을 넘기지 않는 실행 시간 상한과 200ms 선행 묵음을 적용했습니다.
- 실제 경로 모드는 권한 누락·취소·목적지 변경·오류·응답 시간 초과·해제 시 ETA를 비웁니다. 이전 세션의 늦은 콜백은 무시합니다. 자동 샘플 대체는 하지 않고, 샘플 전환은 사용자가 명시적으로 선택해야 합니다.
- 실제 연결은 초기화 뒤 조회하고, 정상 수신 후 5초 간격으로 다시 조회하는 후보입니다. 각 조회의 응답 대기 상한은 10초입니다. SDK 초기화 콜백은 지속적인 서비스 연결 감시가 아니므로 조용한 서비스 단절은 즉시 확인할 수 없고 다음 조회의 오류/시간 초과에서 무효화합니다. 취소·목적지 변경 뒤에는 세션을 닫고 명시적으로 다시 연결합니다.
- 화면과 음성은 전경 상태이고 `CarUxRestrictions`가 사용을 허용할 때만 열립니다. 제한 또는 미확인 상태는 화면·대본·대화상자·TTS를 차단합니다. **일반 Android 기기도 차량 상태가 미확인이므로 잠깁니다.** 테스트의 상태 주입은 배포 APK에 조작 우회 기능을 추가하지 않습니다.
- TTS는 설치된 오프라인 한국어 음성만 선택하고 MEDIA/SPEECH 포커스를 얻은 뒤 시작합니다. 포커스 거부 시 발화하지 않으며 손실, 중단, 오류, 완료, 일시정지, 해제 시 포커스를 반납합니다. 포커스 복귀로 자동 재생하지 않습니다.
- 출처·순서·저장은 이 Android 앱에만 보관하며 브라우저/다른 기기와 동기화하지 않습니다. 공식 원문 스냅샷3개와 Steam 공식 갱신 클라이언트를 구현했습니다. Steam의 실제 HTTPS→동일 브라우저 함수/모델→저장 경로는 curl 기반 fetcher로 검증했으며, 이 Android 앱의 실제 네트워크 실행은 미검증입니다.

## 전달 APK와 현재 검증

고정 소스 `27d297b`의 sampleDebug APK는 artifact `9bef7b49a0b88154d1f57fea8d8de47eb0df8554`에 전달됐고 독립 원격 다운로드·해시·기존 debug 인증서 v2 서명을 확인했습니다. Android JVM24건 전부 통과, sample assembly/core 통과, lint0오류/3경고입니다. [APK 링크와 전체 검증 범위](../sources-notes.md)를 확인합니다. Android 입력을 바꾸지 않은 브라우저·문서 검토본도 동일한41개 입력을 유지하며 새 검토 커밋에서 APK를 빌드했다고 소개하지 않습니다. 실제 설치·음성·차량 callback·release 검증은 별도입니다.

## Pleos 연결을 완료하려면

1. 네트워크 정책에서 `nexus-playground.pleos.ai` 접근을 허용한 환경을 준비합니다. 현재 차단은 프록시 CONNECT 403으로, 계정 인증 실패나 SDK 사용 신청 반려로 확인된 것이 아닙니다.
2. [3.0.5 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)와 실제 AAR로 `remainTime`의 타입·단위·유효 범위·미설정/도착 표현을 확인합니다. 근거와 실제 콜백 fixture를 남기고 `NaviTimeContract.decode()`의 차단을 검증된 변환으로 교체합니다. 이름만 보고 분/초를 선택하면 안 됩니다.
3. `pleos.car.permission.NAVI_ROUTE`의 기기 부여 상태를 확인합니다. 조회만 하므로 NAVI_ROUTE_SEARCH/CUSTOM_MAP은 선언하지 않습니다. [공식 권한 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
4. 실제 3.0.5 AAR로 `assemblePleosDebug`를 통과시킨 뒤 Connect 2.1.0에서 설치, 실제 콜백·ETA 갱신, 권한 회수·목적지 취소·서비스 해제·제한 전환·한국어 음성·포커스 경쟁을 검증합니다. 수신값만으로 요청/경로 식별을 보장할 수 있는지도 확인합니다.
5. 검증 전까지 실제 연결 완료 문구와 영상 자막을 사용하지 않습니다.

[검증 기록](VALIDATION.md) · [시연 문자열·색상·글꼴 계약](DEMO-CONTRACT.md)
