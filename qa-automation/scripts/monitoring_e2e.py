"""Operational checks against the isolated attendance QA stack. No secret values in output."""
import argparse
import base64
import datetime as dt
import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
from attendance_workflow_e2e import BASE, sql
from hybrid_e2e import Api, user

ROOT = Path(__file__).resolve().parents[2]


def get(base, path, authorization=None):
    headers = {'Authorization': authorization} if authorization else {}
    request = urllib.request.Request(base + path, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def main(stack=False):
    assert sql('select current_database()') == 'attendance_qa'
    checks = []
    def check(name, condition):
        assert condition, name
        checks.append(name)
        print('PASS', name, flush=True)

    admin = Api(BASE).login('admin')
    roles = {role['name']: role['id'] for role in admin.call('GET', '/api/roles')}
    run = dt.datetime.now().strftime('%m%d%H%M%S%f')
    location = admin.call('POST', '/api/locations', dict(code='OPS_' + run, name='Monitoring QA',
        address='Isolated QA', timezone='Asia/Ho_Chi_Minh', latitude=10.776889, longitude=106.700806,
        attendanceRadiusMeters=200, wifiRequired=False, active=True))['id']
    position = admin.call('GET', '/api/positions')[0]['id']
    manager = user(admin, 'ops_m_' + run, 'Operations QA Manager', roles['ROLE_MANAGER'], location)
    employee = user(admin, 'ops_e_' + run, 'Operations QA Employee', roles['ROLE_EMPLOYEE'], location, position)
    manager_api, employee_api = admin.login(manager['username']), admin.login(employee['username'])
    status, content = get(BASE, '/actuator/health')
    health = json.loads(content)
    check('anonymous health reports UP without components or details', status == 200 and health['status'] == 'UP' and 'components' not in health and 'details' not in health)
    for probe in ['/livez', '/readyz', '/actuator/health/liveness', '/actuator/health/readiness']:
        check('public probe ' + probe, get(BASE, probe)[0] == 200)
    check('anonymous cannot list metrics', get(BASE, '/actuator/metrics')[0] == 401)
    check('anonymous cannot read application info', get(BASE, '/actuator/info')[0] == 401)
    check('manager cannot list system metrics', get(BASE, '/actuator/metrics', 'Bearer ' + manager_api.token)[0] == 403)
    check('employee cannot list system metrics', get(BASE, '/actuator/metrics', 'Bearer ' + employee_api.token)[0] == 403)
    status, content = get(BASE, '/actuator/metrics', 'Bearer ' + admin.token)
    check('admin can inspect JVM metrics', status == 200 and 'jvm.memory.used' in json.loads(content)['names'])
    status, content = get(BASE, '/actuator/health', 'Bearer ' + admin.token)
    check('admin health includes database component', status == 200 and 'db' in json.loads(content)['components'])
    check('frontend health endpoint responds directly', get('http://127.0.0.1:23000', '/healthz') == (200, 'ok'))
    check('anonymous cannot scrape Prometheus metrics', get(BASE, '/actuator/prometheus')[0] == 401)
    if stack:
        password = (ROOT / '.secrets/metrics_password').read_text().strip()
        basic = 'Basic ' + base64.b64encode(('prometheus:' + password).encode()).decode()
        status, content = get(BASE, '/actuator/prometheus', basic)
        check('scrape account reads application metrics', status == 200 and 'jvm_memory_used_bytes' in content and 'application="smart-shift"' in content)
        check('scrape account cannot read business API', get(BASE, '/api/users', basic)[0] == 401)
        wrong = 'Basic ' + base64.b64encode(b'prometheus:incorrect').decode()
        check('incorrect scrape credential is rejected', get(BASE, '/actuator/prometheus', wrong)[0] == 401)
        query = '/api/v1/query?' + urllib.parse.urlencode({'query': 'up{job="smart-shift"}'})
        deadline, samples = time.monotonic() + 45, []
        while time.monotonic() < deadline:
            status, content = get('http://127.0.0.1:29090', query)
            samples = json.loads(content).get('data', {}).get('result', [])
            if samples and samples[0]['value'][1] == '1': break
            time.sleep(2)
        check('Prometheus successfully scrapes the backend', bool(samples) and samples[0]['value'][1] == '1')
        status, content = get('http://127.0.0.1:29090', '/api/v1/rules')
        rules = json.loads(content)['data']['groups']
        check('all five alert rules are loaded', status == 200 and sum(len(g['rules']) for g in rules) == 5)
        status, content = get('http://127.0.0.1:23001', '/api/health')
        check('Grafana database is healthy', status == 200 and json.loads(content)['database'] == 'ok')
        password = (ROOT / '.secrets/grafana_admin_password').read_text().strip()
        basic = 'Basic ' + base64.b64encode(('admin:' + password).encode()).decode()
        status, content = get('http://127.0.0.1:23001', '/api/dashboards/uid/smartshift-operations', basic)
        check('Grafana dashboard is provisioned', status == 200 and len(json.loads(content)['dashboard']['panels']) == 8)
    output = ROOT / 'qa-automation/results/monitoring.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(dict(passed=len(checks), stack=stack, checks=checks), indent=2), encoding='utf-8')
    print('Completed', len(checks), 'checks:', output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--stack', action='store_true', help='Also validate local Prometheus and Grafana overlay')
    main(parser.parse_args().stack)
