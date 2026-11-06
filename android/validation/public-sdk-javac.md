# 공개 Pleos SDK 컴파일 확인

2026-11-06 KST에 사용자 계정이나 인증 헤더 없이 공식 Maven의 NaviHelper 3.0.5와 전이 의존성 navi-data 3.0.4의 POM 및 AAR을 받았다. SHA-256은 각각 Maven의 게시 체크섬과 일치했다. [Pleos SDK 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)

| 파일 | SHA-256 |
| --- | --- |
| NaviHelper-3.0.5.aar | `15fadd11c7c611b1abdf7ebf9ce91f2a6ce2728e60b4e38b4343e11bb9c12e97` |
| navi-data-3.0.4.aar | `7ca00e592134b53237a48cc3b7864063ecc5e2885465e3ce9c8c2a29789a2e2b` |
| Android API 35 공개 android.jar | `ee568219aa3754977207a8fd849fdbf3a299658dd26dd0907d39ca830587f8aa` |

각 AAR의 classes.jar를 추출하고 JDK 17의 `javap -p -s`로 검사했다. NaviHelper의 `getDestinationInfo()`와 리스너의 `onDestinationInfo(DestinationInfo)`가 확인됐다. navi-data의 `DestinationInfo.getRemainTime()` 반환 타입은 `int`이고 디스크립터는 `()I`다. [Android API 35 스텁](https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/android15-release/35/public/)

`javac -encoding UTF-8 --release 17`에 Android API 35 공개 스텁, 두 AAR에서 추출한 classes.jar, POM에 지정된 Kotlin stdlib 1.9.25를 클래스패스로 제공했다. `PleosRouteConnection.java`, `LiveConnectionFactory.java`와 두 파일의 공통 Java 의존 소스가 종료 코드 0으로 컴파일됐다. 기준 커밋은 `8e8695ee7f8fe0103bf4c91b15a3a405b06a6df9`이며, 로컬 컴파일에 사용한 보관본과 기준 커밋 사이에 Android 소스 변경이 없음을 Git diff로 확인했다.

이 검사는 Java 어댑터의 API 호환성을 확인한다. Gradle의 전체 Pleos APK 패키징, 설치, 권한 부여, 실제 콜백은 별도의 검증 단계다. 시간 단위와 유효 범위, 무효값을 확인한 뒤에만 `NaviTimeContract`의 차단을 해제한다. 기기에서 확인할 때는 화면의 남은 시간과 원시 콜백을 같은 시각에 기록하고, 경로 취소와 권한 회수 뒤에도 같은 입력을 재검사한다.
