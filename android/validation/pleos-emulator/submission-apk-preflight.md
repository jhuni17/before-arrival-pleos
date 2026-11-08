# 제출용 Pleos APK 사전 점검

이 문서는 제출 통과 기록이 아니라 사람이 후보마다 채울 체크리스트다. 저장소 점검 기준 SHA는 `fe53ed3cdcb62ddf9d1a8f65d71dbf8a0295c531`이며, 최종 후보 SHA와 혼동하지 않는다. 이 작업에서는 빌드·서명·설치·Windows 실행을 하지 않았다.

## 근거를 세 단계로 분리

| 단계 | 현재 저장소에서 확인할 수 있는 것 | 후보별 추가 근거 |
| --- | --- | --- |
| 컴파일 / CI | `pleos-emulator-validation.yml`은 특정 브랜치 PR에서 `assemblePleosDebug`를 실행하고 debug 서명을 제거한다. `source-sha.txt`, `input-sha256.txt`, `apk-sha256.txt`, 로그와 unsigned APK를 보존한다. `candidate-verification.yml`은 별도 조건의 **sampleDebug** 검사다. | 실제 실행 성공 여부와 후보 SHA의 artifact를 확인한다. workflow 정의 자체는 성공 기록이 아니다. |
| 제출 release APK | `sample`은 `.sample` 패키지, `pleos`는 `com.beforearrival.briefing`. release는 TTS 비교가 `off`이며 release signingConfig는 없다. | 승인된 별도 서명 절차를 거친 **Pleos release** APK의 빌드 출처, 서명 검증, 최종 바이트 해시. unsigned debug CI는 이를 대체하지 못한다. |
| 실제 에뮬레이터 | [기존 Windows 실측](windows-pleos-2.1.0.md)과 [세션 JSON](runtime-evidence.json)은 `7bda357…`의 로컬 debug 서명 APK 기록이다. | 최종 release APK를 깨끗한 AVD에 설치한 결과와 첫 실행, SDK 권한·콜백 및 기존 재현 절차의 관찰 결과. 과거 결과를 새 후보에 승계하지 않는다. |

`../apk-sha256.txt`와 `../apk-signature.txt`도 과거 **sample debug** 근거다. 서명 전후 APK는 해시가 달라지므로 unsigned CI 해시와 제출 파일 해시를 같다고 요구하지 않는다. CI 입력 대조와 release 빌드 로그로 소스 연계를 확인하고, 제출·설치에 쓰는 최종 서명 파일끼리 해시를 비교한다.

## 후보와 최종 파일 확인

- [ ] 후보의 전체 Git SHA, 깨끗한 작업 트리, 빌드 도구 버전(JDK 17, API 35, Build-Tools), 빌드 로그와 입력 해시를 보존한다. CI artifact의 `source-sha.txt`가 후보와 일치하고 `sha256sum -c input-sha256.txt`는 저장소 루트에서, `sha256sum -c apk-sha256.txt`는 artifact 폴더에서 검사한다. 이후 입력 변경이 있으면 다시 빌드한다.
- [ ] 현재 flavor/SDK 설정은 `android/app/build.gradle`과 `android/settings.gradle`에서 대조한다. release 패키징 task는 `:app:assemblePleosRelease`다. JDK/SDK와 의존성이 준비된 환경에서 `android/` 기준 `bash ./gradlew --no-daemon -Pandroid.builder.sdkDownload=false :app:assemblePleosRelease`를 사용한다. 이 task만으로 서명·설치 가능을 주장하지 않는다. 이 문서는 키 생성·접근이나 서명 설정 변경 절차를 제공하지 않는다.
- [ ] 승인된 절차로 이미 서명된 최종 파일을 받아 빌드 기록의 `pleosRelease` variant와 연결한다. 파일명만으로 flavor를 판정하지 않는다. APK의 패키지는 `com.beforearrival.briefing`, versionCode `1`, versionName `0.1`, minSdk `29`, targetSdk `35`를 현재 설정과 대조하고 `debuggable`이 false인지 확인한다.
- [ ] SDK는 NaviHelper `3.0.5`, TextToSpeech `3.3.1`이다. 최종 병합 manifest에서 `pleos.car.permission.NAVI_ROUTE`, 자동차 메타데이터·미디어 서비스 및 Gleo 수신 권한 `ai.umos.gleo.permission.APP_ACTION`을 확인한다. `TTS_SERVICE`의 명시적 선언은 `src/pleosDebug`에 있으므로 release에 있다고 가정하지 않는다. 최종 manifest와 해당 이미지의 실제 권한 부여·SDK 초기화 결과를 각각 기록한다. manifest 선언은 권한 부여나 SDK 콜백 성공의 증거가 아니다.

Windows PowerShell에서 준비된 SDK 도구로 **최종 파일**을 검사하는 예시다. 경로와 기대 해시는 검증된 전달 기록의 값으로 바꾼다. 명령은 이번 작업에서 실행하지 않았다.

```powershell
$candidate = '<full candidate SHA>'
git rev-parse HEAD   # $candidate 및 빌드 출처와 대조
git status --porcelain   # 빌드 입력 변경이 없어야 함
$apk = 'C:\submission\final-pleos-release.apk'
$expectedHash = '<approved final signed APK SHA-256>'
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash
if ($actualHash -ne $expectedHash) { throw 'APK hash mismatch' }
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\apkanalyzer.bat" manifest print $apk
if ($LASTEXITCODE -ne 0) { throw 'APK manifest inspection failed' }
```

- [ ] `apksigner`의 성공 로그와 인증서 SHA-256을 보존하고 승인된 제출 인증서와 대조한다. Android Debug 인증서이거나 출처를 확인할 수 없으면 보류한다. 서명 검증 성공만으로 release variant나 승인된 인증서임을 판정하지 않는다. `apkanalyzer`는 별도 설치된 Command-line Tools가 필요하며 현재 CI의 도구 존재 검사 범위에는 없다.

## 깨끗한 AVD 설치와 첫 실행

- [ ] [기존 재현 절차와 종료 기준](README.md#재현-절차와-종료-기준)을 그대로 사용한다. 공식 이미지의 새 전용 AVD 또는 Wipe Data한 전용 AVD를 쓰고 snapshot 복원·기존 앱 데이터·수동 권한 부여·추가 음성 엔진이 없는 초기 상태, 이미지/Emulator/Virtual Console 버전을 기록한다. 필요한 후속 환경 변경은 초기 실행 결과와 분리한다.
- [ ] 아래 명령에서 `adb devices -l`의 대상 serial을 고정한다. install 성공뿐 아니라 최초 실행 화면, crash/ANR 유무, 차량 상태 미확인 시 잠금, 플랫폼 UX 허용 후 접근 여부를 관찰한다. 설치·실행 명령 성공만으로 첫 실행 검증을 통과 처리하지 않는다.

```powershell
adb devices -l
$serial = '<clean Pleos AVD serial>'
adb -s $serial install $apk
if ($LASTEXITCODE -ne 0) { throw 'Clean install failed' }
adb -s $serial shell am start -W -n com.beforearrival.briefing/.MainActivity
adb -s $serial shell dumpsys package com.beforearrival.briefing
adb -s $serial shell pm path com.beforearrival.briefing
```

- [ ] `pm path`가 반환한 `base.apk` 경로를 `adb -s $serial pull <returned-path> <local-copy.apk>`로 가져와 `Get-FileHash`로 최종 제출 해시와 대조한다. 패키지·버전·실제 부여 권한, 설치·실행 출력, 화면과 로그를 후보 SHA/해시에 묶는다. 불일치·설치 실패·권한 실패는 통과가 아닌 보류 사유다.
- [ ] 기존 재현 절차에서 사용자 선택, 시간에 따른 편성, 재실행 후 저장 유지, 제한·미확인 전환을 확인한다. 샘플 경로 관찰과 실제 NaviHelper 콜백을 구분한다. 브리핑 편성·시작은 플랫폼 UX 허용 상태에서만 수행하고 기존 차량 제한 정책을 유지한다. 네트워크 음성/Gleo 실험은 이번 범위에서 유예하며 미검증으로 남긴다.

제출 기록에는 후보 SHA, 최종 APK SHA-256, variant/버전, 인증서 지문, 도구·AVD 버전, 단계별 통과/실패/미실행과 근거 위치를 남긴다. 현재 후보의 signed release 검증, clean Windows AVD 설치·첫 실행 및 런타임 재검증은 모두 **미실행/보류**다. 정식 심사 자료에는 연락처·계정 정보와 키·인증값을 넣지 않는다.
