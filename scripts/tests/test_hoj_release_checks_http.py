import contextlib
import http.server
import json
import pathlib
import sys
import threading
import time
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from hoj_release_checks import ReleaseChecks, CONFIG_PATH, DIFFICULTY_PATH, RANK_PATH


@contextlib.contextmanager
def website(rank_first_delay=0, startup_failure=False):
    calls = []

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            calls.append(self.path)
            code = 200
            body = {'status': 200, 'data': {}}
            if self.path == CONFIG_PATH and startup_failure and calls.count(CONFIG_PATH) == 1:
                code = 503
            elif self.path == DIFFICULTY_PATH:
                body['data'] = []
            elif self.path == RANK_PATH:
                if calls.count(RANK_PATH) == 1:
                    time.sleep(rank_first_delay)
                body['data'] = {'total': 0, 'records': []}
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            try:
                self.wfile.write(json.dumps(body).encode())
            except ConnectionError:
                pass  # Deliberate client timeout in the diagnostic-retry test.

        def log_message(self, *args):
            pass

    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server.daemon_threads = True
    thread = threading.Thread(target=lambda: server.serve_forever(poll_interval=.01), daemon=True)
    thread.start()
    try:
        yield 'http://127.0.0.1:' + str(server.server_port), calls
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


class ReleaseHttpChecksTest(unittest.TestCase):
    def test_real_http_startup_failure_recovers_before_ranking(self):
        with website(startup_failure=True) as (url, calls):
            report = ReleaseChecks(url, emit=lambda row: None).run(
                ready_deadline=2, readiness_timeout=.5, request_timeout=.5,
                ranking_budget=.5, ready_interval=.01)
            self.assertTrue(report['canSwitchFrontend'])
            self.assertEqual([CONFIG_PATH] * 3, calls[:3])
            self.assertEqual([RANK_PATH] * 2, calls[-2:])

    def test_real_socket_timeout_retains_path_and_blocks_switch_even_after_recovery(self):
        with website(rank_first_delay=.15) as (url, calls):
            report = ReleaseChecks(url, emit=lambda row: None).run(
                ready_deadline=2, readiness_timeout=.5, request_timeout=.04,
                ranking_budget=.5, ready_interval=.01)
            self.assertTrue(report['functionalPassed'])
            self.assertFalse(report['performancePassed'])
            self.assertFalse(report['canSwitchFrontend'])
            first = report['rankingProbes'][0]
            self.assertEqual(RANK_PATH, first['path'])
            self.assertEqual('request_timeout', first['failure'])
            self.assertGreaterEqual(first['elapsedSeconds'], .03)
            self.assertEqual(2, calls.count(RANK_PATH))


if __name__ == '__main__':
    unittest.main()
