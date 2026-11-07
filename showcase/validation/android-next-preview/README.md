# Android 다음 미확인 제목 미리보기

검증 대상 SHA: `894821f148f83b0bced2ff5130fb2d005a11aa77` (이번 문서 수정 전 HEAD와 일치).
[GitHub CI 실행 42339420095](https://github.com/jhuni17/before-arrival-pleos/actions/runs/42339420095)의 산출물 `before-arrival-894821f148f83b0bced2ff5130fb2d005a11aa77`을 기준으로 한다. 아래 경로는 압축 해제한 산출물 루트 기준이다. 이번 변경은 이 README만 갱신하며 제품 코드와 workflow는 변경하지 않는다.

## CI 증거와 재검증

로컬에 보관된 해당 실행 산출물을 읽기 전용으로 대조했다.

| 증거 경로 | 확인 결과 |
| --- | --- |
| `ci-evidence/source-sha.txt` | 대상 SHA 및 후보 HEAD 일치 |
| `ci-evidence/android-input-sha256.txt` | 추적 Android/shared 입력 60개 모두 현재 파일 해시 일치. 60은 manifest 항목 수이며 제품 한도가 아님 |
| `ci-evidence/apk-sha256.txt`, `ci-evidence/app-sample-debug-unsigned.apk` | SHA-256 `460481e81cfd1aa82f2a2bbcb6147f4e0028a32ee74aecf8b8fbdfa278a21860` |
| `ci-evidence/showcase-html-sha256.txt` | 현재 `showcase/before-arrival-showcase.html` SHA-256 `6494883588a565ef6f25308e499983d2cbf4508e16c7e9d7b9613b60827f7bea`와 일치 |
| `android/app/build/test-results/testSampleDebugUnitTest/TEST-com.beforearrival.briefing.*.xml` | CatalogTest 10개, MainActivityTest 34개, SpeechPlaybackTest 7개, 합계 51개. 각각 실패·오류·건너뜀 0개 |
| `android/app/build/reports/lint-results-sampleDebug.xml` 및 `.html` | 경고 7개, 오류·Fatal 0개 |
| `ci-evidence/android.log` | sample 단위 테스트·lint·unsigned sample APK 빌드 성공 |
| `ci-evidence/showcase.log`, `ci-evidence/browser/browser-report.json` | showcase 회귀 PASS: 37개 캡처, 행동 검사 14개, 최소 대비 5.33:1 |

`ci-evidence/browser/saved-item-next-report.json`, `saved-position-report.json`, `review-next-report.json`, `review-undo-report.json`은 브라우저 상세 이동·저장 위치·확인 후 다음·실행 취소 증거다. 이 보고서들의 `workingTreeDirty: true`와 `browser-report.json`의 `provenance.workingTreeDirtyAtRun: true`를 그대로 인정한다. `sourceSha256`이 있는 항목의 현재 소스 해시는 일치하지만, 열거되지 않은 파일을 포함한 전체 작업 트리 검증이나 clean 상태 증거는 아니다. `local-app-browser-report.json`의 서버 검사는 캡처 응답을 fetcher에 주입한 offline replay이며 실제 upstream 네트워크 실행이 아니다. 다른 fixture/replay 검사도 실제 네트워크와 구분한다.

저장소 루트에서 `ARTIFACT_ROOT`를 해당 실행 산출물의 압축 해제 경로로 지정하고 실행한다. 다운로드·빌드·파일 쓰기 없이 원본 증거를 재검증한다.

```sh
ARTIFACT_ROOT=/absolute/path/to/extracted-artifact python3 - <<'PY'
import hashlib, json, os, subprocess
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(os.environ['ARTIFACT_ROOT'])
evidence = root / 'ci-evidence'
sha = '894821f148f83b0bced2ff5130fb2d005a11aa77'
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert (evidence / 'source-sha.txt').read_text().strip() == sha
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() == sha
rows = (evidence / 'android-input-sha256.txt').read_text().splitlines()
assert len(rows) == 60
for row in rows:
    expected, name = row.split(maxsplit=1)
    assert digest(Path(name)) == expected, name
for manifest, path, expected in [
    ('apk-sha256.txt', evidence / 'app-sample-debug-unsigned.apk',
     '460481e81cfd1aa82f2a2bbcb6147f4e0028a32ee74aecf8b8fbdfa278a21860'),
    ('showcase-html-sha256.txt', Path('showcase/before-arrival-showcase.html'),
     '6494883588a565ef6f25308e499983d2cbf4508e16c7e9d7b9613b60827f7bea')]:
    assert (evidence / manifest).read_text().split()[0] == digest(path) == expected
for name, count in [('CatalogTest', 10), ('MainActivityTest', 34), ('SpeechPlaybackTest', 7)]:
    p = root / ('android/app/build/test-results/testSampleDebugUnitTest/'
                'TEST-com.beforearrival.briefing.' + name + '.xml')
    suite = ET.parse(p).getroot()
    assert int(suite.get('tests')) == count
    assert all(int(suite.get(k)) == 0 for k in ('failures', 'errors', 'skipped'))
    print(name, count, 'failures/errors/skipped=0')
issues = ET.parse(root / 'android/app/build/reports/lint-results-sampleDebug.xml').getroot().findall('issue')
assert sum(i.get('severity') == 'Warning' for i in issues) == 7
assert not any(i.get('severity') in ('Error', 'Fatal') for i in issues)
checked = 0
def check_sources(value):
    global checked
    if isinstance(value, dict):
        for key, child in value.items():
            if key == 'sourceSha256':
                for name, expected in child.items():
                    assert digest(Path('showcase') / name) == expected, name
                    checked += 1
            else:
                check_sources(child)
    elif isinstance(value, list):
        for child in value:
            check_sources(child)
for report in (evidence / 'browser').rglob('*.json'):
    check_sources(json.loads(report.read_text()))
assert checked > 0
print('PASS: source/HEAD, 60 inputs, APK, HTML, lint 7/0, browser source hashes', checked)
PY
```

## 수용 기준 1~5의 코드·테스트 근거

아래 링크는 추적된 실제 소스와 테스트다. 테스트 통과 수는 위 CI XML에서 확인했으며, 속성 검사와 기기 검증의 범위를 구분한다.

| 기준 | 구현 및 테스트 근거 |
| --- | --- |
| 1. 전체 제목 표시 | [MainActivity.java:683](../../../android/app/src/main/java/com/beforearrival/briefing/MainActivity.java#L683)의 `nextUnread(item)` 결과를 TextView로 표시하고 `setSingleLine(false)` 적용. [MainActivityTest.java:714](../../../android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java#L714) `nextUnreadPreviewPreservesFullLongTitle`; 같은 파일 1132행의 `assertEquals(Integer.MAX_VALUE, preview.getMaxLines())` 등은 전체 텍스트·줄 수·말줄임 속성 검사다. 실제 Android 화면 렌더링이나 접근성 서비스 실행 증거가 아니다 |
| 2. 빈 후보와 클릭 시 재조회 | [MainActivity.java:729](../../../android/app/src/main/java/com/beforearrival/briefing/MainActivity.java#L729)에서 후보 재조회, 732행에서 `preview.setText("")` 및 숨김. [MainActivityTest.java:727](../../../android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java#L727) `nextUnreadRechecksConfirmationAndDeletionAndNeverReopensSelf`가 확인/삭제 변경, 자기 자신 제외와 후보 소진을 검사 |
| 3. 저장 순서·순환·부작용 방지 | [MainActivity.java:648](../../../android/app/src/main/java/com/beforearrival/briefing/MainActivity.java#L648)의 `nextUnread`; [MainActivityTest.java:671](../../../android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java#L671) `nextUnreadUsesSavedOrderWrapsAndLeavesDataUntouched`가 ALL/UNREAD/CONFIRMED, 저장 순서·순환, 편성·저장 데이터·외부 실행·음성 부작용을 검사. fixture의 items.size()는 제품 한도가 아님 |
| 4. 확인 저장·실패 재시도·실행 취소 유지 | [MainActivity.java:712](../../../android/app/src/main/java/com/beforearrival/briefing/MainActivity.java#L712)의 확인 후 다음 저장과 실패 시 return; [MainActivityTest.java:805](../../../android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java#L805) `confirmNextUsesSavedOrderWrapsAndFinishesInBothFilters`, 847행 `confirmNextRechecksCurrentAndCandidatesAndRetriesFailedStorage`. 같은 파일 914행 `undoConfirmationUpdatesEveryFilterWithoutChangingPlanOrSavedOrder`, 994행 `undoConfirmationStorageFailuresKeepTargetAndRetryPersists`가 실행 취소와 저장 실패 재시도를 별도로 검사 |
| 5. 주행 제한·lifecycle 콜백 무효화 | [AccessPolicy.java:8](../../../android/app/src/main/java/com/beforearrival/briefing/AccessPolicy.java#L8) `return resumed && state == State.PARKED_USE_ALLOWED;`, [MainActivity.java:727](../../../android/app/src/main/java/com/beforearrival/briefing/MainActivity.java#L727)의 접근·generation·현재 dialog 검사. [MainActivityTest.java:777](../../../android/app/src/test/java/com/beforearrival/briefing/MainActivityTest.java#L777) `nextUnreadAndConfirmStayStaleAfterRestrictionsPauseAndRecreation`, 1074행 `undoConfirmationNeverReturnsAfterRestrictionsPauseOrRecreation`가 제한·상태 불명·pause·재생성 이후 이전 콜백과 음성 정지를 검사 |

## 이전 로컬 기록과 남은 공백

이전 기록의 기준 SHA는 `5b4bc8329044d3cdf427e8fbc38d851da6889c87`이며 당시 변경은 미커밋 working tree였다. 당시 `sh android/gradlew -p android --offline --no-daemon --console=plain :app:testSampleDebugUnitTest --tests '*MainActivityTest.nextUnread*'`는 테스트 전에 `JAVA_HOME is not set and no 'java' command could be found in your PATH`로 차단됐다. `java -version`도 command not found였다. 당시 `git diff --check`는 PASS였으며 새 CI 결과·APK·캡처는 없었다. 이전 기준의 50개 통과 제공 정보와 이번 후보 CI의 51개 통과는 별개다.

이번 문서 작업에서는 산출물 읽기 전용 대조와 `git diff --check`를 수행했다. 로컬 Android 테스트 재실행 및 수정 전 실패 재현은 여전히 미검증이다. 전체 회귀는 GitHub CI에 맡기며 브라우저도 로컬 재실행하지 않았다.

실제 Android 화면·접근성 서비스, APK 설치, 에뮬레이터 실행, 실제 Pleos SDK 콜백 및 제출 준비 완료는 미검증이다. unsigned sample APK 빌드는 설치 또는 제출 준비 완료 증거가 아니다. `android/`는 주차 상태 차량용 프로토타입이고 `showcase/`는 샘플 경로·기기 간 연동을 연출하는 시연 자료이며 실제 Pleos 실행과 구분한다. 주행 제한 또는 상태 불명 시 상호작용·음성을 중지하는 정책의 기기 검증 공백도 유지한다.
