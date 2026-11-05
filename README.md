# 비공개 샘플 APK · 27d297b29818

소스: [`27d297b2981826be52ee360c21c999379c82f776`](https://github.com/jhuni17/before-arrival-pleos/tree/27d297b2981826be52ee360c21c999379c82f776) · [PR #3](https://github.com/jhuni17/before-arrival-pleos/pull/3). 지정한 정확한 SHA를 별도 깨끗한 detached 체크아웃에서 새로 빌드했습니다. 이전 APK를 대체 사용하지 않았습니다.

- APK: [before-arrival-sample-27d297b29818.apk](before-arrival-sample-27d297b29818.apk) — 저장소 권한으로 GitHub에 로그인한 뒤 파일 페이지의 **Download raw file**을 사용하세요.
- APK SHA-256: `ae3118ea2a235d50d36e74b6f563071eca4ff37f178bc023b3d773f6d9fa7f4e`
- public debug 인증서 SHA-256: `6469377ec22e482c38f2f5eb0cb2538d3c413a34803a4cf2be7be2d9294216e4`. 원 차량 APK의 기존 인증서와 일치합니다.
- 생성 시각: 2026-11-05T22:31:01+09:00

## 검증

- 기록된 Android 입력 41개 전부 SHA-256 일치. 요청 SHA 및 작업 트리 clean 확인.
- sampleDebug APK 조립 성공. 실제 APK의 공통 카탈로그 asset도 소스와 같은 바이트입니다.
- 전체 Android JVM 24건 통과, 실패/오류/skip 0. Catalog·MainActivity·SpeechPlayback 검사를 포함합니다.
- 이전 df7b545의 `savedOfficialArticleOpensExactUrlOnlyWhileAllowed`는 **통과**했습니다. 대화상자 Handler 콜백을 처리한 뒤 정확한 원문 Intent를 확인하고, 제한 전환 후 큐에 있던 클릭과 오래된 버튼이 열지 못하는 것도 검사합니다. [개별 결과](evidence/previous-failure-regression.json).
- 핵심 경로·제한 JVM 검사 2개와 색상 검사 통과.
- lint 오류 0 / 경고 3.
- APK v2 apksigner 검증 통과, 기존 public debug 인증서 일치.

기존 승인된 SDK API35·Build-Tools35.0.0·JDK17.0.12·Gradle8.11.1·debug 키를 재사용했습니다. offline 모드, SDK 자동 다운로드 금지, 기존 키가 없거나 다른 경로이면 중단하는 외부 Gradle 검사를 사용했습니다. 새 라이선스 동의·새 키 생성·기존 키 변경은 하지 않았습니다. 서명키·SDK 파일·계정정보는 산출물에 포함하지 않았습니다.

Android 폴더에서 기존 환경을 설정한 뒤 실행할 기본 명령:

```sh
sh ./gradlew --offline --no-daemon --console=plain :app:assembleSampleDebug :app:testSampleDebugUnitTest :app:lintSampleDebug
sh checks/verify-core.sh
python3 checks/verify-colors.py
```

[빌드·검사·입력 해시·서명 근거](evidence)와 [provenance.json](provenance.json)을 동봉했습니다. 다운로드 후 `sha256sum -c SHA256SUMS`로 확인할 수 있습니다.

이 파일은 **sample debug APK**이며 제출용 release APK가 아닙니다. Android JVM/Robolectric 결과는 실제 기기·Pleos 콜백·ETA·한국어 음성·차량 오디오 정책 실행 증거가 아닙니다. 이 작업은 APK 빌드·검증·전달만 수행하며 main·통합 소스·기존 서명키를 변경하지 않았습니다. Astra의 독립 검수 판정은 별도입니다.

## 원격 재다운로드 검증

인증된 Git으로 이 비공개 산출물 브랜치를 별도 폴더에 새로 clone한 뒤 APK 해시·apksigner·기존 인증서 일치를 다시 확인했습니다. [다운로드 검사 기록](evidence/download-verification.json)과 [다운로드 APK 서명](evidence/redownload-signature.txt)을 동봉합니다. 로그인된 브라우저에서 다운로드 버튼을 직접 실행한 것으로 주장하지 않습니다. 저장소 권한으로 GitHub 로그인 후 APK 파일의 **Download raw file**을 사용하세요.

```sh
git clone --depth 1 --single-branch --branch artifacts/sample-content-27d297b29818 https://github.com/jhuni17/before-arrival-pleos.git
cd before-arrival-pleos
sha256sum -c SHA256SUMS
```
