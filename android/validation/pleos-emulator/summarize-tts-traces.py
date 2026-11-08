#!/usr/bin/env python3
"""Offline, text-free summary of one fixture-v1 queue per input file."""
import argparse
import json
import re
from pathlib import Path

PROXIES = {
    'baseline': 'MediaPlayer_start_return_proxy_not_verified_audible_onset',
    'candidate': 'sdk_onStart_proxy',
}
FAILURES = {
    'stop_failed_no_next_topic', 'candidate_unavailable_or_error_no_fallback',
    'initialization_or_start_timeout', 'candidate_initialization_unsupported_no_fallback',
    'baseline_initialization_unavailable_no_offline_korean_voice',
}


def summarize(lines):
    result = {'status': 'incomplete', 'arm': None, 'engine': 'unknown',
              'first_request_to_start_proxy_ms': None,
              'skip_to_next_start_proxy_ms': [], 'issues': []}
    issues = result['issues']
    arm = None
    last = None
    fixtures = 0
    first = None
    first_complete = False
    pending = None
    request = None
    pids = set()
    failed = False
    invalid = False

    def issue(code):
        if code not in issues:
            issues.append(code)

    for line in lines:
        if 'TtsComparison' not in line:
            continue
        # Ignore every free-form field; never copy input text into output.
        match = re.search(r'elapsedRealtimeMs=(\d+) engine=(baseline|candidate)(?:\s|$)', line)
        if not match:
            invalid = True
            issue('malformed_trace_record')
            continue
        pid = re.search(r'^\s*\d+\.\d+\s+(\d+)\s+\d+\s+[VDIWEF]\s+TtsComparison', line)
        if pid:
            pids.add(pid[1])
        stamp, current_arm = int(match[1]), match[2]
        if arm is not None and current_arm != arm:
            invalid = True
            issue('mixed_arms')
        arm = arm or current_arm
        if last is not None and stamp < last:
            invalid = True
            issue('non_monotonic_or_mixed_run')
        last = stamp
        fields = match.string[match.end():].split()
        tokens = set(fields)
        values = dict(field.split('=', 1) for field in fields if '=' in field)
        if values.get('fixture') == 'v1':
            fixtures += 1
            if fixtures > 1:
                invalid = True
                issue('multiple_fixture_runs')
        if 'error' in values or tokens.intersection(FAILURES):
            failed = True
            issue('failure_record')
        if current_arm == 'candidate':
            result['engine'] = 'Pleos_ON_DEVICE_identity_unverified'
        elif 'baseline_engine' in values:
            # Package syntax only, never voice names or transcripts.
            package = values['baseline_engine']
            if re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', package) and len(package) <= 200:
                result['engine'] = package
        topic = values.get('topic')
        invalid_target = False
        if topic is not None and topic not in {'0', '1', '2'}:
            invalid = True
            invalid_target = True
            issue('invalid_fixture_topic')
        if ('next' in values or 'skip_command' in tokens) and values.get('next') not in {'0', '1', '2'}:
            invalid = True
            invalid_target = True
            issue('invalid_skip_target')
        if invalid_target:
            # Untrusted targets must never reach pairing or integer conversion.
            continue
        if 'pause_or_focus_stop_command' in tokens:
            invalid = True
            issue('interrupted_run_not_comparable')
        if 'skip_command' in tokens:
            if pending is not None:
                issue('skip_without_start_proxy')
            pending = (stamp, values.get('next'))
            request = None
        if 'start_request' in tokens:
            if request is not None:
                invalid = True
                issue('overlapping_or_repeated_request')
            request = (stamp, topic)
            if first is None:
                first = request
                if topic != '0' or pending is not None:
                    invalid = True
                    issue('missing_initial_topic_request')
        if PROXIES[current_arm] in tokens:
            if request is None or request[1] != topic:
                invalid = True
                issue('unpaired_start_proxy')
                continue
            if not first_complete and request == first:
                result['first_request_to_start_proxy_ms'] = stamp - first[0]
                first_complete = True
            if pending is not None:
                if pending[1] != topic or topic is None:
                    invalid = True
                    issue('skip_target_mismatch')
                else:
                    result['skip_to_next_start_proxy_ms'].append(
                        {'topic': int(topic), 'interval_ms': stamp - pending[0]})
                pending = None
            request = None
    if len(pids) > 1:
        invalid = True
        issue('multiple_processes')
    if fixtures != 1:
        issue('missing_single_fixture_boundary')
    if not first_complete:
        issue('missing_first_start_proxy')
    if pending is not None or request is not None:
        issue('unfinished_request_or_skip')
    if not result['skip_to_next_start_proxy_ms']:
        issue('missing_skip_start_proxy')
    result['arm'] = arm
    result['status'] = 'invalid' if invalid else 'failure' if failed else 'incomplete' if issues else 'complete'
    if invalid:
        # Ambiguous attribution must never produce apparently comparable numbers.
        result['first_request_to_start_proxy_ms'] = None
        result['skip_to_next_start_proxy_ms'] = []
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('traces', nargs='+', type=Path)
    args = parser.parse_args()
    reports = []
    for number, path in enumerate(args.traces, 1):
        try:
            with path.open(encoding='utf-8', errors='strict') as stream:
                report = summarize(stream)
        except (OSError, UnicodeError):
            report = {'status': 'incomplete', 'issues': ['unreadable_input']}
        # Ordinals avoid leaking personal input paths.
        reports.append({'input': number, **report})
    print(json.dumps({'measurement': 'monotonic software proxy intervals; not audible timing',
                      'reports': reports}, indent=2))
    return 0 if all(r['status'] == 'complete' for r in reports) else 1


if __name__ == '__main__':
    raise SystemExit(main())
