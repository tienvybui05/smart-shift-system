"""Weekly scheduling budget checks; isolated attendance_qa database only, no deletes."""
import datetime as dt
import json
from pathlib import Path
from attendance_workflow_e2e import BASE, sql, raw
from hybrid_e2e import Api, user, period

ZONE = dt.timezone(dt.timedelta(hours=7))
OUTPUT = Path(__file__).resolve().parents[1] / 'results/budget.json'


def main():
    assert sql('select current_database()') == 'attendance_qa'
    admin = Api(BASE).login('admin')
    run = dt.datetime.now().strftime('%m%d%H%M%S%f')
    roles = {r['name']: r['id'] for r in admin.call('GET', '/api/roles')}
    position = admin.call('GET', '/api/positions')[0]['id']
    admin_id = admin.call('GET', '/api/auth/me')['id']
    config = dict(code='BUD_' + run, name='Budget QA ' + run, address='Isolated budget fixtures',
                  timezone='Asia/Ho_Chi_Minh', latitude=10.776889, longitude=106.700806,
                  attendanceRadiusMeters=200, wifiSsid='KEEP_WIFI', wifiRequired=False, active=True)
    location = admin.call('POST', '/api/locations', config)['id']
    other_location = admin.call('POST', '/api/locations', config | dict(code='BUDB_' + run))['id']
    manager = user(admin, 'bud_m_' + run, 'Budget QA manager', roles['ROLE_MANAGER'], location)
    outsider = user(admin, 'bud_o_' + run, 'Other budget manager', roles['ROLE_MANAGER'], other_location)
    employees = [user(admin, f'bud_e{i}_' + run, f'Budget employee {i}', roles['ROLE_EMPLOYEE'],
                      location, position, rate=rate) for i, rate in enumerate([30000, 50000])]
    mgr, other = admin.login(manager['username']), admin.login(outsider['username'])
    clients = [admin.login(e['username']) for e in employees]
    settings_path = f'/api/scheduling-budgets/locations/{location}'
    checks = []

    def check(label, condition):
        assert condition, label
        checks.append(label)
        print('PASS', label, flush=True)

    def budget(value):
        return mgr.call('PATCH', settings_path, dict(weeklyBudget=value))

    def report(pid):
        return mgr.call('GET', f'/api/scheduling-budgets/periods/{pid}')

    check('employee cannot read budget', raw(clients[0], 'GET', settings_path)[0] == 403)
    check('employee cannot change budget', raw(clients[0], 'PATCH', settings_path, dict(weeklyBudget=1))[0] == 403)
    check('other branch cannot read or change budget', raw(other, 'GET', settings_path)[0] == 403 and
          raw(other, 'PATCH', settings_path, dict(weeklyBudget=1))[0] == 403)
    check('negative amount rejected', raw(mgr, 'PATCH', settings_path, dict(weeklyBudget=-1))[0] == 400)
    check('excessive precision rejected', raw(mgr, 'PATCH', settings_path, dict(weeklyBudget='1.001'))[0] == 400)
    check('database precision overflow rejected', raw(mgr, 'PATCH', settings_path, dict(weeklyBudget='10000000000000'))[0] == 400)
    check('manager configures own branch budget', budget(300000)['weeklyBudget'] == 300000)
    check('unrelated branch settings are preserved', admin.call('GET', f'/api/locations/{location}')['wifiSsid'] == 'KEEP_WIFI')
    today = dt.datetime.now(ZONE).date()
    monday = today + dt.timedelta(days=7-today.weekday())
    avail = set()

    def available(person, day):
        if (person, day) in avail:
            return
        avail.add((person, day))
        clients[person].call('POST', '/api/employee-availabilities/me', dict(availableDate=str(day),
            startTime='00:00', endTime='23:59', availabilityType='AVAILABLE'))

    def shift(pid, day, clock, hours=4, pause=0, person=0, assigned=True, status='OPEN', assignment_status='ASSIGNED'):
        start = dt.datetime.combine(day, dt.time.fromisoformat(clock), ZONE)
        end = start + dt.timedelta(hours=hours)
        for d in range((end.date()-day).days+1):
            available(person, day+dt.timedelta(days=d))
        sid = int(sql(f"""
          with s as (insert into work_shifts(schedule_period_id,start_at,end_at,break_minutes,status)
            values({pid},'{start.isoformat()}','{end.isoformat()}',{pause},'{status}') returning id),
          r as (insert into shift_requirements(work_shift_id,position_id,min_employees,max_employees,priority)
            select id,{position},1,1,1 from s) select id from s;
        """))
        if assigned:
            sql(f"""insert into shift_assignments(work_shift_id,user_id,position_id,assignment_source,status,assigned_by)
              values({sid},{employees[person]['id']},{position},'MANUAL','{assignment_status}',{admin_id});""")
        return sid

    first = period(admin, location, 'Budget partial ' + run, monday, monday+dt.timedelta(days=1))
    second = period(admin, location, 'Budget adjacent ' + run, monday+dt.timedelta(days=2), monday+dt.timedelta(days=6))
    third = period(admin, location, 'Budget empty ' + run, monday+dt.timedelta(weeks=1), monday+dt.timedelta(weeks=1,days=1))
    previous = period(admin, location, 'Budget overnight ' + run, monday-dt.timedelta(days=1), monday-dt.timedelta(days=1))
    shift(first, monday, '04:00', pause=30)
    shift(first, monday+dt.timedelta(days=1), '15:00')
    shift(second, monday+dt.timedelta(days=2), '10:00')
    shift(second, monday+dt.timedelta(days=3), '08:00', person=1, status='CANCELLED')
    shift(second, monday+dt.timedelta(days=4), '08:00', person=1, assignment_status='CANCELLED')
    shift(previous, monday-dt.timedelta(days=1), '22:00', person=1)
    check('employee and foreign manager cannot read period cost',
          raw(clients[0], 'GET', f'/api/scheduling-budgets/periods/{first}')[0] == 403 and
          raw(other, 'GET', f'/api/scheduling-budgets/periods/{first}')[0] == 403)
    data = report(first)
    week = data['weeks'][0]
    check('partial period keeps full weekly budget', data['weeklyBudget'] == 300000 and len(data['weeks']) == 1)
    check('deducts breaks from projected cost', data['periodCost'] == 225000)
    check('includes adjacent period within same local week', week['otherPeriodsCost'] == 120000 and week['totalCost'] == 345000)
    check('cancelled shifts and cancelled assignments are excluded', week['assignedShifts'] == 3)
    check('over-budget excess is exact and remaining is nonnegative', data['overBudget'] and week['excess'] == 45000 and week['remaining'] == 0)
    check('Sunday overnight cost belongs to its local start week', report(previous)['periodCost'] == 200000 and week['totalCost'] == 345000)
    check('empty next week has no prior-week cost', report(third)['weeks'][0]['totalCost'] == 0 and report(third)['weeks'][0]['remaining'] == 300000)
    budget(0)
    check('zero budget differs from unset and never divides by zero', report(first)['weeks'][0]['utilizationPercent'] is None and report(first)['weeks'][0]['excess'] == 345000)
    budget(None)
    check('cleared budget preserves cost and removes exceedance warning', not report(first)['overBudget'] and report(first)['periodCost'] == 225000 and report(first)['weeks'][0]['remaining'] is None)
    budget(345000)
    check('exact budget is not exceeded', not report(first)['overBudget'] and report(first)['weeks'][0]['utilizationPercent'] == 100)
    budget(300000)
    check('soft budget permits publication of valid over-budget schedule', mgr.call('POST', f'/api/schedule-periods/{first}/publish')['status'] == 'PUBLISHED')
    check('changing budget does not change saved assignments', report(first)['periodCost'] == 225000)
    budget(1)
    auto_day = monday+dt.timedelta(weeks=2)
    auto = period(admin, location, 'Budget auto ' + run, auto_day, auto_day)
    shift(auto, auto_day, '08:00', assigned=False)
    available(1, auto_day)
    result = mgr.call('POST', '/api/schedules/generate', dict(schedulePeriodId=auto))
    check('hybrid covers demand despite soft budget excess', result['assignmentsCreated'] == 1 and result['unfilledPositions'] == 0)
    check('hybrid prefers cheaper eligible employee', result['assignments'][0]['userId'] == employees[0]['id'])
    check('report reflects newly generated cost and excess', report(auto)['periodCost'] == 120000 and report(auto)['weeks'][0]['excess'] == 119999)
    demo_day = monday+dt.timedelta(weeks=3)
    demo_name = 'Budget browser ' + run
    demo = period(admin, location, demo_name, demo_day, demo_day)
    shift(demo, demo_day, '08:00', assigned=False)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(dict(run=run,passed=len(checks),checks=checks,locationId=location,
        manager=manager['username'],employee=employees[0]['username'],periodId=demo,periodName=demo_name,
        reportPeriodId=first),ensure_ascii=False,indent=2),encoding='utf-8')
    print('Completed',len(checks),'checks:',OUTPUT)


if __name__ == '__main__':
    main()
