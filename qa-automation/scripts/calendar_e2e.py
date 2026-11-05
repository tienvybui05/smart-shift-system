"""Calendar preview/placement regression and visual demo fixtures in attendance_qa only."""
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import json
from pathlib import Path
from attendance_workflow_e2e import BASE,raw,sql
from hybrid_e2e import Api,user,period

ZONE=dt.timezone(dt.timedelta(hours=7))
OUTPUT=Path(__file__).resolve().parents[1]/'results/calendar.json'


def main():
    assert sql('select current_database()')=='attendance_qa'
    admin=Api(BASE).login('admin'); admin_id=admin.call('GET','/api/auth/me')['id']
    run=dt.datetime.now().strftime('%m%d%H%M%S%f')
    roles={r['name']:r['id'] for r in admin.call('GET','/api/roles')}
    jobs=admin.call('GET','/api/positions'); primary,secondary=jobs[0]['id'],jobs[1]['id']
    cfg=dict(code='CAL_'+run,name='Smart Shift · Nguyễn Huệ',address='QA calendar fixture',timezone='Asia/Ho_Chi_Minh',
        latitude=10.776889,longitude=106.700806,attendanceRadiusMeters=200,wifiRequired=False,active=True)
    location=admin.call('POST','/api/locations',cfg)['id']
    foreign=admin.call('POST','/api/locations',cfg|dict(code='CALB_'+run,name='Chi nhánh khác'))['id']
    manager=user(admin,'cal_m_'+run,'Quản lý lịch',roles['ROLE_MANAGER'],location)
    outsider=user(admin,'cal_o_'+run,'Quản lý chi nhánh khác',roles['ROLE_MANAGER'],foreign)
    names=['Nguyễn Minh Anh','Trần Quốc Bảo','Lê Ngọc Linh','Phạm Tuấn Khải','Võ Thanh Hà','Đặng Hoàng Nam','Bùi Khánh Vy','Trương Đức Huy','Ngô Thảo Nhi']
    employees=[user(admin,f'cal_e{i}_'+run,name,roles['ROLE_EMPLOYEE'],location,secondary if i==2 else primary) for i,name in enumerate(names)]
    mgr,other=admin.login(manager['username']),admin.login(outsider['username'])
    clients=[admin.login(e['username']) for e in employees]
    today=dt.datetime.now(ZONE).date(); monday=today+dt.timedelta(days=7-today.weekday())
    pid=period(admin,location,'Kiểm thử lịch '+run,monday,monday+dt.timedelta(days=6))
    demo_day=monday+dt.timedelta(weeks=1)
    demo=period(admin,location,'Tuần làm việc · Nguyễn Huệ',demo_day,demo_day+dt.timedelta(days=6))
    foreign_pid=period(admin,foreign,'Other calendar '+run,monday,monday+dt.timedelta(days=6))
    templates=[admin.call('POST','/api/shift-templates',dict(locationId=location,name=name,startTime=start,endTime=end,
        breakMinutes=0,colorCode=color,active=True))['id'] for name,start,end,color in
        [('Ca sáng','08:00','12:00','#9782DD'),('Ca chiều','15:00','19:00','#75B69F')]]
    for index,employee in enumerate(employees):
        for start_day in [monday,demo_day]:
            for offset in range(7):
                clients[index].call('POST','/api/employee-availabilities/me',dict(availableDate=str(start_day+dt.timedelta(days=offset)),
                    startTime='00:00',endTime='23:59',availabilityType='UNAVAILABLE' if index==5 and start_day==monday and offset==2 else 'AVAILABLE'))
    sql(f"update users set max_consecutive_days=1 where id={employees[6]['id']}; update users set min_hours_per_week=0,max_hours_per_week=4 where id={employees[8]['id']};")
    def shift(period_id,day,clock,hours=4,minimum=1,maximum=1,template=None,support=False):
        a=dt.datetime.combine(day,dt.time.fromisoformat(clock),ZONE); b=a+dt.timedelta(hours=hours)
        sid=int(sql(f"""insert into work_shifts(schedule_period_id,shift_template_id,start_at,end_at,break_minutes,status)
            values({period_id},{template or 'null'},'{a.isoformat()}','{b.isoformat()}',0,'OPEN') returning id;"""))
        sql(f"insert into shift_requirements(work_shift_id,position_id,min_employees,max_employees,priority) values({sid},{primary},{minimum},{maximum},1);")
        if support: sql(f"insert into shift_requirements(work_shift_id,position_id,min_employees,max_employees,priority) values({sid},{secondary},1,1,2);")
        return sid
    def seed(sid,index):
        return int(sql(f"insert into shift_assignments(work_shift_id,user_id,position_id,assignment_source,status,assigned_by) values({sid},{employees[index]['id']},{employees[index]['positionId']},'MANUAL','ASSIGNED',{admin_id}) returning id;"))
    def payload(index,target,source=None):
        body=dict(userId=employees[index]['id'],targetWorkShiftId=target,reason='Calendar QA')
        if source:
            ref=sql(f"select work_shift_id||','||version from shift_assignments where id={source}").split(',')
            body.update(sourceAssignmentId=source,sourceWorkShiftId=int(ref[0]),expectedVersion=int(ref[1]))
        return body
    checks=[]
    def check(label,condition):
        assert condition,label
        checks.append(label); print('PASS',label,flush=True)
    path=f'/api/schedule-periods/{pid}/board'
    source_shift=shift(pid,monday,'08:00'); overlap=shift(pid,monday,'09:00'); afternoon=shift(pid,monday,'15:00')
    race=shift(pid,monday+dt.timedelta(days=1),'08:00')
    too_long=shift(pid,monday,'18:00',5)
    blocked=shift(pid,monday+dt.timedelta(days=2),'08:00')
    night=shift(pid,monday+dt.timedelta(days=2),'22:00',8)
    early=shift(pid,monday+dt.timedelta(days=3),'07:00')
    later=shift(pid,monday+dt.timedelta(days=1),'15:00')
    source=seed(source_shift,0)
    check('employee cannot read calendar',raw(clients[0],'GET',path)[0]==403)
    check('foreign manager cannot read calendar',raw(other,'GET',path)[0]==403)
    initial=mgr.call('GET',path)
    check('board returns branch timezone shifts and employee data',initial['timezone']=='Asia/Ho_Chi_Minh' and len(initial['employees'])==9 and len(initial['shifts'])==9)
    body=payload(0,overlap,source)
    count=sql(f'select count(*) from schedule_audit_logs where schedule_period_id={pid}')
    preview=mgr.call('POST',path+'/preview',body)
    check('preview allows move overlapping source when source is excluded',preview['eligible'])
    check('preview does not mutate or write audit',sql(f'select work_shift_id from shift_assignments where id={source}')==str(source_shift) and sql(f'select count(*) from schedule_audit_logs where schedule_period_id={pid}')==count)
    moved=mgr.call('POST',path+'/place',body)
    check('move preserves assignment id and increments version',moved['assignmentId']==source and moved['version']==1)
    check('move warns about shortage left at source',bool(moved['warnings']))
    check('move records both sides in audit',int(sql(f'select count(*) from schedule_audit_logs where schedule_period_id={pid}'))==int(count)+2)
    check('repeating stale drop returns conflict',raw(mgr,'POST',path+'/place',body)[0]==409)
    check('full target rejects new assignment',raw(mgr,'POST',path+'/place',payload(1,overlap))[0]==400)
    check('wrong position is rejected',not mgr.call('POST',path+'/preview',payload(2,afternoon))['eligible'])
    check('foreign manager cannot place employees',raw(other,'POST',path+'/place',payload(1,afternoon))[0]==403)
    check('employee cannot place through forged request',raw(clients[0],'POST',path+'/place',payload(0,afternoon))[0]==403)
    check('daily overload is rejected',raw(mgr,'POST',path+'/place',payload(0,too_long))[0]==400)
    check('unavailable employee is rejected',raw(mgr,'POST',path+'/place',payload(5,blocked))[0]==400)
    mgr.call('POST',path+'/place',payload(0,source_shift,source))
    stale=payload(0,overlap,source)
    with ThreadPoolExecutor(max_workers=2) as pool:
        jobs=[pool.submit(raw,mgr,'POST',path+'/place',stale|dict(targetWorkShiftId=target)) for target in [overlap,afternoon]]
        statuses=sorted(job.result()[0] for job in jobs)
    check('two concurrent moves of one assignment apply once',statuses==[200,409])
    check('concurrent moves never lose or duplicate source row',sql(f'select count(*) from shift_assignments where id={source}')=='1')
    with ThreadPoolExecutor(max_workers=2) as pool:
        jobs=[pool.submit(raw,mgr,'POST',path+'/place',payload(person,race)) for person in [3,4]]
        statuses=sorted(job.result()[0] for job in jobs)
    check('concurrent placements respect one remaining slot',statuses==[200,400])
    check('capacity remains valid in database',sql(f"select count(*) from shift_assignments where work_shift_id={race} and status in ('ASSIGNED','CONFIRMED')")=='1')
    mgr.call('POST',path+'/place',payload(1,night))
    check('overnight clopening is rejected',raw(mgr,'POST',path+'/place',payload(1,early))[0]==400)
    seed(source_shift,6)
    check('maximum consecutive days is enforced',raw(mgr,'POST',path+'/place',payload(6,later))[0]==400)
    seed(source_shift,8)
    check('weekly hour cap is enforced',raw(mgr,'POST',path+'/place',payload(8,later))[0]==400)
    history=seed(source_shift,7)
    sql(f"insert into attendances(shift_assignment_id,break_minutes,status) values({history},0,'ABSENT');")
    check('assignment with attendance cannot move',raw(mgr,'POST',path+'/place',payload(7,later,history))[0]==400)
    other_shift=shift(demo,demo_day,'06:00')
    check('moving to another period is rejected',raw(mgr,'POST',path+'/place',payload(0,other_shift,source))[0]==400)
    sql(f"update schedule_periods set status='PUBLISHED',published_by={admin_id},published_at=now() where id={pid};")
    check('published calendar is read-only',not mgr.call('GET',path)['shifts'][0]['editable'] and raw(mgr,'POST',path+'/place',payload(1,later))[0]==400)
    # Separate editable showcase used by actual browser drag/drop checks and manual demo.
    demo_shifts=[]
    for offset in range(7):
        day=demo_day+dt.timedelta(days=offset)
        morning=shift(demo,day,'08:00',minimum=2,maximum=2,template=templates[0],support=True)
        evening=shift(demo,day,'15:00',minimum=2,maximum=2,template=templates[1])
        demo_shifts.append(dict(morning=morning,evening=evening))
        if offset<5:
            for person in ([0,1,2] if offset%2==0 else [0,2]): seed(morning,person)
        if offset in [1,3]:
            for person in [3,4]: seed(evening,person)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    OUTPUT.write_text(json.dumps(dict(run=run,passed=len(checks),checks=checks,manager=manager['username'],
        employee=employees[3]['username'],locationId=location,periodId=demo,readOnlyPeriodId=pid,periodName='Tuần làm việc · Nguyễn Huệ',
        employees=employees,shifts=demo_shifts,startDate=str(demo_day)),ensure_ascii=False,indent=2),encoding='utf-8')
    print('Completed',len(checks),'checks:',OUTPUT)


if __name__=='__main__': main()
