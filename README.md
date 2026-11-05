# 비공개 샘플 APK · 콘텐츠 확장 후보

소스: [`02f83ffada2733b4413339f4b75b1ac0e36d88fb`](https://github.com/jhuni17/before-arrival-pleos/tree/02f83ffada2733b4413339f4b75b1ac0e36d88fb) · [통합 PR #3](https://github.com/jhuni17/before-arrival-pleos/pull/3). 기존 `de8cc10`이 아닌, 출처 등록·순서·저장·공통 콘텐츠 모델을 추가한 정확한 새 커밋에서 빌드했습니다.

- APK: [before-arrival-sample-02f83ffada27.apk](before-arrival-sample-02f83ffada27.apk) — 파일 페이지의 Download raw file로 내려받을 수 있습니다. 저장소 접근 권한으로 로그인해야 합니다.
- APK SHA-256: `881c82e0aefabd7e3e91544b96e00deb15e20e92c8abc231975f4b2b55d80f4f`
- public debug 인증서 SHA-256: `6469377ec22e482c38f2f5eb0cb2538d3c413a34803a4cf2be7be2d9294216e4`. 원 차량 APK 인증서와 일치합니다. private key/서명키·계정정보·SDK 파일은 포함하지 않았습니다.
- 생성 시각: 2026-11-05T21:32:32+09:00
- 기존 승인된 API 35/Build-Tools 35.0.0/JDK 17/Gradle 캐시/debug 키를 재사용했습니다. 새 라이선스 동의나 키 생성은 수행하지 않았습니다.

## 검증

별도 깨끗한 detached 체크아웃에서 소스를 수정하지 않고 `:app:assembleSampleDebug :app:testSampleDebugUnitTest :app:lintSampleDebug`를 실행했습니다. SDK 자동 다운로드를 끄고, 기존 debug 키가 없거나 다른 경로면 중단하는 외부 Gradle 검사를 적용했습니다. lint는 오류 0개·경고 3개입니다. 실제 APK에 공통 `catalog.json`이 소스와 같은 바이트로 들어 있고 ContentCatalog가 DEX에 포함됐는지도 확인했습니다.

Android JVM 테스트 21개가 실패/오류/skip 없이 통과했습니다. 핵심 경로·접근 정책 JVM 검사 2개와 색상 검사도 통과했습니다. Android JVM은 Robolectric 모형이며 실기 콜백/실제 음성 검증이 아닙니다. APK v2 서명 검증이 통과하고 이전 APK의 public debug 인증서와 일치합니다. 빌드·테스트·lint·서명 로그는 [evidence](evidence)에 있습니다.

기본 빌드 명령(기존 JDK/SDK/서명 환경 설정 후 android/에서):

```sh
sh ./gradlew --offline --no-daemon --console=plain :app:assembleSampleDebug :app:testSampleDebugUnitTest :app:lintSampleDebug
sh checks/verify-core.sh
python3 checks/verify-colors.py
```

다운로드 후 `sha256sum -c SHA256SUMS`로 파일을 검증할 수 있습니다. 원 APK `d5be4460…`과 이번 APK는 다른 산출물입니다.

## 범위와 남은 항목

이 파일은 **sample debug APK**이며 공모전 제출용 release APK가 아닙니다. 실제 차량 상태를 확인하지 못하면 앱 조작·TTS가 잠깁니다. Pleos SDK 콜백·실제 ETA·에뮬레이터/실차·한국어 소리·차량 오디오 정책·기기 간 동기화는 실행 검증하지 않았습니다. 공식 페이지에서 확인한 콘텐츠 스냅샷 3개와 앱의 실제 새로고침 성공(0건)을 구분합니다. 이 산출물은 Astra의 최종 제출 승인이나 실제 콘텐츠 자동 조회 성공을 의미하지 않습니다.

소스 브랜치와 main은 이 빌드 작업에서 수정하지 않았습니다. 이 브랜치는 바이너리와 공개 검증 정보만 보관하는 비공개 산출물 브랜치입니다.

## 원격 재다운로드 확인

인증된 Git으로 이 비공개 산출물 브랜치를 별도 폴더에 새로 clone한 뒤 APK의 SHA-256 및 apksigner 검사를 다시 통과했습니다. [다운로드 확인 기록](evidence/download-verification.json)에 검사한 산출물 커밋·APK 해시·방법을 기록했습니다. 로그인 없는 raw HTTPS 요청은 404였으며, 로그인된 브라우저에서의 다운로드 조작을 실행했다고 주장하지 않습니다. 저장소 접근 권한으로 GitHub에 로그인한 뒤 위 APK 파일의 **Download raw file**을 사용하세요.

Git을 사용할 경우:

```sh
git clone --depth 1 --single-branch --branch artifacts/sample-content-02f83ffada27 https://github.com/jhuni17/before-arrival-pleos.git
cd before-arrival-pleos
sha256sum -c SHA256SUMS
```
