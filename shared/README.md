# 공통 출처·콘텐츠 계약

`catalog.json`은 브라우저 빌드와 Android assets가 같은 바이트를 읽는 기본 카탈로그다. Android는 `app`의 assets sourceSet에 `../../shared`를 추가한다. 개별 기기에 저장한 설정을 공유하거나 서버에 보내는 파일이 아니다.

- `providers`: 등록 가능한 공식 HTTPS 주소 패턴, 표시 이름, 출처 종류, 예시 주소. URL 자격증명·임의 호스트·추적/토큰 쿼리는 저장하지 않는다. 디시 등록의 공개 게시판 `id` 쿼리만 허용한다.
- `sources`: 안정 ID, 이름, 제공자, 종류, 등록 URL, 사용 여부, 상태, 항목 목록. 배열 순서가 사용자 순서다.
- `items`: `id/sourceId/title/publishedAt/publishedPrecision/originalUrl/summary/estimatedSeconds/deadlineAt/provenance/fetchedAt`. 시간 미제공은 null, 날짜만 확인된 메이플 소식은 YYYY-MM-DD 그대로이며 시간을 만들지 않는다. ETA는 초, 듣기 예산은 max45초이며 도착3분 여유를 뺀 나머지의20%다. 10분 이내 마감 항목을 먼저 둔 뒤 출처 순서와 예산으로 고른다.
- `snapshots`: 공식 페이지에서 직접 확인해 가져온 기사3개. 앱의 네트워크 갱신 결과가 아니다. 출처를 등록하면 해당 공식 디렉터리와 일치하는 snapshot을 연결한다. 게시 시각·원문·확인 시각을 유지한다.

로컬 v2 저장 형식은 `schemaVersion:2/sources/savedItems`이며 Android SharedPreferences `catalog_v2`, 브라우저 localStorage `before-arrival-sources-v2`에 각각 남는다. 구형3출처 선택·우선순위·저장을 한 번 마이그레이션한다. 출처 항목 ID/원문 URL로 중복을 제거하고 실패 때 기존 항목과 마지막 성공 시각을 유지한다. 정상 빈 응답은 빈 목록으로 바꾼다. 예시 항목으로 실패를 채우지 않는다.

출처 상태는 예시, 가져온 공식 스냅샷, 실제 갱신 성공, 설정 필요, 링크 저장으로 구분한다. Steam public GetNewsForApp v2만 실제 실행 경로가 구현됐다. 고정 HTTPS 목적지·공개 appID·응답256KiB·최대50검사/20보관·5~6초 제한·리다이렉트/쿠키/자격증명 금지를 적용한다. 공식 feed 후보 이름의 실제 지원과 성공 응답은 이 환경의403 차단 때문에 검증하지 못했다. 추출한 본문은 원문 발췌이며 한국어 생성 요약이나 실제 발화 실측으로 소개하지 않는다.

`evidence/`는 공식 페이지 확인3건과 문서별 기능/제약의 근거다. 연구 도구가 읽은 공식 페이지와 앱 런타임 성공을 구분한다. YouTube·치지직·카페의 필요한 연결 설정, SOOP·게시판의 확인되지 않은 자동 연결 범위는 UI 상태로 안내한다. 세션·쿠키·브라우저 기록·비밀키를 읽거나 동봉하지 않는다.
