#!/usr/bin/env python3
"""Synthetic offline checks; no device or speech evidence."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('summary', Path(__file__).with_name('summarize-tts-traces.py'))
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


def trace(arm):
    proxy = summary.PROXIES[arm]
    records = [(10, 'topic=0 fixture=v1 selected_in_debug_build_not_live_content'),
               (15, 'topic=0 baseline_engine=org.example.offline voice=PRIVATE_TEXT'),
               (20, 'topic=0 start_request'), (50, 'topic=0 ' + proxy),
               (100, 'topic=0 skip_command next=1'),
               (110, 'topic=1 start_request'), (150, 'topic=1 ' + proxy)]
    return [f'1234.000 42 43 D TtsComparison: elapsedRealtimeMs={t} engine={arm} {event}\n'
            for t, event in records]


class SummaryTests(unittest.TestCase):
    def test_two_arms(self):
        for arm in summary.PROXIES:
            with self.subTest(arm=arm):
                result = summary.summarize(trace(arm))
                self.assertEqual(result['status'], 'complete')
                self.assertEqual(result['arm'], arm)
                self.assertEqual(result['first_request_to_start_proxy_ms'], 30)
                self.assertEqual(result['skip_to_next_start_proxy_ms'], [{'topic': 1, 'interval_ms': 50}])
                self.assertNotIn('PRIVATE_TEXT', str(result))
                self.assertNotEqual(result['engine'], 'unknown')

    def test_missing_and_error(self):
        self.assertEqual(summary.summarize([])['status'], 'incomplete')
        self.assertEqual(summary.summarize(trace('candidate')[:-1])['status'], 'incomplete')
        lines = trace('candidate') + ['TtsComparison: elapsedRealtimeMs=160 engine=candidate stage=speak error=PRIVATE_TEXT']
        result = summary.summarize(lines)
        self.assertEqual(result['status'], 'failure')
        self.assertNotIn('PRIVATE_TEXT', str(result))

    def test_mixed_runs(self):
        cases = [trace('baseline') + trace('candidate'),
                 trace('candidate') + trace('candidate'),
                 trace('candidate') + ['TtsComparison: elapsedRealtimeMs=160 engine=candidate topic=0 fixture=v1'],
                 [line.replace(' 42 43 ', ' 99 43 ') if i == 6 else line
                  for i, line in enumerate(trace('candidate'))]]
        for lines in cases:
            result = summary.summarize(lines)
            self.assertEqual(result['status'], 'invalid')
            self.assertIsNone(result['first_request_to_start_proxy_ms'])
            self.assertEqual(result['skip_to_next_start_proxy_ms'], [])

    def test_unpaired_interrupted_and_malformed(self):
        for extra in ['topic=1 sdk_onStart_proxy', 'topic=1 pause_or_focus_stop_command', 'broken']:
            line = ('TtsComparison: broken' if extra == 'broken' else
                    'TtsComparison: elapsedRealtimeMs=160 engine=candidate ' + extra)
            self.assertEqual(summary.summarize(trace('candidate') + [line])['status'], 'invalid')

    def test_missing_first_fixture_mixed_run_cli(self):
        for arm in summary.PROXIES:
            # Same PID and arm, strictly increasing monotonic time; only the
            # earlier queue's fixture marker is missing.
            later = [line.replace(f'elapsedRealtimeMs={stamp}',
                                  f'elapsedRealtimeMs={stamp + 200}')
                     for line, stamp in zip(trace(arm), [10, 15, 20, 50, 100, 110, 150])]
            for lines in [trace(arm)[1:], trace(arm)[1:] + later]:
                with self.subTest(arm=arm, mixed=len(lines) > len(trace(arm))):
                    with tempfile.TemporaryDirectory() as directory:
                        path = Path(directory) / 'synthetic.log'
                        path.write_text(''.join(lines), encoding='utf-8')
                        completed = subprocess.run(
                            [sys.executable, str(Path(summary.__file__)), str(path)],
                            capture_output=True, text=True, check=False)
                    self.assertEqual(completed.returncode, 1)
                    self.assertEqual(completed.stderr, '')
                    self.assertNotIn('PRIVATE_TEXT', completed.stdout)
                    report = json.loads(completed.stdout)['reports'][0]
                    self.assertEqual(report['status'], 'invalid')
                    self.assertIn('measurement_before_fixture_boundary', report['issues'])
                    self.assertIsNone(report['first_request_to_start_proxy_ms'])
                    self.assertEqual(report['skip_to_next_start_proxy_ms'], [])

    def test_invalid_targets_cli(self):
        for arm in summary.PROXIES:
            for value in ['PRIVATE_TEXT', '-1', '3', '01', '']:
                for target in ['topic', 'next', 'both']:
                    with self.subTest(arm=arm, value=value, target=target):
                        lines = trace(arm)
                        if target in {'next', 'both'}:
                            lines = [line.replace('next=1', 'next=' + value) for line in lines]
                        if target in {'topic', 'both'}:
                            lines = [line.replace('topic=1', 'topic=' + value) for line in lines]
                        with tempfile.TemporaryDirectory() as directory:
                            path = Path(directory) / 'synthetic.log'
                            path.write_text(''.join(lines), encoding='utf-8')
                            completed = subprocess.run(
                                [sys.executable, str(Path(summary.__file__)), str(path)],
                                capture_output=True, text=True, check=False)
                        self.assertEqual(completed.returncode, 1)
                        self.assertEqual(completed.stderr, '')
                        self.assertNotIn('PRIVATE_TEXT', completed.stdout)
                        report = json.loads(completed.stdout)['reports'][0]
                        self.assertEqual(report['status'], 'invalid')
                        self.assertIsNone(report['first_request_to_start_proxy_ms'])
                        self.assertEqual(report['skip_to_next_start_proxy_ms'], [])


class ComparisonTests(unittest.TestCase):
    def run_cli(self, captures, compare=True):
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for number, lines in enumerate(captures):
                path = Path(directory) / f'PRIVATE_TEXT-{number}.log'
                path.write_text(''.join(lines), encoding='utf-8')
                paths.append(str(path))
            result = subprocess.run(
                [sys.executable, summary.__file__] + (['--compare'] if compare else []) + paths,
                capture_output=True, text=True, check=False)
        self.assertEqual(result.stderr, '')
        self.assertNotIn('PRIVATE_TEXT', result.stdout)
        return result.returncode, json.loads(result.stdout)

    def test_opt_in_and_direction(self):
        baseline = trace('baseline')
        # First callback is 10 ms earlier, next callback is 20 ms later.
        candidate = [line.replace('elapsedRealtimeMs=50 ', 'elapsedRealtimeMs=40 ')
                     .replace('elapsedRealtimeMs=150 ', 'elapsedRealtimeMs=170 ')
                     for line in trace('candidate')]
        for captures in ([baseline, candidate], [candidate, baseline]):
            code, output = self.run_cli(captures)
            self.assertEqual(code, 0)
            comparison = output['comparison']
            self.assertEqual(comparison['status'], 'complete')
            self.assertEqual(comparison['first_request_to_start_proxy_ms'],
                             {'baseline_ms': 30, 'candidate_ms': 20,
                              'candidate_minus_baseline_ms': -10})
            self.assertEqual(comparison['skip_to_next_start_proxy_ms'],
                             [{'topic': 1, 'baseline_ms': 50, 'candidate_ms': 70,
                               'candidate_minus_baseline_ms': 20}])
        code, output = self.run_cli([baseline, candidate], compare=False)
        self.assertEqual(code, 0)
        self.assertEqual(output, {
            'measurement': 'monotonic software proxy intervals; not audible timing',
            'reports': [{'input': i, **summary.summarize(lines)}
                        for i, lines in enumerate([baseline, candidate], 1)]})

    def test_refused_pairs(self):
        baseline, candidate = trace('baseline'), trace('candidate')
        failure = candidate + ['TtsComparison: elapsedRealtimeMs=180 engine=candidate error=PRIVATE_TEXT']
        mismatch = [line.replace('next=1', 'next=2').replace('topic=1', 'topic=2')
                    for line in candidate]
        for captures, issue in [
                ([baseline], 'requires_exactly_two_inputs'),
                ([baseline, candidate, candidate], 'requires_exactly_two_inputs'),
                ([baseline, baseline], 'requires_one_input_per_arm'),
                ([candidate, candidate], 'requires_one_input_per_arm'),
                ([baseline, candidate[:-1]], 'requires_complete_inputs'),
                ([baseline, []], 'requires_complete_inputs'),
                ([baseline, failure], 'requires_complete_inputs'),
                ([baseline, candidate + candidate], 'requires_complete_inputs'),
                ([baseline, mismatch], 'skip_topic_sequence_mismatch')]:
            with self.subTest(issue=issue, count=len(captures)):
                code, output = self.run_cli(captures)
                self.assertEqual(code, 1)
                self.assertEqual(output['comparison'], {'status': 'refused', 'issues': [issue]})

    def test_multiple_skips_and_zero_delta(self):
        reports = []
        for arm in summary.PROXIES:
            lines = trace(arm) + [
                f'TtsComparison: elapsedRealtimeMs=200 engine={arm} topic=1 skip_command next=2',
                f'TtsComparison: elapsedRealtimeMs=210 engine={arm} topic=2 start_request',
                f'TtsComparison: elapsedRealtimeMs=260 engine={arm} topic=2 {summary.PROXIES[arm]}']
            reports.append(summary.summarize(lines))
        result = summary.compare(reports)
        self.assertEqual([item['topic'] for item in result['skip_to_next_start_proxy_ms']], [1, 2])
        self.assertTrue(all(item['candidate_minus_baseline_ms'] == 0
                            for item in result['skip_to_next_start_proxy_ms']))


if __name__ == '__main__':
    unittest.main()
