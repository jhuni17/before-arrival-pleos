#!/usr/bin/env python3
"""Synthetic offline checks; no device or speech evidence."""
import importlib.util
from pathlib import Path
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


if __name__ == '__main__':
    unittest.main()
