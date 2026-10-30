"""Run only against an isolated Compose demo stack; adds test data and a restore database.

Exercises real Java, Python, MySQL and Kafka. Does not delete the primary database.
"""
import http.cookiejar
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

COMPOSE = ['docker', 'compose', '-f', 'compose.yaml', '-f', 'compose.integrated.yaml']
BASE = 'http://127.0.0.1:8080'
results = []

def command(args, data=None, timeout=180):
    return subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          check=True, timeout=timeout).stdout

def compose(*args, data=None, timeout=180):
    return command(COMPOSE + list(args), data, timeout)

def sql(statement, database='trustflow'):
    return compose('exec', '-T', 'mysql', 'sh', '-c',
        'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --batch --skip-column-names "$1" -e "$2"',
        'sh', database, statement).decode().strip()

def wait_for(label, condition, seconds=120):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        try:
            if condition():
                results.append(label)
                print('PASS', label, flush=True)
                return
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            last = type(error).__name__
        time.sleep(1)
    raise AssertionError(f'{label} timed out ({last})')

class Client:
    def __init__(self, username):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.token = self.call('/api/csrf')[1]
        self.call('/login', 'POST', urllib.parse.urlencode({'username': username, 'password': 'TrustFlow123!'}).encode(), form=True)
        self.token = self.call('/api/csrf')[1]
    def call(self, path, method='GET', data=None, form=False):
        headers = {'Accept': 'application/json', 'X-Request-ID': 'integration-check'}
        if method != 'GET': headers[self.token['headerName']] = self.token['token']
        if data is not None and not form:
            data = json.dumps(data).encode(); headers['Content-Type'] = 'application/json'
        if form: headers['Content-Type'] = 'application/x-www-form-urlencoded'
        req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=10) as response:
                body = response.read(); return response.status, json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            return error.code, None
    def expense(self, title, amount=20, description=None):
        status, case = self.call('/api/cases', 'POST', {'type':'EXPENSE', 'title':title,
            'description': description or 'Taxi fare for a client meeting approved by my manager with receipt attached',
            'amount':amount})
        assert status == 201, status
        return case

def main():
    wait_for('Spring Boot and MySQL readiness', lambda: urllib.request.urlopen(BASE + '/actuator/health/readiness', timeout=3).status == 200, 240)
    assert sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1') == '3'
    results.append('Flyway V1/V2/V3 applied and Hibernate validation passed')
    employee, other, reviewer, admin = [Client(x) for x in ['employee','employee2','reviewer','admin']]
    low = employee.expense('Business travel expense')
    assert low['status'] == 'APPROVED', low
    high = employee.expense('Conference expense', 1200)
    assert high['status'] == 'PENDING_REVIEW'
    guarded = employee.expense('Business travel expense', description='Taxi fare for a client meeting not approved by my manager with no receipt attached')
    assert guarded['status'] == 'PENDING_REVIEW'
    assert 'text-guard' in guarded['escalationReason']
    assert other.call('/api/cases/' + str(low['id']))[0] == 403
    page = other.call('/api/cases/page?size=1&q=Business')[1]
    assert page['totalElements'] == 0
    assert employee.call('/api/cases/99999999')[0] == 404
    assert employee.call('/api/cases/page?size=101')[0] == 400
    assert employee.call('/api/admin/policies')[0] == 403
    results.append('Real-model routing, text guard, ownership and bounded paging')
    url = '/api/cases/' + str(high['id']) + '/review'
    assert reviewer.call(url, 'POST', {'version':99, 'decision':'REJECTED', 'reason':'Wrong version'})[0] == 409
    assert reviewer.call(url, 'POST', {'version':high['version'], 'decision':'REJECTED', 'reason':'Department budget is insufficient'})[0] == 200
    assert reviewer.call(url, 'POST', {'version':high['version'], 'decision':'APPROVED', 'reason':'Second decision must fail'})[0] == 409
    results.append('Human override and stale/final-review conflicts')
    policy = next(x for x in admin.call('/api/admin/policies')[1] if x['type'] == 'EXPENSE')
    new_policy = {'version':policy['version'], 'amountThreshold':10, 'minConfidence':.8, 'alwaysReview':False}
    assert admin.call('/api/admin/policies/' + str(policy['id']), 'PUT', new_policy)[0] == 204
    snapshot = employee.call('/api/cases/' + str(low['id']))[1]['record']
    assert snapshot['policyAmountThreshold'] == 500
    assert admin.call('/api/admin/policies/' + str(policy['id']) + '/history')[1]['totalElements'] == 1
    results.append('Policy change audit and unchanged historical case snapshot')
    wait_for('Initial outbox drained', lambda: sql('SELECT COUNT(*) FROM outbox_event WHERE published = 0') == '0')
    compose('stop', 'kafka')
    try:
        offline = employee.expense('Broker outage case', 1200)
        assert offline['status'] == 'PENDING_REVIEW'
        assert int(sql('SELECT COUNT(*) FROM outbox_event WHERE published = 0')) > 0
    finally:
        compose('start', 'kafka')
    wait_for('Kafka recovery drains persisted outbox', lambda: sql('SELECT COUNT(*) FROM outbox_event WHERE published = 0') == '0', 180)
    wait_for('Recovered notice is visible', lambda: any(n['caseId'] == offline['id'] for n in employee.call('/api/notifications')[1]))
    wait_for('All notification deliveries committed', lambda: sql('SELECT COUNT(*) FROM notification') == sql('SELECT COUNT(*) FROM outbox_event'))
    payload = sql('SELECT payload FROM outbox_event ORDER BY id LIMIT 1')
    before = sql('SELECT COUNT(*) FROM notification')
    compose('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'localhost:9092', '--topic', 'trustflow-notifications', data=(payload+'\n'+payload+'\n{invalid json}\n').encode())
    # The poison record follows duplicates in the same partition. Seeing it in DLT proves the earlier deliveries were consumed.
    dlt = compose('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-consumer.sh', '--bootstrap-server', 'localhost:9092', '--topic', 'trustflow-notifications.DLT', '--from-beginning', '--max-messages', '1', '--timeout-ms', '60000', timeout=90)
    assert b'{invalid json}' in dlt
    assert sql('SELECT COUNT(*) FROM notification') == before
    results.append('Duplicate Kafka delivery ignored and poison record routed to DLT')
    compose('stop', 'ai')
    try:
        unavailable = employee.expense('AI outage case')
        assert unavailable['status'] == 'PENDING_REVIEW' and unavailable['modelVersion'] == 'unavailable'
    finally:
        compose('start', 'ai')
    results.append('AI outage preserves manual-review case')
    wait_for('Final outbox drained', lambda: sql('SELECT COUNT(*) FROM outbox_event WHERE published = 0') == '0')
    case_count = sql('SELECT COUNT(*) FROM case_record')
    compose('restart', 'app')
    wait_for('App restart and database persistence', lambda: urllib.request.urlopen(BASE + '/actuator/health/readiness', timeout=3).status == 200)
    assert sql('SELECT COUNT(*) FROM case_record') == case_count
    dump = compose('exec', '-T', 'mysql', 'sh', '-c', 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces --set-gtid-purged=OFF trustflow')
    sql('CREATE DATABASE trustflow_restore')
    compose('exec', '-T', 'mysql', 'sh', '-c', 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" trustflow_restore', data=dump)
    for table in ['app_user','review_policy','case_record','review_record','audit_entry','notification','outbox_event','policy_change','email_delivery','flyway_schema_history']:
        assert sql('SELECT COUNT(*) FROM '+table) == sql('SELECT COUNT(*) FROM '+table, 'trustflow_restore'), table
    results.append('MySQL dump restored into a separate database with matching table counts')
    Path('test-results').mkdir(exist_ok=True)
    Path('test-results/trustflow-mysql-dump.sql').write_bytes(dump)
    Path('test-results/integration.json').write_text(json.dumps({'passed':results}, indent=2))
    print(json.dumps({'passed':results}, indent=2))

if __name__ == '__main__': main()
