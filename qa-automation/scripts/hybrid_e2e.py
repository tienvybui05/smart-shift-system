"""Synthetic REST fixtures for compose.test.yml; standard library only, no deletes."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import json
import os
from pathlib import Path
import random
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'qa-automation/results/hybrid-e2e.json'
ZONE = dt.timezone(dt.timedelta(hours=7))
PASSWORD = os.environ.get('E2E_PASSWORD', 'Demo@123456')


class Api:
    def __init__(self, base, token=None):
        self.base, self.token = base.rstrip('/'), token

    def call(self, method, path, data=None, expected=None):
        headers = {'Content-Type': 'application/json'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        body = None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(self.base + path, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=150) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        result = json.loads(raw) if raw else None
        if expected is not None:
            assert status == expected, f'{method} {path}: expected {expected}, got {status}: {result}'
        elif not 200 <= status < 300:
            raise RuntimeError(f'{method} {path}: HTTP {status}: {result}')
        return result

    def login(self, username, password=PASSWORD):
        result = self.call('POST', '/api/auth/login', {'username': username, 'password': password})
        return Api(self.base, result['accessToken'])


def save(state):
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT.with_suffix('.tmp')
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary.replace(OUTPUT)


def instant(date):
    return dt.datetime.combine(date, dt.time(), ZONE).isoformat()


def user(admin, username, name, role, location, position=None, part=False, rate=30000):
    result = admin.call('POST', '/api/users', dict(
        username=username, password=PASSWORD, fullName=name, email=username + '@example.test',
        roleId=role, locationId=location, positionId=position,
        employmentType='PART_TIME' if part else 'FULL_TIME', hireDate=str(dt.datetime.now(ZONE).date()),
        minHoursPerWeek=16 if part else 32, maxHoursPerWeek=24 if part else 40,
        maxHoursPerDay=8, minRestHours=11, maxConsecutiveDays=5,
        basePayAmount=rate, salaryCoefficient=1, active=True))
    return {key: result[key] for key in ('id', 'username', 'positionId')}


def requirements(admin, shift, positions, same=True):
    admin.call('PUT', f'/api/shift-requirements/work-shifts/{shift}', dict(
        requirements=[dict(positionId=p, minEmployees=1, maxEmployees=1, priority=i+1)
                      for i, p in enumerate(positions)],
        applyToSameTemplate=same, changeReason='Synthetic E2E fixture'))


def generate_shifts(admin, period, templates, start, end):
    return admin.call('POST', '/api/work-shifts/generate', dict(
        schedulePeriodId=period, shiftTemplateIds=templates, startDate=str(start), endDate=str(end)))['workShifts']


def period(admin, location, name, start, end):
    return admin.call('POST', '/api/schedule-periods', dict(
        locationId=location, name=name, startDate=str(start), endDate=str(end),
        changeReason='Synthetic E2E fixture'))['id']


def seed_case(admin, roles, positions, run_id, start, kind, count, days, budget):
    short = {'balanced': 'bal', 'shortage': 'short', 'night': 'night'}[kind]
    label = 'E2E - ' + kind
    chosen = positions[:2] if kind == 'night' else positions
    location = admin.call('POST', '/api/locations', dict(
        code=f'E2E_{short}_{run_id}', name=label, address='Synthetic fixture, not a real store',
        timezone='Asia/Ho_Chi_Minh', latitude=10.776889, longitude=106.700806,
        attendanceRadiusMeters=200, weeklyBudget=budget, wifiSsid='SMART_SHIFT_E2E',
        allowedIpAddress='127.0.0.1', wifiRequired=True, active=True))['id']
    manager = user(admin, f'e2e_{run_id}_{short}_mgr', label + ' Manager', roles['ROLE_MANAGER'], location)
    rng = random.Random(42)
    employees = [user(admin, f'e2e_{run_id}_{short}_{i+1:02d}', f'{label} NV {i+1:02d}',
                      roles['ROLE_EMPLOYEE'], location, chosen[i % len(chosen)],
                      part=(i % 4 == 3 and kind != 'night'), rate=rng.choice([25000, 30000, 35000]))
                 for i in range(count)]

    def availability(item):
        i, employee = item
        client = admin.login(employee['username'])
        first = start - dt.timedelta(days=1) if kind == 'night' else start
        for offset in range((start + dt.timedelta(days=days) - first).days + 1):
            date = first + dt.timedelta(days=offset)
            unavailable = kind == 'balanced' and i == count-1 and date == start + dt.timedelta(days=3)
            client.call('POST', '/api/employee-availabilities/me', dict(
                availableDate=str(date), startTime='00:00', endTime='23:59',
                availabilityType='UNAVAILABLE' if unavailable else ('PREFERRED' if i % 3 == 0 else 'AVAILABLE'),
                note='Synthetic fixture: includes next day for overnight availability'))

    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(availability, enumerate(employees)))
    times = [('06:00', '14:00'), ('14:00', '22:00'), ('22:00', '06:00')]
    if kind == 'night':
        times = [('06:00', '14:00'), ('22:00', '06:00')]
    templates = [admin.call('POST', '/api/shift-templates', dict(
        locationId=location, name=f'E2E {a}-{b}', startTime=a, endTime=b,
        breakMinutes=30, colorCode=['#3182CE', '#DD6B20', '#6B46C1'][i], active=True))
        for i, (a, b) in enumerate(times)]
    end = start + dt.timedelta(days=days-1)
    period_id = period(admin, location, label + ' ' + str(start), start, end)
    shifts = generate_shifts(admin, period_id, [t['id'] for t in templates], start, end)
    for template in templates:
        shift = next(s for s in shifts if s['shiftTemplateId'] == template['id'])
        requirements(admin, shift['id'], chosen)
    case = dict(kind=kind, locationId=location, periodId=period_id, start=str(start), end=str(end),
                employees=employees, manager=manager, shiftIds=[s['id'] for s in shifts], status='SEEDED')
    if kind == 'balanced':
        shift = min(shifts, key=lambda s: s['startAt'])
        assigned = admin.call('POST', f"/api/shift-assignments/work-shifts/{shift['id']}",
                              dict(userId=employees[0]['id'], note='Keep this manual assignment fixed'))
        case.update(fixedShiftId=shift['id'], fixedAssignmentId=assigned['assignments'][0]['id'])
        day = start + dt.timedelta(days=2)
        leave = admin.login(employees[1]['username']).call('POST', '/api/time-off-requests/me', dict(
            startAt=instant(day), endAt=instant(day+dt.timedelta(days=1)), leaveType='ANNUAL', reason='E2E leave'))
        admin.call('PATCH', f"/api/time-off-requests/{leave['id']}/review", dict(status='APPROVED'))
        case['approvedLeaveId'] = leave['id']
    elif kind == 'night':
        day = start-dt.timedelta(days=1)
        prior = period(admin, location, 'E2E previous Sunday boundary', day, day)
        template = next(t for t in templates if t['startTime'].startswith('22:00'))
        shift = generate_shifts(admin, prior, [template['id']], day, day)[0]
        requirements(admin, shift['id'], [employees[0]['positionId']], False)
        assigned = admin.call('POST', f"/api/shift-assignments/work-shifts/{shift['id']}", dict(
            userId=employees[0]['id'], note='Previous-week night constrains Monday'))
        case.update(boundaryShiftId=shift['id'], boundaryAssignmentId=assigned['assignments'][0]['id'])
        admin.call('POST', f'/api/schedule-periods/{prior}/publish')
    return case


def check_case(admin, case):
    if case['status'] == 'PASSED':
        print(case['kind'] + ': already passed; data retained', flush=True)
        return
    pid = case['periodId']
    current = admin.call('GET', f'/api/schedule-periods/{pid}')
    published = current['status'] == 'PUBLISHED'
    response = case.get('generation') or admin.call('POST', '/api/schedules/generate', dict(schedulePeriodId=pid))
    assert response, 'Published period has no saved generation result; use --new-run'
    assert response['optimization']['algorithm'] == 'ALNS_CP_SAT'
    assert response['assignmentsCreated'] > 0
    assert len(response['assignments']) == response['assignmentsCreated']
    case['generation'] = response
    assert (response['unfilledPositions'] > 0) if case['kind'] == 'shortage' else (response['unfilledPositions'] == 0)
    if not published:
        repeated = admin.call('POST', '/api/schedules/generate', dict(schedulePeriodId=pid))
        assert repeated['assignmentsCreated'] == 0, 'Generation must be idempotent'
        case['repeatAssignmentsCreated'] = 0
    else:
        admin.call('POST', '/api/schedules/generate', dict(schedulePeriodId=pid), expected=400)
    if case.get('fixedAssignmentId'):
        summary = admin.call('GET', f"/api/shift-assignments/work-shifts/{case['fixedShiftId']}")
        fixed = next(a for a in summary['assignments'] if a['id'] == case['fixedAssignmentId'])
        assert fixed['assignmentSource'] == 'MANUAL' and fixed['userId'] == case['employees'][0]['id']
    check = admin.call('GET', f'/api/schedule-periods/{pid}/publication-check')
    case['publicationCheck'] = check
    assert check['invalidAssignments'] == 0, check
    if case['kind'] == 'shortage':
        assert not check['canPublish']
        admin.call('POST', f'/api/schedule-periods/{pid}/publish', expected=400)
    else:
        if not published:
            assert check['canPublish'], check
            assert admin.call('POST', f'/api/schedule-periods/{pid}/publish')['status'] == 'PUBLISHED'
        client = admin.login(case['employees'][0]['username'])
        schedule = client.call('GET', '/api/shift-assignments/me?' + urllib.parse.urlencode(dict(
            startDate=case['start'], endDate=case['end'])))
        # An overnight shift from the previous period can overlap the requested date range.
        schedule = [s for s in schedule if s['schedulePeriodId'] == pid]
        assert schedule and all(s['schedulePeriodStatus'] == 'PUBLISHED' for s in schedule)
        notifications = client.call('GET', '/api/notifications?size=100')
        assert any(n['type'] == 'SCHEDULE_PUBLISHED' for n in notifications['content'])
        case['employeeVisibleShifts'] = len(schedule)
    case['status'] = 'PASSED'
    print(f"{case['kind']}: {response['assignmentsCreated']} new / {response['unfilledPositions']} missing / "
          f"{response['optimization']['elapsedMillis']} ms solver: PASS", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:18080')
    parser.add_argument('--new-run', action='store_true', help='Create another fixture without deleting old data')
    parser.add_argument('--seed-only', action='store_true')
    args = parser.parse_args()
    url = urllib.parse.urlparse(args.base_url)
    if url.hostname not in {'localhost', '127.0.0.1'} or url.port not in {18080, 13000}:
        parser.error('Only isolated localhost test ports 18080/13000 are allowed')
    admin = Api(args.base_url).login('admin', os.environ.get('E2E_ADMIN_PASSWORD', 'Demo@123456'))
    if OUTPUT.exists() and not args.new_run:
        state = json.loads(OUTPUT.read_text(encoding='utf-8'))
        assert state['baseUrl'] == args.base_url, 'Manifest belongs to another API'
    else:
        today = dt.datetime.now(ZONE).date()
        start = today + dt.timedelta(days=7-today.weekday())
        state = dict(runId=dt.datetime.now(ZONE).strftime('%m%d%H%M%S'), baseUrl=args.base_url,
                     startDate=str(start), synthetic=True, seed=42, cases=[])
        save(state)
    roles = {r['name']: r['id'] for r in admin.call('GET', '/api/roles')}
    references = admin.call('GET', '/api/positions')
    positions = [next(p['id'] for p in references if p['code'] == code)
                 for code in ('CASHIER', 'SERVICE_STAFF', 'KITCHEN_STAFF')]
    for kind, count, days, budget in [('balanced', 24, 14, 20000000), ('shortage', 6, 7, 1000000), ('night', 8, 7, 10000000)]:
        if any(c['kind'] == kind for c in state['cases']):
            continue
        print(f'Seeding {kind}: {count} employees, {days} days', flush=True)
        case = seed_case(admin, roles, positions, state['runId'], dt.date.fromisoformat(state['startDate']),
                         kind, count, days, budget)
        state['cases'].append(case)
        save(state)
    if not args.seed_only:
        for case in state['cases']:
            try:
                check_case(admin, case)
            finally:
                save(state)
        balanced, shortage = state['cases'][:2]
        manager = admin.login(balanced['manager']['username'])
        manager.call('GET', f"/api/work-shifts?schedulePeriodId={balanced['periodId']}")
        manager.call('GET', f"/api/work-shifts?schedulePeriodId={shortage['periodId']}", expected=403)
        admin.login(balanced['employees'][0]['username']).call('POST', '/api/schedules/generate',
                                                             dict(schedulePeriodId=balanced['periodId']), expected=403)
        state.update(authorizationChecks='PASSED', completedAt=dt.datetime.now(ZONE).isoformat())
        save(state)
    print('Manifest/results: ' + str(OUTPUT), flush=True)


if __name__ == '__main__':
    main()
