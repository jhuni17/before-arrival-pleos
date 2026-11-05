# 비공개 샘플 APK · df7b545 검토 후보

**Android 테스트 1건 실패: 전체 검증 통과 산출물이 아닙니다.** 요청된 정확한 소스의 APK와 실패 근거를 함께 전달합니다. 원인 수정이나 통합 소스 변경은 수행하지 않았습니다.

소스: [`df7b545fb6ef8b3531b4e55718626086e73a93c0`](https://github.com/jhuni17/before-arrival-pleos/tree/df7b545fb6ef8b3531b4e55718626086e73a93c0) · [PR #3](https://github.com/jhuni17/before-arrival-pleos/pull/3).

- APK: [before-arrival-sample-df7b545fb6ef.apk](before-arrival-sample-df7b545fb6ef.apk). 저장소 권한으로 로그인한 뒤 파일 페이지의 **Download raw file**을 사용하세요.
- APK SHA-256: `9b83b5c9253db48c4f3c20d7eec80b05fb853974d94511e57aee5d8a26a180df`
- public debug 인증서 SHA-256: `6469377ec22e482c38f2f5eb0cb2538d3c413a34803a4cf2be7be2d9294216e4`. 원 차량 APK 인증서와 일치합니다.
- 생성 시각: 2026-11-05T21:50:50+09:00
- 이전 `02f83ff` APK를 대체 사용하지 않았습니다. 새 detached 체크아웃에서 새 APK를 만들었습니다.
- 기존 승인된 SDK API 35·Build-Tools 35.0.0·JDK 17·Gradle 8.11.1·debug 키를 재사용했습니다. 새 라이선스 동의나 키 생성은 없습니다. 키·SDK·계정정보는 이 브랜치에 포함하지 않았습니다.

## 정확한 검증 결과

`validation/sources-v2/android-input-sha256.txt`의 입력 **33개 전부 일치**, 작업 트리 clean, 요청 SHA 일치를 확인했습니다. 공통 catalog.json도 APK의 assets에 같은 바이트로 포함됐습니다.

- `:app:assembleSampleDebug`: 성공.
- `:app:testSampleDebugUnitTest`: **23건 중 22 통과 / 1 실패 / 오류 0 / skip 0**.
- `:app:lintSampleDebug`: 오류 0 / 경고 3.
- 핵심 경로·제한 JVM 검사 2개 및 실제 리소스 색상 검사: 통과.
- apksigner: APK v2 서명 검증 통과, 기존 public debug 인증서와 일치.

Gradle은 offline 모드와 SDK 자동 다운로드 금지로 실행했습니다. 기존 debug 키가 없거나 다른 경로면 중단하는 외부 init 검사를 사용했습니다. 소스 파일을 고치거나 테스트를 제외해 성공으로 바꾸지 않았습니다.

```sh
sh ./gradlew --offline --no-daemon --console=plain :app:assembleSampleDebug :app:testSampleDebugUnitTest :app:lintSampleDebug
sh checks/verify-core.sh
python3 checks/verify-colors.py
```

첫 조합 실행은 Android 테스트 실패로 중단됐습니다. 그 후 `:app:assembleSampleDebug :app:lintSampleDebug`를 별도 실행해 APK 조립과 lint의 성공을 확인했습니다. 전체 테스트 결과는 최초 23건의 결과 그대로 보존합니다.

## 실패 내용

`MainActivityTest.savedOfficialArticleOpensExactUrlOnlyWhileAllowed`의 [223행](https://github.com/jhuni17/before-arrival-pleos/blob/df7b545fb6ef8b3531b4e55718626086e73a93c0/android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java#L223)에서 `opened.getAction()` 호출 시 `opened`가 null인 NullPointerException입니다. 저장한 공식 글 대화상자의 원문 버튼을 누른 직후 `getNextStartedActivity()`가 null을 반환했습니다.

소스의 버튼 클릭·Activity 전환 처리와 테스트의 비동기 메시지 처리 시점을 후속으로 확인해야 합니다. 이 결과만으로 실기에서 원문 열기가 고장 났다고 단정하지 않습니다. [실패 스택](evidence/test-failures.json), [전체 23건 결과](evidence/test-summary.json), [Gradle 실패 로그](evidence/android-tests-failed.txt), [분리한 조립·lint 성공](evidence/assemble-and-lint.txt), [입력 해시](evidence/input-hash-verification.txt)를 동봉합니다.

이 파일은 **sample debug APK**이며 제출용 release APK가 아닙니다. Robolectric 결과는 실제 기기 실행·Pleos 콜백·ETA·한국어 소리 검증을 뜻하지 않습니다. main과 통합 소스 브랜치는 수정하지 않았습니다.
