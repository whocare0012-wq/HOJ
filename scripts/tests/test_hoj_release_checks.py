import io
import json
import pathlib
import socket
import ssl
import sys
import unittest
import tempfile
import contextlib
from unittest import mock
import urllib.error

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from hoj_release_checks import ReleaseChecks, CONFIG_PATH, DIFFICULTY_PATH, RANK_PATH, valid_rank, main


CONFIG = {'status': 200, 'data': {}}
DIFFICULTY = {'status': 200, 'data': []}
RANK = {'status': 200, 'data': {'total': 1, 'records': [
    {'uid': 'synthetic-user', 'rankPosition': 1, 'score': 10.0, 'ac': 1, 'total': 2}]}}


class Clock:
    def __init__(self):
        self.value = 0.0

    def now(self):
        return self.value

    def sleep(self, duration):
        self.value += duration


class Response:
    status = 200

    def __init__(self, body):
        self.body = body if isinstance(body, bytes) else json.dumps(body).encode()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def read(self, limit):
        return self.body[:limit]


class ReleaseChecksTest(unittest.TestCase):
    def checker(self, events):
        clock = Clock()
        calls, emitted = [], []
        events = iter(events)

        def transport(request, timeout):
            body, duration = next(events)
            calls.append((request.full_url, timeout))
            clock.sleep(duration)
            if isinstance(body, Exception):
                raise body
            return Response(body)

        checker = ReleaseChecks('http://127.0.0.1:12345', opener=transport,
                                clock=clock.now, sleeper=clock.sleep, emit=emitted.append)
        return checker, calls, emitted

    def test_ready_retries_upstream_startup_without_running_rank_early(self):
        unavailable = urllib.error.HTTPError('http://127.0.0.1', 503, '', {}, io.BytesIO())
        checker, calls, _ = self.checker([(unavailable, .1), (CONFIG, .1), (CONFIG, .1),
                                         (DIFFICULTY, .1), (RANK, .5), (RANK, .4)])
        report = checker.run()
        self.assertTrue(report['canSwitchFrontend'])
        self.assertEqual([CONFIG_PATH] * 3 + [DIFFICULTY_PATH, RANK_PATH, RANK_PATH],
                         [url.replace(checker.base_url, '') for url, _ in calls])

    def test_slow_but_valid_rank_is_performance_failure_not_readiness_failure(self):
        checker, _, emitted = self.checker([(CONFIG, .1), (CONFIG, .1), (DIFFICULTY, .1),
                                            (RANK, 12), (RANK, 1)])
        report = checker.run(request_timeout=30, ranking_budget=10)
        self.assertTrue(report['readinessPassed'])
        self.assertTrue(report['functionalPassed'])
        self.assertFalse(report['performancePassed'])
        self.assertFalse(report['canSwitchFrontend'])
        self.assertEqual(RANK_PATH, emitted[-2]['path'])
        self.assertEqual(12, emitted[-2]['elapsedSeconds'])

    def test_timeout_retry_is_bounded_and_cannot_hide_a_latency_failure(self):
        checker, calls, _ = self.checker([(CONFIG, .1), (CONFIG, .1), (DIFFICULTY, .1),
                                         (socket.timeout(), 30), (RANK, .5)])
        report = checker.run()
        self.assertEqual(5, len(calls))
        self.assertEqual('request_timeout', report['rankingProbes'][0]['failure'])
        self.assertTrue(report['functionalPassed'])
        self.assertFalse(report['canSwitchFrontend'])

    def test_repeat_request_timeout_also_gets_only_one_diagnostic_retry(self):
        checker, calls, _ = self.checker([(CONFIG, .1), (CONFIG, .1), (DIFFICULTY, .1),
                                         (RANK, .5), (socket.timeout(), 30), (RANK, .5)])
        report = checker.run()
        self.assertEqual(6, len(calls))
        self.assertTrue(report['functionalPassed'])
        self.assertFalse(report['performancePassed'])
        self.assertFalse(report['canSwitchFrontend'])

    def test_wrong_application_result_does_not_retry_or_pass_on_http_200(self):
        checker, calls, _ = self.checker([(CONFIG, .1), (CONFIG, .1), (DIFFICULTY, .1),
                                         ({'status': 500, 'msg': 'private-data'}, .1)])
        report = checker.run()
        self.assertFalse(report['functionalPassed'])
        self.assertEqual(4, len(calls))
        self.assertEqual('application_error', report['probes'][-1]['failure'])
        self.assertNotIn('private-data', json.dumps(report))

    def test_malformed_rank_is_a_functional_failure(self):
        checker, _, _ = self.checker([(CONFIG, .1), (CONFIG, .1), (DIFFICULTY, .1),
                                      ({'status': 200, 'data': {'total': 5, 'records': []}}, .1)])
        report = checker.run()
        self.assertFalse(report['canSwitchFrontend'])
        self.assertEqual('invalid_response_shape', report['probes'][-1]['failure'])

    def test_readiness_deadline_prevents_unbounded_retries(self):
        checker, calls, _ = self.checker([(socket.timeout(), 1), (socket.timeout(), 1)])
        report = checker.run(ready_deadline=2, readiness_timeout=1, ready_interval=1)
        self.assertFalse(report['readinessPassed'])
        self.assertEqual(1, len(calls))
        self.assertNotIn(RANK_PATH, json.dumps(calls))

    def test_late_response_does_not_pass_after_overall_readiness_deadline(self):
        checker, _, _ = self.checker([(CONFIG, .1), (CONFIG, 2)])
        report = checker.run(ready_deadline=2, ready_interval=.1)
        self.assertFalse(report['readinessPassed'])
        self.assertEqual('deadline_exceeded', report['readinessFailure'])

    def test_certificate_errors_are_not_bypassed_or_retried(self):
        checker, calls, _ = self.checker([(urllib.error.URLError(ssl.SSLError('certificate')), .1)])
        report = checker.run()
        self.assertFalse(report['canSwitchFrontend'])
        self.assertEqual('tls_error', report['probes'][0]['failure'])
        self.assertEqual(1, len(calls))

    def test_connection_reset_is_recorded_without_crashing_report_generation(self):
        checker, _, _ = self.checker([(ConnectionResetError(), .1), (CONFIG, .1), (CONFIG, .1),
                                      (DIFFICULTY, .1), (RANK, .1), (RANK, .1)])
        report = checker.run()
        self.assertTrue(report['canSwitchFrontend'])
        self.assertEqual('connection_error', report['probes'][0]['failure'])

    def test_empty_site_and_numeric_rank_fields(self):
        self.assertTrue(valid_rank({'data': {'total': 0, 'records': []}}))
        self.assertFalse(valid_rank({'data': {'total': True, 'records': []}}))
        invalid = json.loads(json.dumps(RANK))
        invalid['data']['records'][0]['rankPosition'] = 0
        self.assertFalse(valid_rank(invalid))

    def test_credentials_and_tokens_cannot_be_embedded_in_base_url(self):
        for url in ['file:///tmp/data', 'https://user:password@example.com',
                    'https://example.com?token=private', 'https://example.com/api']:
            with self.assertRaises(ValueError):
                ReleaseChecks(url)

    def test_cli_exit_codes_preserve_all_release_gates(self):
        for functional, performance, expected in [(True, True, 0), (True, False, 3), (False, False, 2)]:
            with self.subTest(expected=expected), tempfile.TemporaryDirectory() as directory:
                output = pathlib.Path(directory) / 'report.json'
                report = {'readinessPassed': True, 'functionalPassed': functional,
                          'performancePassed': performance, 'canSwitchFrontend': functional and performance}
                with mock.patch.object(ReleaseChecks, 'run', return_value=report), contextlib.redirect_stdout(io.StringIO()):
                    code = main(['--base-url', 'http://127.0.0.1:12345', '--output', str(output)])
                self.assertEqual(expected, code)
                self.assertEqual(report, json.loads(output.read_text()))


if __name__ == '__main__':
    unittest.main()
