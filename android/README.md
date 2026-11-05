# 도착 전에 · Android Automotive OS 스타터

Android Automotive OS 헤드유닛에서 정차 중 사용할 수 있도록 만든 네이티브 Android 앱입니다. Android 10 이상 일반 Android 기기에서도 데모 화면을 열 수 있습니다. 화면의 도착 시간과 일정·게임 패치·커뮤니티 소식에는 예시 데이터를 사용합니다.

## 빌드와 실행

1. Android Studio에서 이 `android` 폴더를 엽니다. Gradle JDK는 JDK 17을 선택하고, SDK Manager에서 Android API 35와 Build-Tools 35.0.0을 설치합니다.
2. Gradle 동기화 후 Android Automotive OS 에뮬레이터 또는 Android API 29 이상 기기를 선택해 `app`을 실행합니다. 한국어 음성 재생을 확인하려면 기기의 TTS 엔진에 오프라인 한국어 음성을 설치합니다.
3. CLI에서는 이 폴더에서 Windows의 `gradlew.bat :app:assembleDebug` 또는 macOS·Linux의 `sh ./gradlew :app:assembleDebug`를 실행합니다. 결과 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다.

## 데모에서 확인할 동작

- **18분 · 08:40** — 듣기 예산은 45초입니다. 세 출처가 모두 켜져 있으면 약 32초 분량을 고른 우선순위대로 보여 줍니다.
- **4분 · 08:54** — 듣기 예산은 12초입니다. 08:58까지 회의 장소 확인이 필요한 일정이 먼저 나오고, 게임·커뮤니티 소식은 나중에 듣기로 옮겨집니다. 일정 출처를 끄면 11초짜리 게임 패치가 들어갈 수 있습니다. 13초짜리 커뮤니티는 4분 시나리오 예산을 넘습니다.
- 출처 켜기·끄기와 먼저 듣기 설정은 이 기기에 저장됩니다. 선택되거나 보류된 소식에 `저장`을 눌러 `도착해서 보기`에 넣고, 목록의 제목을 눌러 본문을 확인합니다.
- `브리핑 듣기`는 설치된 오프라인 한국어 음성을 사용합니다. `대본 보기`로 화면에서 같은 내용을 읽을 수 있습니다. 오디오 포커스를 얻지 못하거나 음성이 준비되지 않으면 상태가 표시됩니다.
- AAOS에서 UX 제한을 받거나 상태를 확인할 수 없을 때 화면을 잠그고 음성과 대화상자를 닫습니다. 액티비티가 일시정지될 때도 음성과 대화상자를 닫습니다. 정차 상태로 돌아왔을 때는 사용자가 다시 듣기를 눌러야 합니다.

## 브리핑 규칙

도착까지 3분을 남기고, 나머지 시간의 20%를 최대 45초까지 듣기 예산으로 배정합니다. 예시 소식에는 8초, 11초, 13초의 추정 낭독 길이를 두었습니다. 마감이 지난 소식의 우선권은 만료됩니다. 마감이 10분 이내에 다가온 소식을 먼저 검토한 뒤 선택한 우선순위와 기본 순서를 적용합니다. 각 소식은 남은 예산에 들어갈 때 포함합니다. 경로 입력에서 ETA를 확인할 수 없으면 예산은 0초입니다.

`RouteInput`은 남은 분, 관측 시각, 출처 표시를 제공하는 교체 지점입니다. 이 프로젝트에서는 `SampleRouteInput`을 연결했습니다. Pleos 환경에서 권한과 SDK를 확보한 뒤 NaviHelper v3.0.5의 `onDestinationInfo` 콜백으로 받는 `DestinationInfo.remainTime` 값의 단위와 유효 범위를 확인하고 이 인터페이스로 변환할 수 있습니다. 업데이트가 들어올 때 화면의 계획을 다시 계산하는 연결도 필요합니다. 일정·패치·커뮤니티의 외부 연동과 Pleos 실기 검증은 다음 단계입니다. [NaviHelper SDK 소개](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/intro), [목적지 조회](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations), [DestinationInfo API](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/entry)

## 브리핑 규칙 검사

JDK만 있는 환경에서는 이 폴더에서 다음 명령으로 두 ETA, 우선순위, 소스 비활성화, 예상 낭독 초과, ETA 미확인, 마감 경과를 검증할 수 있습니다.

```text
javac -encoding UTF-8 --release 17 -d build/core-check app/src/main/java/com/beforearrival/briefing/RouteInput.java app/src/main/java/com/beforearrival/briefing/SampleRouteInput.java app/src/main/java/com/beforearrival/briefing/Briefing.java checks/BriefingCheck.java
java -cp build/core-check com.beforearrival.briefing.BriefingCheck
```

이 작업 환경에서 위 JVM 검사를 통과했고, 공개 AOSP API 35와 차량 API 스텁으로 전체 Java 소스를 컴파일했습니다. APK 빌드와 에뮬레이터 검증에는 Android SDK 설치와 SDK 라이선스 동의가 필요합니다.

- [AAOS 정차용 앱과 UX 제한](https://developer.android.com/training/cars/parked/automotive-os)
- [AAOS 에뮬레이터 설정](https://developer.android.com/training/cars/testing/emulator)
- [Android 오디오 포커스](https://developer.android.com/media/optimize/audio-focus)
- [Android Gradle Plugin 8.9 요구 사항](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
