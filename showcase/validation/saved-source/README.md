출처 필터·조건 모두 지우기 검증 자료

source-report.json은 기준 HEAD와 workingTreeDirty, 소스 5개·생성 HTML·주야간 320/390 캡처 8개의 SHA-256을 기록한다. 커밋하지 않은 작업 트리 검증이므로 새 후보 커밋 SHA는 없다.

고정 데이터: 저장 출처 3개, 그중 2개는 동일한 긴 이름. 출처·제목(빈 문자열/공백/일치/불일치)·미확인의 모든 조합에서 표시 건수와 순서를 확인한다. 조건 모두 지우기 후 전체 출처·빈 검색어·전체 확인 상태, 검색창 초점, 저장 객체 전체 불변을 검증한다. Enter/Space 실행, 주야간 320/390의 대비 4.5 이상·16px 이상·가로 넘침 없음, restricted/unknown에서 오래된 초기화 핸들러 차단도 검사한다.

기존 출처별 결과, 삭제/확인 실행 취소, 새로고침 초기화, 안전 전환과 재생 중지 검사를 유지한다. 기존 CI 필수 명령 verify:saved-undo가 이 검사를 호출하며 CI 설정은 변경하지 않았다. 캡처는 출처 선택 결과와 빈 결과에서 초기화 버튼을 보여 준다. 새 초기화 문구는 Asta Sans로 표시된다. 기존 일부 버튼의 한글이 이 환경에서 네모로 렌더링되는 현상은 남아 있다. 물리적 한국어 IME 검증은 포함하지 않는다.

브라우저 샘플 검증이며 Android APK 빌드·설치·에뮬레이터·실제 Pleos SDK 콜백·제출 준비 완료의 증거가 아니다. 같은 디렉터리의 기존 .txt/.log는 이전 출처 필터 작업의 기록이며 이번 실행 결과는 source-report.json을 기준으로 한다.

이번 실행: build, verify, verify:server(30개), saved-source-browser-verify, review-undo-browser-verify, 소스/HTML/캡처 해시 재대조, git diff --check 통과. 첫 모바일 검사에서 초기화 버튼 글자 크기 기준 미달을 발견해 16px/Asta Sans 상속으로 수정한 뒤 재검사를 통과했다. verify:saved-undo 묶음 및 verify:arrival → verify:browser 묶음은 완료 메시지 없이 종료 코드 143으로 중단되어 전체 회귀 통과로 간주하지 않는다. Android 검사는 실행하지 않았다.
