"""Split-shift/clopening regression through real APIs in compose.attendance-test.yml only."""
import datetime as dt
import json
from pathlib import Path
from attendance_workflow_e2e import BASE, sql, raw
from hybrid_e2e import Api, user, period

OUTPUT = Path(__file__).resolve().parents[1] / 'results/split-shift.json'
ZONE = dt.timezone(dt.timedelta(hours=7))


def main():
    assert sql('select current_database()') == 'attendance_qa', 'Refuse another database'
    admin = Api(BASE).login('admin')
    run = dt.datetime.now().strftime('%m%d%H%M%S%f')
    roles = {r['name']: r['id'] for r in admin.call('GET', '/api/roles')}
    position = admin.call('GET', '/api/positions')[0]['id']
    location = admin.call('POST', '/api/locations', dict(
        code='SS_' + run, name='Split shift QA ' + run, address='Isolated fixture',
        timezone='Asia/Ho_Chi_Minh', latitude=10.776889, longitude=106.700806,
        attendanceRadiusMeters=200, wifiRequired=False, active=True))['id']
    manager = user(admin, 'ss_m_' + run, 'Split QA manager', roles['ROLE_MANAGER'], location)
    employees = [user(admin, f'ss_e{i}_' + run, f'Split QA employee {i}',
                      roles['ROLE_EMPLOYEE'], location, position) for i in range(3)]
    clients = [admin.login(e['username']) for e in employees]
    mgr = admin.login(manager['username'])
    today = dt.datetime.now(ZONE).date()
    monday = today + dt.timedelta(days=7 - today.weekday())
    checks, fixtures = [], []

    def check(label, condition, detail=''):
        assert condition, f'{label}: {detail}'
        checks.append(label)
        print('PASS', label, flush=True)

    def fixture(name, week, definitions, available=(0,)):
        day = monday + dt.timedelta(weeks=week)
        pid = period(admin, location, name + ' ' + run, day, day + dt.timedelta(days=2))
        for person in available:
            for offset in range(3):
                clients[person].call('POST', '/api/employee-availabilities/me', dict(
                    availableDate=str(day + dt.timedelta(days=offset)), startTime='00:00', endTime='23:59',
                    availabilityType='AVAILABLE', note='Split/clopening QA'))
        shifts = []
        for offset, clock, hours, minimum in definitions:
            start = dt.datetime.combine(day + dt.timedelta(days=offset), dt.time.fromisoformat(clock), ZONE)
            end = start + dt.timedelta(hours=hours)
            shift = int(sql(f"""
                with s as (
                  insert into work_shifts(schedule_period_id,start_at,end_at,break_minutes,status)
                  values({pid},'{start.isoformat()}','{end.isoformat()}',0,'OPEN') returning id
                ), r as (
                  insert into shift_requirements(work_shift_id,position_id,min_employees,max_employees,priority)
                  select id,{position},{minimum},1,1 from s
                ) select id from s;
            """))
            shifts.append(shift)
        fixtures.append(dict(name=name, periodId=pid, shiftIds=shifts))
        return pid, shifts

    def assign(shift, person=0):
        result = mgr.call('POST', f'/api/shift-assignments/work-shifts/{shift}',
                          dict(userId=employees[person]['id'], note='Split/clopening QA'))
        return next(a for a in result['assignments'] if a['userId'] == employees[person]['id'])['id']

    def owner(shift):
        return mgr.call('GET', f'/api/shift-assignments/work-shifts/{shift}')['assignments'][0]['userId']

    def generate(pid):
        return mgr.call('POST', '/api/schedules/generate', dict(schedulePeriodId=pid))

    def publish(pid):
        return mgr.call('POST', f'/api/schedule-periods/{pid}/publish')

    def request(giver, assignment, target=None):
        return clients[giver].call('POST', '/api/shift-swap-requests', dict(
            requesterAssignmentId=assignment, targetAssignmentId=target, reason='Split/clopening QA'))['id']

    def accept(person, rid):
        return clients[person].call('PATCH', f'/api/shift-swap-requests/me/{rid}/respond', dict(status='ACCEPTED'))

    def approve(rid):
        return mgr.call('PATCH', f'/api/shift-swap-requests/{rid}/review', dict(
            status='APPROVED', reviewerNote='Validate current schedule at approval'))

    pid, shifts = fixture('manual', 0, [(0,'04:00',4,1),(0,'15:00',4,1),(0,'07:00',2,0),(0,'19:30',1,0)])
    assign(shifts[0])
    candidates = mgr.call('GET', f'/api/shift-assignments/work-shifts/{shifts[1]}/candidates?positionId={position}')
    check('candidate list accepts same local day across different UTC dates',
          next(c for c in candidates if c['userId'] == employees[0]['id'])['eligible'])
    assign(shifts[1])
    check('manual assignment accepts split shift with total eight hours', owner(shifts[1]) == employees[0]['id'])
    for shift, label in [(shifts[2], 'manual assignment still rejects overlap'),
                          (shifts[3], 'manual assignment still rejects daily overload')]:
        check(label, raw(mgr,'POST',f'/api/shift-assignments/work-shifts/{shift}',dict(userId=employees[0]['id']))[0] == 400)

    _, shifts = fixture('clopening', 1, [(0,'20:00',3,1),(1,'05:00',4,1)])
    assign(shifts[0])
    check('manual assignment rejects cross-day six-hour rest despite same UTC date',
          raw(mgr,'POST',f'/api/shift-assignments/work-shifts/{shifts[1]}',dict(userId=employees[0]['id']))[0] == 400)
    _, shifts = fixture('exact-rest', 2, [(0,'18:00',4,1),(1,'09:00',4,1)])
    assign(shifts[0]); assign(shifts[1])
    check('manual assignment allows exactly eleven hours rest', owner(shifts[1]) == employees[0]['id'])

    pid, shifts = fixture('hybrid-new', 3, [(0,'04:00',4,1),(0,'15:00',4,1)])
    result = generate(pid)
    check('hybrid constructs two split shifts for one eligible employee', result['assignmentsCreated'] == 2 and result['unfilledPositions'] == 0)
    check('publication validates split shifts consistently', publish(pid)['status'] == 'PUBLISHED')
    pid, shifts = fixture('hybrid-fixed', 4, [(0,'04:00',4,1),(0,'15:00',4,1)])
    fixed = assign(shifts[0])
    check('hybrid adds split shift beside fixed manual assignment', generate(pid)['assignmentsCreated'] == 1)
    check('hybrid preserves fixed assignment id', mgr.call('GET',f'/api/shift-assignments/work-shifts/{shifts[0]}')['assignments'][0]['id'] == fixed)
    pid, shifts = fixture('hybrid-night', 5, [(0,'22:00',8,1),(1,'07:00',4,1)])
    check('hybrid cannot treat overnight-to-morning as split shift', generate(pid)['assignmentsCreated'] == 1)

    pid, shifts = fixture('direct-swap', 6, [(0,'04:00',4,1),(0,'15:00',4,1),(0,'16:00',4,1)], (0,1))
    assign(shifts[0]); source = assign(shifts[1]); target = assign(shifts[2],1)
    publish(pid)
    rid = request(0,source,target)
    check('direct swap acceptance allows replacement split shift', accept(1,rid)['status'] == 'ACCEPTED')
    check('manager approves direct swap using shared constraints', approve(rid)['status'] == 'APPROVED')
    check('direct swap updates both owners without conflicting with removed assignments',
          owner(shifts[1]) == employees[1]['id'] and owner(shifts[2]) == employees[0]['id'])

    pid, shifts = fixture('giveaway', 7, [(0,'04:00',4,1),(0,'15:00',4,1)], (0,1))
    assign(shifts[0]); source = assign(shifts[1],1); publish(pid)
    rid = request(1,source); accept(0,rid)
    check('manager approves giveaway completing split shift', approve(rid)['status'] == 'APPROVED' and owner(shifts[1]) == employees[0]['id'])

    pid, shifts = fixture('reject-night-transfer', 8, [(0,'22:00',8,1),(1,'07:00',4,1)], (0,1))
    assign(shifts[0]); source = assign(shifts[1],1); publish(pid)
    rid = request(1,source)
    check('giveaway acceptance still rejects overnight clopening',
          raw(clients[0],'PATCH',f'/api/shift-swap-requests/me/{rid}/respond',dict(status='ACCEPTED'))[0] == 400)

    pid, shifts = fixture('recheck-at-review', 9, [(0,'04:00',4,1),(0,'15:00',4,1),(0,'22:00',4,1)], (0,1,2))
    assign(shifts[0]); source = assign(shifts[1],1); night = assign(shifts[2],2); publish(pid)
    pending = request(1,source); accept(0,pending)
    another = request(2,night); accept(0,another); approve(another)
    check('approval rechecks rest and hours after another transfer changes schedule',
          raw(mgr,'PATCH',f'/api/shift-swap-requests/{pending}/review',dict(status='APPROVED'))[0] == 400)
    check('failed review preserves original shift ownership', owner(shifts[1]) == employees[1]['id'])
    mgr.call('PATCH',f'/api/shift-swap-requests/{pending}/review',dict(status='REJECTED',reviewerNote='Schedule changed'))

    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    OUTPUT.write_text(json.dumps(dict(run=run,passed=len(checks),checks=checks,fixtures=fixtures,
        locationId=location,manager=manager['username'],employees=[e['username'] for e in employees]),
        ensure_ascii=False,indent=2),encoding='utf-8')
    print('Completed',len(checks),'checks:',OUTPUT)


if __name__ == '__main__':
    main()
