# 도착 전에

업무 일정과 관심 소식을 목적지까지 남은 시간에 맞춰 편성하고 읽어 주는 Pleos 차량용 출근 브리핑입니다.

## 제출용 차량 앱

- [서명된 release APK](apk/최재훈_도착전에_release.apk)와 [SHA-256](apk/SHA256SUMS)
- 검증 환경은 Windows의 Pleos Connect Emulator 2.1.0, Android Automotive API 34입니다. APK를 설치하고 기본 내비의 목적지 안내를 시작한 뒤 앱을 엽니다. 필요하면 ‘실제 경로 다시 연결’을 누릅니다.
- 새 설치에서는 업무 일정, 커뮤니티, 공식 게임 공지 순으로 시연 콘텐츠를 편성합니다. 업무 일정과 커뮤니티 글은 시연 예시입니다. 코엑스 56분에서 3건, 노선버스 전용 공영차고지 5분에서 업무 일정 1건과 Pleos 서윤 음성 재생을 확인했습니다. 영상 촬영 당시 코엑스 ETA는 46분이었습니다.
- 제출 파일과 Pleos에 설치된 APK의 SHA-256이 일치합니다. ETA 해석은 확인한 Pleos 2.1.0 이미지에 적용합니다. 이 이미지에서 첫 음성 재생은 약 15초, 반복 재생은 약 3.5초 뒤 시작했습니다.
- 소스에서 빌드할 때는 `android/`에서 JDK 17, Android SDK 35와 Pleos SDK 의존성을 갖추고 `./gradlew :app:assemblePleosRelease`를 실행합니다.

## 이전 검증 기록

현재 검토본은 공통 출처 모델로 Android 앱과 브라우저에서 출처 추가·사용 여부·순서·저장을 실제 편성에 반영한다. 공식 원문 스냅샷3개와 별도로 Steam 한 제공자는6db 로컬 앱 서버에서 실제 브라우저 갱신→공식 HTTPS→5개 항목·저장·재로드를 검증했다. Android 실기 네트워크는 미검증이며, 최신 도착 배치는 별도의 화면·독립 검토 근거를 따른다.

현재 후보 `21c83fdf61768eba7407922791a82f290ac766ea`는 제공된 승인 보고서에 따라 [CI #42477729327](https://github.com/jhuni17/before-arrival-pleos/actions/runs/42477729327)의 실제 Pleos SDK 컴파일·unsigned debug APK 빌드 성공으로 기록한다. CI 빌드 환경의 근거이며 설치·signed release·현재 후보 런타임 성공을 뜻하지 않는다. 이번 문서 검사는 CI 재조회나 재빌드 없이 수행했다.

과거 `7bda357f3f6f2a286fa163aa11bf8eacfdbec3d2`의 Windows x64/WHPX·Pleos Connect 2.1.0/API 34·NaviHelper 3.0.5에서는 원시 콜백 1920·1860·1680과 취소 후 새 조회의 0을 관찰했지만 당시 앱은 ETA를 표시하지 않았다. [Windows 기록](android/validation/pleos-emulator/windows-pleos-2.1.0.md)과 [세션 JSON](android/validation/pleos-emulator/runtime-evidence.json)을 따른다. `dc6cf91d678c3a7cb6ad38cbbeabb3c3c2a88ebc`의 같은 Windows Pleos 2.1.0/API 34 환경에서 D 상태 재생·미디어 명령은 [샘플 경로/eSpeak NG 시험](android/validation/pleos-emulator/commute-audio.md)이며 실제 Gleo 발화 성공이 아니다.

이전 디버그 후보의 ETA→예산·편성 및 경로 무효화→종료 검증 범위는 [기획서 5절](contest-brief.md#5-현재-구현과-남은-검증)에 기록했다. 18분→4분은 샘플 예시다. `27d297b`의 서명 sampleDebug APK와 JVM 24건은 [과거 기록](sources-notes.md)이다.

브라우저 재생은 무음 연출, 저장은 같은 주소의 브라우저에 한정되며 기기 간 동기화는 연출이다. 브라우저 제한 장면의 재생 초기화와 현재 Pleos 활성 큐의 차량 미디어 제어는 다르다. 편성·재생 시작은 차량 화면 사용 가능 시에만 하며 제한 중 설정·대본은 잠근다. 차량 상태 미확인 시 화면 상호작용과 음성을 중지한다. [시연 안내](showcase/REHEARSAL.md)의 상단 범위를 따른다.

## 실행

Python3과 curl이 있는 환경에서 저장소 루트의 다음 명령으로 실행한다. 빌드된 HTML은 저장소에 포함돼 있다.

```sh
python3 showcase/app_server.py
```

브라우저에서 `http://127.0.0.1:8765`를 연다. 기존 프록시·CA·TLS 설정을 유지하며 Steam이 허용된 환경이어야 새 소식을 가져올 수 있다. 이 로컬 앱 서버는 현재 HTML과 고정된 공식 Steam 조회만 제공한다. 외부 주소를 전달하는 범용 프록시가 아니며 공용 주소로 배포하지 않는다.

저장은 주소별이므로 같은 `127.0.0.1:8765`를 계속 사용한다. 파일 직접 열기·다른 포트·localhost 주소의 저장을 자동으로 가져오거나 지우지 않는다. HTML만 직접 열면 소식을 볼 수 있지만 새로고침은 실행 안내를 표시한다. 기존 직접 Steam 요청의 CORS 차단을 확인해 같은 출처의 로컬 조회로 바꿨다. 6db 로컬 서버는 허용된 외부 환경에서 실제 Steam UI 갱신·저장·재로드를 통과했다. 최신 도착 배치는 별도의 화면 검사·독립 검토 대상으로 기록하며 서버·전송 코드는 유지한다.

도착 보관함의 출처·미확인 필터는 현재 주소의 브라우저에 보존하고 제목 검색어는 현재 세션에서만 유지한다. ‘조건 모두 지우기’도 다음 방문에 반영한다. 출처 필터 저장 실패 시 현재 화면에만 적용되었음을 안내하며 재시도할 수 있다. Android 앱이나 다른 기기와 동기화하지 않는다.

휴대폰에서 관심사와 우선순위를 정해두면, 차가 목적지까지 남은 시간에 맞춰 짧은 브리핑을 골라줍니다. 자차로 출퇴근하는 사람이 일정 변경을 놓치지 않으면서 기다리던 게임 소식도 들을 수 있게 합니다.

- [공모전 기획서 본문 초안](contest-brief.md)
- [Codex Cloud 작업 지시서](cloud-handoff.md)
- `android/` 차량 화면과 브리핑 선택 로직
- `showcase/` 발표용 시연 디자인

데모는 가상 일정, 소식, 남은 시간을 사용합니다. 차량 화면의 확인과 읽어주기는 주차 상태를 기준으로 설계했습니다. 실차 출시 후보는 Pleos Connect에서 실제 경로 정보, 기기 권한, 한국어 음성, 주행 제한을 각각 확인한 뒤 범위를 확정합니다.

시연 흐름은 휴대폰의 우선순위 변경, 차량에서 남은 시간 변경, 브리핑 순서·분량 갱신, 도착 후 이어보기 순입니다. 샘플로 시연한 장면과 에뮬레이터에서 확인한 장면에는 각각 근거를 표시합니다.

`showcase/before-arrival-showcase.html`을 열어 시연 디자인을 볼 수 있습니다. 소스를 수정한 뒤에는 Node.js 22로 `showcase/`에서 `npm ci`, `npm run build`, `npm run verify`를 차례로 실행합니다.
