# 사용자 출처 기능과 검증 기록

2026-11-05 확인. 구현·APK의 고정 소스는 `27d297b2981826be52ee360c21c999379c82f776`, 전달 artifact 커밋은 `9bef7b49a0b88154d1f57fea8d8de47eb0df8554`다. 이후 문서와 브라우저 로컬 앱 서버를 갱신한 검토본도 아래41개 APK 입력과 동일하다. APK를 새 검토 커밋에서 다시 빌드했다고 소개하지 않는다. 브라우저 서버 수정은 별도 검증 대상이다.

## 현재 동작

브라우저·Android가 공통 출처 모델을 읽고10종의 공식 게임·채널·게시판 주소를 등록·활성화·정렬한다. 켜 둔 출처에서 확인된 항목만 ETA 예산과 일정 마감에 따라 실제 편성한다. 원래 일정·게임·커뮤니티 예시와 구형 설정·우선순위·저장을 유지한다. 18분은45초, 4분은12초 듣기 예산이며, 가까운 일정 마감이 출처 순서보다 먼저다. 표시 길이는 실제 음성 실측이 아닌 추정치다.

설정·저장은 Android의 해당 앱 SharedPreferences와 같은 브라우저의 localStorage에 각각 남는다. 휴대폰 모형도 브라우저 안에서 동작하며 계정·기기 간 동기화는 없다. 자동18초 시연은 실제 저장을 만들지 않는다. 구형 임의 저장 제목은 원문 링크를 만들어 붙이지 않고 예시 항목으로 보존한다.

4분에서 듣지 못하는 항목에는 명시적 저장 동작이 있다. 저장해도 회의 우선순위는 바뀌지 않으며 도착 보관함과 재실행 뒤에 자기 출처·원문·실제/예시 표시를 유지한다. Android의 저장 공식 항목에는 원문 열기가 있고, 버튼 callback 실행 시점에도 주차 사용 허용과 공식 URL을 검사한다. 제한·미확인·백그라운드에서는 화면·음성·대화상자를 막고 복귀 후 자동 재생하지 않는다. 비활성→재활성 사이 늦은 갱신 응답도 폐기한다.

## 전달한 샘플 APK

[비공개 GitHub APK 파일](https://github.com/jhuni17/before-arrival-pleos/blob/9bef7b49a0b88154d1f57fea8d8de47eb0df8554/before-arrival-sample-27d297b29818.apk) · [고정 artifact와 근거](https://github.com/jhuni17/before-arrival-pleos/tree/9bef7b49a0b88154d1f57fea8d8de47eb0df8554)

| 항목 | 확인 결과 |
| --- | --- |
| 빌드 소스 | `27d297b2981826be52ee360c21c999379c82f776` |
| 파일 | `before-arrival-sample-27d297b29818.apk`, 2056299 bytes |
| APK SHA-256 | `ae3118ea2a235d50d36e74b6f563071eca4ff37f178bc023b3d773f6d9fa7f4e` |
| 서명 | v2 검증 통과, Android Debug RSA2048, 기존 debug 인증서 유지 |
| 인증서 SHA-256 | `6469377ec22e482c38f2f5eb0cb2538d3c413a34803a4cf2be7be2d9294216e4` |
| 패키지 | `com.beforearrival.briefing.sample`, debuggable, minSdk29/targetSdk35 |
| 빌드·테스트 | sampleDebug assembly/core 통과, Android JVM24건 전부 통과(실패·오류·skip0), lint0오류/3경고 |
| 소스 대응 | 전체41개 입력 해시, 내장 catalog·실제 Steam 응답 assets가 고정 소스와 일치 |

승인된 원래 JDK17/SDK35/Gradle8.11.1 작업 환경이 기존 키로 빌드했다. 독립 검수자는 원격 artifact 커밋을 별도의 새 Git 저장소로 가져와 APK를 다시 얻고 hash·apksig v2·manifest·내장 assets·41개 입력을 확인했다. 이는 실제 인증 Git 다운로드 검증이며, GitHub 웹 Download 버튼 클릭이나 독립 작업 환경의 APK 바이트 재빌드를 수행한 결과로 확대하지 않는다. 구형 worker 로컬 링크에 의존하지 않는다.

저장 원문 callback·제한 전환·비활성 출처의 늦은 응답·실제 CDN 응답 회귀를 포함한24개 Android JVM 테스트가 통과했다. 이전 `df7b545`의23건 중1건 실패와02/df7 APK는 과거 중간 기록이며 이 전달본을 대신하지 않는다. 수정된 테스트는 허용/제한 callback을 실제 실행한 뒤 정확한 Intent 또는 부재를 단언한다. 제품의 guard를 제거하지 않았다.

## 콘텐츠와 실제 네트워크 근거

LoL26.22·Valorant13.09·메이플1.2.420 세 건은 공식 원문에서 연구 도구로 가져온 **스냅샷**이다. [조회 기록](shared/evidence/official-observations.json)의 원문·게시일·확인 시각을 유지하며, 메이플은 날짜만 제공돼 시각을 만들지 않았다. 이 세 건을 앱의 자동 갱신 성공으로 세지 않는다.

Steam은 공개 공식 query의 실제 전송과 앱 함수·모델·저장을 별도로 검증했다. Steam 허용 실행 환경에서 기존 프록시·CA·TLS를 유지한 실제 HTTPS 요청2건이 CONNECT200/HTTP200/TLS검증0을 반환했다. **curl 기반 fetcher가 전송만 담당하고 소스27의 변경 없는 `refreshSteam`→파서→`applyFeed`→중복 제거 저장→디스크 `readRegistry`가5개 항목을 처리했다.** Steam만 켜면18분에30초 항목1개를 선택하고4개를 보류하며, 4분12초 예산에서는5개 모두 보류했다. 이는 한 제공자의 실제 네트워크→모델→저장 연동 근거다.

같은 외부 환경의 Chromium API 최상위 탐색은 HTTP200/JSON5건을 받았다. 이후 실제 UI 문서도 HTTP200으로 열렸으나 기본 브라우저 fetch의 Steam 응답은 HTTP200·Access-Control-Allow-Origin 부재로 CDP `MissingAllowOriginHeader`/`net::ERR_FAILED`가 발생했고, UI 오류와 항목0이 저장되는 **실제 CORS 결함**을 확인했다. 이는 앞선 문서 로딩 차단/당시 CORS 미검증 기록을 갱신한다.

이를 해결하기 위해 `python3 showcase/app_server.py`로 시작하는127.0.0.1 전용 앱 서버를 추가했다. 기존 HTML과 같은 출처의 고정 Steam 조회만 제공하고, appid 이외의 URL·헤더·자격증명을 받거나 전달하지 않는다. 공식 HTTPS·기존 프록시/CA/TLS·리다이렉트 금지·시간/크기 제한을 유지한다. Host·Origin 검증으로 외부 페이지 접근을 막으며 CORS를 끄거나 공개 프록시를 쓰지 않는다. 서버가 제공한 HTML만 같은 출처의 조회를 사용하고, 단독 HTML은 갱신 실행 안내를 표시한다.

서버30건 검사와 실제 로컬 서버를 통한 브라우저 갱신→5개 캡처 항목→저장/재로드/ETA 검사에 통과했다. 이는 서버의 상류 응답을 테스트에서 주입한 **재생 검사**다. 이후 허용된 외부 환경의 정확6db 서버에서 응답 주입 없이 실제 브라우저 사용자 갱신을 수행해 동일 출처 HTTP200 두 번·5개 항목 표시·저장/재로드·중복 제거·원문5개 일치를 확인했다. Steam만 켜면18분에는1개 선택/4개 보류,4분에는0개 선택/5개 보류였다. 이 후속 실제 검사와 재생 검사를 구분한다. Android 앱의 실제 네트워크 fetch는 실행하지 않았다. 기존 통합 실행 환경의 이전 정책403과 Steam이 허용된 외부 환경을 구분한다.

[캡처한 실제 응답](shared/evidence/steam-real-response.json)과 [그 조회 출처](shared/evidence/steam-real-response-meta.json)는 12:47~12:50UTC의 앞선 기록이다. 양쪽 파서 및 실제 브라우저 UI에서 이를 재생한 검사도 있으며, 재생과 이후 실제 전송 검사를 구분한다. 브라우저의 응답 주입은 실제 네트워크 성공이 아니다.

Steam 원문은 관측된 정확한 CDN 호스트·원시 경로만 허용하고, 전체/항목 appid·양수 uint64 기사 ID·경로 ID와 gid 일치를 검사한다. 임의 호스트·다른 게임의 명시적 URL·userinfo/포트/query/fragment·인코딩/경로 이동을 거절한다. 손상된 응답과 실패는 마지막 정상 내용·성공 시각을 유지하며, 정상 빈 응답만 캐시를 비운다. 최대50건 검사·20건 보관, 응답256KiB·시간 상한을 둔다. Steam 본문은 원문 발췌이며 한국어 생성 요약이나 발화 실측이 아니다.

YouTube·치지직·카페는 연결 설정 필요, SOOP·디시·에펨코리아는 링크 저장/자동 연결 미확인으로 관리한다. [공식 문서 기능표](shared/evidence/provider-research.json)의 확인 범위를 따른다. URL 등록만으로 수집 완료를 주장하거나 확인되지 않은 소식을 만들지 않는다. 비밀키·세션·쿠키·브라우저 기록을 읽거나 동봉하지 않는다.

## 독립 검수와 남은 범위

Astra는 CORS 결함을 확인하기 전의 정확 소스27와 artifact9bef에 대해 **소스 통합 및 전달된 sampleDebug 프로토타입 범위에서 수용 가능**, 당시 확인된 필수 수정사항 없음으로 판정했다. 이후 확인한 브라우저 CORS 결함과 새 로컬 앱 서버는 이 이전 판정에 포함되지 않으며 새 검토가 필요하다. 여기에는 독립 APK 다운로드·서명·입력 대응과 빌더의24건 통과 기록 검토가 포함된다. 공개 API35 Java 컴파일·60개 모델 assertion, 실제 응답5건 및 양쪽 파서18개 부정 사례도 독립 확인했다. [브라우저37캡처·14동작 검사](validation/sources-v2/browser.txt)는 주야간·작은 화면·확대·등록/정렬/저장/재로드/마이그레이션/제한을 다루며 최소 대비5.95:1, 수평 넘침·잘림·page error가 없었다. [보고서](showcase/evidence/browser-report.json)의 소스·HTML 해시가 당시 검사 바이트를 고정한다.

이 판정은 최종 출품·release·실차 승인이 아니다. sampleDebug는 release 제출물이 아니다. 실제 기기/에뮬레이터 설치, 한국어 소리, 차량 포커스/제한 callback, 실제 Pleos SDK·ETA, 수정한 서버의 실제 브라우저 UI 네트워크/Android 실기 네트워크, 기기 간 동기화와 제출 영상은 남아 있다. 실제 ETA decoder는 단위·범위 확인 전 값을 거절하며 오류를 샘플 ETA로 대체하지 않는다. 일반 Android도 차량 상태 미확인이면 잠기며 상태 우회 기능은 없다.

공식1차 접수양식은 필수이고 영상/GitHub/APK는 전체 제출에서 선택 자료다. GitHub 제출을 선택할 경우 실행 가능한 release APK와 심사 접근 권한이 요구된다. 현재 sampleDebug 전달과 이를 구분하며 공식 안내에 없는 새 인증서 종류를 의무로 가정하지 않는다. main 병합·접수·공유 권한 변경은 수행하지 않았다.

[41개 입력 목록](validation/sources-v2/android-input-sha256.txt)과 [현재 브라우저 서버 검토본의 APK 입력 동일성](validation/local-app-server/apk-input-identity.json)을 함께 확인한다. 공개 API35 모델 검사는 저장소 루트에서 `JAVA_HOME=/path/to/jdk17 sh validation/sources-v2/verify-model.sh /path/to/android-all-api35.jar`로 재현한다. compile-only R 검사는 APK·기기 실행을 대신하지 않는다. 초기 기록은 [과거 통합](integration-notes.md)과 [초기 Astra 기록](review-notes.md)에 보존한다.


## 로컬 앱 실행과 검사

[실행 안내](README.md)를 따라 기본 `http://127.0.0.1:8765`를 사용한다. 출처·저장은 이 주소의 브라우저에서만 유지되며 파일 주소·다른 호스트명/포트와 자동 이관하지 않는다. 이전 저장소는 읽거나 삭제하지 않는다. 서버는 `--port`로 로컬 포트만 바꿀 수 있고 외부 주소 바인딩 옵션은 없다.

소스를 수정했다면 `cd showcase && npm ci && npm run build` 후 실행한다. `npm run verify:server`는 상류를 모형으로 대체한 endpoint/전송 오류 검사를, `npm run verify:local-app`은 실제 로컬 서버와 브라우저에서 캡처한 응답을 재생하는 검사를 수행한다. 실제 API 결과와 이 검사들을 혼동하지 않는다. 근거는 [서버 검사](validation/local-app-server/server-tests.txt)와 [로컬 앱 브라우저 검사](validation/local-app-server/local-app-browser-report.json)에 있다.


## 도착 후 저장한 소식을 먼저 표시

Astra가 실제6db 화면에서 저장 제목이390×1080의 y1502, 320×960의 y1741에 있어 첫 화면에서 읽을 수 없음을 확인했다. 좁은 도착 장면에서는 보관함을 차량 요약보다 먼저 표시하도록 DOM 순서만 바꿨다. 넓은 화면은 차량 왼쪽·보관함 오른쪽을 유지한다. masthead를 숨기거나 제품에 자동 스크롤을 넣지 않았다.

공식 LoL 항목을4분의 보류 목록에서 직접 저장한 뒤 도착을 누른 실제 브라우저 검사에서, 주야간 모두390×1080은 제목 하단 y706, 320×960은 y825로 첫 화면에 들어왔다(scrollY0). 원문 URL·공식 스냅샷 표시·재로드·삭제·빈 보관함·주행 제한/미확인 상태를 함께 확인했다. 자동 저장·링크 열기·음성을 추가하지 않았다. [측정과 검사](validation/arrival-first/arrival-browser-report.json) · [390px](validation/arrival-first/day-arrival-saved-390.png) · [320px](validation/arrival-first/day-arrival-saved-320.png).

`npm run verify:arrival`은 `EVIDENCE_DIR` 아래에 측정·화면을 남기며, PR 검증 workflow의 기존 로컬 앱 검사 다음에도 실행한다. 기존37캡처·14동작의 대비/넘침 검사도 통과했다. 이 도착 검사는 가져온 공식 항목의 UI 동작을 확인하며 새 실제 네트워크 요청 결과로 소개하지 않는다. 별도로6db 로컬 서버는 허용된 외부 환경에서 실제 Steam 사용자 갱신 HTTP200·5건·저장/재로드/중복 제거를 통과했다는 후속 근거가 있다. 이번 서버·전송 소스는 그6db와 같고, 최신 도착 배치의 네트워크 재검증 여부는 독립 검토 결과를 따른다.

동시에 추가된 CI 이력을 유지했고 도착 검사를 한 줄 추가했다. CI의 APK는 서명 없는 빌드 검사용이며 전달한 서명 APK를 대체하지 않는다. [입력 동일성](validation/arrival-first/unchanged-inputs.json)에 따라 Android/shared41개 입력은 여전히27과 같아9bef 서명 APK를 유지한다. 원래 저장 키·마이그레이션·출처/서버·Steam 전송은 변경하지 않았다.


## 소식 목록의 갱신 결과 표시

사용자가 같은 응답을 다시 확인했는지 알아볼 수 있도록 출처별 세션 결과를 추가했다. 이 출처의 소식 목록을 기준으로 첫5개는 ‘5개 추가됨’, 같은5개의 반복·중복 응답은 ‘추가된 소식 없음 · 5개 확인’, 이전에 없던 원문 URL1개가 들어오면 ‘1개 추가됨’으로 표시한다. 게시 날짜나 읽음 상태, 사용자가 저장한 보관함 개수를 세는 기능이 아니다. 정상 빈 응답은 ‘현재 소식 없음’, 실패는 이전 목록 유지 여부를 명시한다.

성공적으로 수용하고 같은 주소의 브라우저에 보관한 뒤에만 결과를 표시한다. 저장 공간 오류에는 성공 수를 알리지 않는다. 확인을 시작하면 이전 결과를 지우며, 출처 비활성/재활성·제한/미확인 전환 뒤 늦은 완료도 새 성공 결과로 표시하지 않는다. 결과는 `role=status/aria-live=polite`의 세션 UI에만 남고, 재로드 시 실제 목록·저장은 복구하되 세션 문구는 비워 둔다. 저장 schema·키·편성·순서는 변경하지 않았다.

[실제 브라우저7개 검사 그룹](validation/refresh-feedback/refresh-feedback-report.json)은11개 모형 응답으로 첫5개·반복/중복·추가1개·출처 구분·실패·빈 결과·저장 실패·늦은 응답·재로드를 확인했다. 320/390px 주야간에서 가로 넘침이 없고 결과 문구 대비는 주간7.17:1/야간8.81:1이었다. 기존 도착 첫 화면 검사와37캡처·14동작 검사도 통과했다. 이 검사 응답은 fixture이며 새 실제 네트워크 성공으로 소개하지 않는다.

`npm run verify:refresh-feedback`을 기존 PR workflow의 도착 검사 다음에 연결해 동일 후보 커밋에서 실행하고 `EVIDENCE_DIR`에 근거를 남긴다. [입력 동일성](validation/refresh-feedback/unchanged-inputs.json)에 따라 Android/shared41개와 로컬 서버·catalog는 그대로다. 검증된27/9bef 서명 APK와 CI의 unsigned 빌드용 APK를 구분한다.


## 최근 저장 삭제 한 건 되돌리기

도착 보관함에서 성공한 최근 삭제 한 건에만 ‘삭제 실행 취소’를 제공한다. 항목의 원문·출처·표시 내용을 세션에 보관하고, 되돌릴 때는 현재 목록에 항목 하나만 원래 이웃 위치로 복원한다. 그사이에 저장한 항목과 출처 순서·선택은 유지하며, 이미 같은 원문이 다시 보관돼 있으면 현재 사본을 그대로 두고 중복을 만들지 않는다.

삭제나 복원의 저장이 실패하면 성공 안내를 내지 않는다. 실패한 새 삭제가 직전 성공 삭제의 제안을 대체하지 않으며, 복원 실패는 제안을 남겨 재시도할 수 있다. 없어진 삭제 대상은 새 저장 작업을 만들지 않는다. 제안은 영구 저장하지 않고 재로드·주행 제한·상태 미확인에서 해제한다. 제한 중 오래된 버튼의 callback도 복원하지 못한다.

실제 Tab/Enter 삭제 후에는 실행 취소 버튼으로, 복원 후에는 해당 제목으로 초점을 이어 준다. [6개 브라우저 검사 그룹](validation/saved-undo/saved-undo-report.json)에서 원래 위치·정확한 원문/출처·그사이 변경·최근 한 건·실패/재시도·없어진/재저장된 대상·제한/재로드를 확인했다. 320/390px 주야간에서 가로 넘침 없이 버튼이 보였고 안내 대비는 주간17.44:1/야간15.25:1이었다. 기존 도착·갱신 결과 검사와37캡처·14동작 검사도 통과했다. 공식 스냅샷으로 검사했으며 실제 Steam을 다시 조회하지 않았다.

`npm run verify:saved-undo`를 기존 PR CI의 focused 검사 뒤에 연결했다. [입력 동일성](validation/saved-undo/unchanged-inputs.json)에 따라 Android/shared41개, catalog와 서버·전송은 그대로다. 저장 schema·키·공급자 범위를 바꾸지 않아27/9bef 서명 APK를 유지하며 CI의 unsigned 빌드 산출물과 구분한다.
