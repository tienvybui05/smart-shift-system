"""Validate live documentation and real contracts in attendance_qa only. No token output."""
import datetime as dt
import json
from pathlib import Path
import urllib.error
import urllib.request
from attendance_workflow_e2e import BASE, sql
from hybrid_e2e import Api, user
from openapi_sync import validate

ROOT = Path(__file__).resolve().parents[2]


def request(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    data = None if body is None else json.dumps(body).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE + path, data=data, headers=headers, method=method), timeout=60) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


def main():
    assert sql('select current_database()') == 'attendance_qa'
    checks = []
    def check(name, condition):
        assert condition, name
        checks.append(name); print('PASS', name, flush=True)
    status, headers, content = request('GET', '/v3/api-docs')
    check('generated JSON documentation is public', status == 200)
    spec = json.loads(content); validate(spec)
    check('live document passes independent OpenAPI validation', True)
    operations = [(path, method, op) for path, item in spec['paths'].items()
        for method, op in item.items() if method in {'get','post','put','patch','delete'}]
    business = [(path, method, op) for path, method, op in operations if path.startswith('/api/')]
    check('all business operations have summaries tags and unique IDs', len(business) >= 100 and all(op.get('summary') and op.get('tags') for _,_,op in business) and len({op['operationId'] for _,_,op in business}) == len(business))
    check('all business operations declare HTTP roles', all('x-roles' in op for _,_,op in business))
    check('server is relative for same-origin Try it out', spec['servers'][0]['url'] == '/')
    check('YAML export is available', request('GET', '/v3/api-docs.yaml')[0] == 200)
    check('Swagger UI assets are available', request('GET', '/swagger-ui/index.html')[0] == 200)
    config = json.loads(request('GET', '/v3/api-docs/swagger-config')[2])
    check('Swagger does not persist JWT or send specs to external validator', not config.get('persistAuthorization') and not config.get('validatorUrl'))
    check('login is public in the contract', spec['paths']['/api/auth/login']['post'].get('security') == [])
    check('anonymous business requests remain unauthorized', request('GET', '/api/users')[0] == 401)
    status, _, body = request('POST', '/api/auth/login', dict(username='', password=''))
    error = json.loads(body)
    check('validation returns documented 400 and fieldErrors', status == 400 and error['status'] == 400 and 'username' in error['fieldErrors'] and 'password' in error['fieldErrors'])
    check('incorrect credentials return documented 401', request('POST', '/api/auth/login', dict(username='admin', password='incorrect-password'))[0] == 401)
    admin = Api(BASE).login('admin')
    check('JWT authenticates protected current-user endpoint', admin.call('GET', '/api/auth/me')['username'] == 'admin')
    run = dt.datetime.now().strftime('%m%d%H%M%S%f')
    location_body = dict(code='DOC_' + run, name='Swagger contract QA', address='Isolated docs fixture',
        timezone='Asia/Ho_Chi_Minh', latitude=10.776889, longitude=106.700806,
        attendanceRadiusMeters=200, wifiRequired=False, active=True)
    status, _, body = request('POST', '/api/locations', location_body, admin.token)
    location = json.loads(body)['id']
    check('create operation returns documented 201', status == 201 and '201' in spec['paths']['/api/locations']['post']['responses'])
    roles = {r['name']:r['id'] for r in admin.call('GET','/api/roles')}
    position = admin.call('GET','/api/positions')[0]['id']
    manager = user(admin, 'doc_m_' + run, 'Swagger QA Manager', roles['ROLE_MANAGER'], location)
    employee = user(admin, 'doc_e_' + run, 'Swagger QA Employee', roles['ROLE_EMPLOYEE'], location, position)
    manager_api, employee_api = admin.login(manager['username']), admin.login(employee['username'])
    check('documented Manager read access succeeds', request('GET','/api/users',token=manager_api.token)[0] == 200 and 'MANAGER' in spec['paths']['/api/users']['get']['x-roles'])
    check('Employee denial matches documented 403', request('GET','/api/users',token=employee_api.token)[0] == 403 and 'EMPLOYEE' not in spec['paths']['/api/users']['get']['x-roles'])
    check('Manager cannot call Admin-only create', request('POST','/api/locations',location_body,manager_api.token)[0] == 403 and spec['paths']['/api/locations']['post']['x-roles'] == ['ADMIN'])
    status, _, _ = request('PATCH','/api/users/me/password',dict(currentPassword='Demo@123456',newPassword='SwaggerQa@123456',confirmPassword='SwaggerQa@123456'),employee_api.token)
    check('change password returns documented empty 204', status == 204 and '204' in spec['paths']['/api/users/me/password']['patch']['responses'])
    check('pagination limits are enforced as documented', request('GET','/api/users?page=-1',token=admin.token)[0] == 400)
    today = dt.datetime.now(dt.timezone.utc).date().isoformat()
    status, headers, body = request('GET', f'/api/attendances/export?startDate={today}&endDate={today}&locationId={location}', token=admin.token)
    mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    check('XLSX binary response matches content type and schema', status == 200 and headers['Content-Type'] == mime and body.startswith(b'PK') and spec['paths']['/api/attendances/export']['get']['responses']['200']['content'][mime]['schema']['format'] == 'binary')
    check('SSE documents streaming content type', 'text/event-stream' in spec['paths']['/api/notifications/stream']['get']['responses']['200']['content'])
    password = spec['components']['schemas']['ChangePasswordRequest']['properties']
    check('all password fields are write-only password format', all(password[key].get('writeOnly') and password[key].get('format') == 'password' for key in ['currentPassword','newPassword','confirmPassword']))
    output = ROOT / 'qa-automation/results/openapi.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(dict(passed=len(checks), operations=len(operations), businessOperations=len(business), schemas=len(spec['components']['schemas']), checks=checks), indent=2), encoding='utf-8')
    print('Completed',len(checks),'checks:',output)


if __name__ == '__main__': main()
