"""Read-only HOJ readiness, functional and ranking-latency gates.

Run after starting the backend, before switching the frontend index. This module
never uploads artifacts, changes configuration, restarts services or rolls back.
Keep the deployment's existing backup and protected-container checks as well.
"""
import argparse
import datetime
import http.client
import json
import math
import pathlib
import socket
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request

CONFIG_PATH = '/api/get-website-config'
DIFFICULTY_PATH = '/api/problem-difficulties'
RANK_PATH = '/api/get-rank-list?type=1&limit=5&currentPage=1'
MAX_BODY_BYTES = 4 * 1024 * 1024


def valid_rank(body):
    data = body.get('data')
    if not isinstance(data, dict) or not isinstance(data.get('records'), list):
        return False
    if len(data['records']) > 5 or type(data.get('total')) is not int or data['total'] < 0:
        return False
    if len(data['records']) > data['total'] or (data['total'] > 0 and not data['records']):
        return False
    for row in data['records']:
        if not isinstance(row, dict) or not isinstance(row.get('uid'), str) or not row['uid']:
            return False
        for key in ('rankPosition', 'ac', 'total'):
            value = row.get(key)
            if type(value) is not int or value < (1 if key == 'rankPosition' else 0):
                return False
        score = row.get('score')
        if isinstance(score, bool) or not isinstance(score, (int, float)) or not math.isfinite(score):
            return False
    return data['total'] != 0 or not data['records']


class ReleaseChecks:
    def __init__(self, base_url, opener=None, clock=None, sleeper=None, emit=None):
        parsed = urllib.parse.urlsplit(base_url)
        if (parsed.scheme not in ('http', 'https') or not parsed.hostname
                or parsed.username or parsed.password or parsed.query or parsed.fragment
                or parsed.path not in ('', '/')):
            raise ValueError('Use an HTTP(S) origin without credentials, path or query')
        self.base_url = base_url.rstrip('/')
        self.opener = opener or urllib.request.urlopen
        self.clock = clock or time.monotonic
        self.sleep = sleeper or time.sleep
        self.emit = emit or (lambda event: print(json.dumps(event), flush=True))
        self.probes = []
        self.readiness_failure = None

    def probe(self, name, path, timeout, validator):
        started = self.clock()
        result = {'name': name, 'path': path, 'timeoutSeconds': timeout,
                  'passed': False, 'retryable': False}
        request = urllib.request.Request(self.base_url + path, headers={'Cache-Control': 'no-cache'})
        try:
            with self.opener(request, timeout=timeout) as response:
                result['httpStatus'] = response.status
                raw = response.read(MAX_BODY_BYTES + 1)
            if len(raw) > MAX_BODY_BYTES:
                result['failure'] = 'response_too_large'
            elif result['httpStatus'] != 200:
                result['failure'] = 'http_error'
                result['retryable'] = result['httpStatus'] in (429, 502, 503, 504)
            else:
                body = json.loads(raw)
                if not isinstance(body, dict) or body.get('status') != 200:
                    result['failure'] = 'application_error'
                    if isinstance(body, dict):
                        status = body.get('status')
                        if type(status) is int:
                            result['applicationStatus'] = status
                elif not validator(body):
                    result['failure'] = 'invalid_response_shape'
                else:
                    result['passed'] = True
        except urllib.error.HTTPError as error:
            result.update(httpStatus=error.code, failure='http_error',
                          retryable=error.code in (429, 502, 503, 504))
            error.close()
        except (socket.timeout, TimeoutError):
            result.update(failure='request_timeout', retryable=True)
        except urllib.error.URLError as error:
            if isinstance(error.reason, (socket.timeout, TimeoutError)):
                result.update(failure='request_timeout', retryable=True)
            elif isinstance(error.reason, ssl.SSLError):
                result['failure'] = 'tls_error'
            else:
                result.update(failure='connection_error', retryable=True)
        except ssl.SSLError:
            result['failure'] = 'tls_error'
        except http.client.IncompleteRead:
            result.update(failure='incomplete_response', retryable=True)
        except OSError:
            result.update(failure='connection_error', retryable=True)
        except (ValueError, UnicodeError):
            result['failure'] = 'invalid_json'
        result['elapsedSeconds'] = round(self.clock() - started, 6)
        self.probes.append(result)
        # Never include response bodies, student data, tokens, or sensitive headers.
        self.emit(dict(result))
        return result

    def wait_ready(self, deadline_seconds, request_timeout, interval):
        deadline = self.clock() + deadline_seconds
        consecutive = 0
        while self.clock() < deadline:
            remaining = deadline - self.clock()
            if remaining <= 0:
                break
            probe = self.probe('readiness', CONFIG_PATH,
                               min(request_timeout, remaining),
                               lambda body: isinstance(body.get('data'), dict))
            if self.clock() > deadline:
                self.readiness_failure = 'deadline_exceeded'
                return False
            consecutive = consecutive + 1 if probe['passed'] else 0
            if consecutive >= 2:
                return True
            if not probe['passed'] and not probe['retryable']:
                self.readiness_failure = probe['failure']
                return False
            remaining = deadline - self.clock()
            if remaining <= 0:
                break
            self.sleep(min(interval, remaining))
        self.readiness_failure = 'deadline_exceeded'
        return False

    def run(self, ready_deadline=210, readiness_timeout=5, request_timeout=30,
            ranking_budget=10, ready_interval=2):
        for value in (ready_deadline, readiness_timeout, request_timeout, ranking_budget, ready_interval):
            if not math.isfinite(value) or value <= 0:
                raise ValueError('Timeouts, interval and budget must be positive finite numbers')
        self.probes = []
        self.readiness_failure = None
        report = {'schemaVersion': 1, 'baseUrl': self.base_url,
                  'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  'readinessPassed': False, 'functionalPassed': False,
                  'performancePassed': False, 'rankingBudgetSeconds': ranking_budget,
                  'canSwitchFrontend': False}
        report['readinessPassed'] = self.wait_ready(ready_deadline, readiness_timeout, ready_interval)
        report['readinessFailure'] = self.readiness_failure
        if report['readinessPassed']:
            difficulty = self.probe('difficulty', DIFFICULTY_PATH, request_timeout,
                                    lambda body: isinstance(body.get('data'), list))
            ranks = []
            if difficulty['passed']:
                first = self.probe('ranking_first', RANK_PATH, request_timeout, valid_rank)
                ranks.append(first)
                if first['passed']:
                    ranks.append(self.probe('ranking_repeat', RANK_PATH, request_timeout, valid_rank))
                elif first['retryable']:
                    # One bounded diagnostic retry. A timeout still fails the latency gate.
                    self.sleep(1)
                    ranks.append(self.probe('ranking_retry', RANK_PATH, request_timeout, valid_rank))
                if first['passed'] and not ranks[-1]['passed'] and ranks[-1]['retryable']:
                    self.sleep(1)
                    ranks.append(self.probe('ranking_retry', RANK_PATH, request_timeout, valid_rank))
            report['functionalPassed'] = difficulty['passed'] and bool(ranks) and ranks[-1]['passed']
            report['performancePassed'] = bool(ranks) and all(
                probe['passed'] and probe['elapsedSeconds'] <= ranking_budget for probe in ranks)
            report['rankingProbes'] = ranks
        report['canSwitchFrontend'] = (report['readinessPassed'] and report['functionalPassed']
                                       and report['performancePassed'])
        report['probes'] = list(self.probes)
        report['completedAt'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        return report


def positive(value):
    number = float(value)
    if not math.isfinite(number) or number <= 0:
        raise argparse.ArgumentTypeError('must be a positive finite number')
    return number


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True, help='New diagnostic JSON file')
    parser.add_argument('--ready-deadline', type=positive, default=210)
    parser.add_argument('--readiness-timeout', type=positive, default=5)
    parser.add_argument('--request-timeout', type=positive, default=30)
    parser.add_argument('--ranking-budget', type=positive, default=10)
    args = parser.parse_args(argv)
    if args.output.exists():
        parser.error('Output file already exists; preserve the prior diagnostic record')
    checks = ReleaseChecks(args.base_url)
    report = checks.run(ready_deadline=args.ready_deadline, readiness_timeout=args.readiness_timeout,
                        request_timeout=args.request_timeout, ranking_budget=args.ranking_budget)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2)
        stream.write('\n')
    print(json.dumps({'canSwitchFrontend': report['canSwitchFrontend'],
                      'readinessPassed': report['readinessPassed'],
                      'functionalPassed': report['functionalPassed'],
                      'performancePassed': report['performancePassed']}), flush=True)
    if report['canSwitchFrontend']:
        return 0
    return 3 if report['functionalPassed'] else 2


if __name__ == '__main__':
    raise SystemExit(main())
