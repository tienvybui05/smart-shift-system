"""Attendance workflow checks against compose.attendance-test.yml only. No deletes."""
from concurrent.futures import ThreadPoolExecutor
import argparse
import datetime as dt
import json
from pathlib import Path
import subprocess
import urllib.request
import urllib.error
from hybrid_e2e import Api, user

BASE='http://127.0.0.1:28080'
DB='smart-shift-attendance-db'
OUTPUT=Path(__file__).resolve().parents[1]/'results/attendance-workflow.json'
checks=[]

def sql(query):
    result=subprocess.run(['docker','exec','-i',DB,'psql','-X','-qAt','-v','ON_ERROR_STOP=1',
        '-U','attendance_qa','-d','attendance_qa'],input=query.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
    return result.stdout.decode().strip()
def check(label, condition, detail=''):
    assert condition,f'{label} {detail}'
    checks.append(label)
    print('PASS',label,flush=True)
def raw(api, method, path, body=None, headers=None):
    content=None if body is None else json.dumps(body).encode()
    h={'Content-Type':'application/json','Authorization':'Bearer '+api.token}
    h.update(headers or {})
    request=urllib.request.Request(api.base+path,data=content,headers=h,method=method)
    try:
        with urllib.request.urlopen(request,timeout=40) as r: return r.status,json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code,json.loads(e.read())
def main(seed_only=False):
    assert sql('select current_database()')=='attendance_qa','Refuse to seed any other database'
    admin=Api(BASE).login('admin')
    run=dt.datetime.now().strftime('%m%d%H%M%S%f')
    roles={r['name']:r['id'] for r in admin.call('GET','/api/roles')}
    position=admin.call('GET','/api/positions')[0]['id']
    config=dict(code='AC_'+run,name='Attendance QA '+run,address='Isolated test fixture',timezone='Asia/Ho_Chi_Minh',
        latitude=10.776889,longitude=106.700806,attendanceRadiusMeters=200,wifiSsid='QA_OFFICE',
        wifiBssid='00:11:22:33:44:55',allowedIpAddress='198.51.100.88',wifiRequired=True,active=True)
    location=admin.call('POST','/api/locations',config)['id']
    otherloc=admin.call('POST','/api/locations',config|dict(code='ACB_'+run,name='Other branch QA'))['id']
    manager=user(admin,'ac_m_'+run,'QA Manager',roles['ROLE_MANAGER'],location)
    outsider=user(admin,'ac_o_'+run,'Other Manager',roles['ROLE_MANAGER'],otherloc)
    employee=user(admin,'ac_e_'+run,'QA Employee',roles['ROLE_EMPLOYEE'],location,position)
    colleague=user(admin,'ac_c_'+run,'QA Colleague',roles['ROLE_EMPLOYEE'],location,position)
    mgr=admin.login(manager['username']);other=admin.login(outsider['username'])
    emp=admin.login(employee['username']);peer=admin.login(colleague['username'])
    admin_id=admin.call('GET','/api/auth/me')['id']
    now=dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    start=(now-dt.timedelta(days=7)).date().isoformat()
    end=(now+dt.timedelta(days=1)).date().isoformat()
    period=int(sql(f"""
        insert into schedule_periods(location_id,name,start_date,end_date,status,created_by,published_by,published_at)
        values({location},'Attendance workflow fixture','{start}','{end}','PUBLISHED',{admin_id},{admin_id},now()) returning id;
    """))
    def assignment(person, offset, active=False):
        a=now-dt.timedelta(minutes=10) if active else now-dt.timedelta(hours=36+offset*24)
        b=a+dt.timedelta(hours=8)
        result=json.loads(sql(f"""
            with s as (
              insert into work_shifts(schedule_period_id,start_at,end_at,break_minutes,status)
              values({period},'{a.isoformat()}','{b.isoformat()}',30,'FILLED') returning id
            ), req as (
              insert into shift_requirements(work_shift_id,position_id,min_employees,max_employees,priority)
              select id,{position},1,1,1 from s
            ), assigned as (
              insert into shift_assignments(work_shift_id,user_id,position_id,assignment_source,status,assigned_by)
              select id,{person['id']},{position},'MANUAL','ASSIGNED',{admin_id} from s returning id,work_shift_id
            ) select json_build_object('id',id,'shift',work_shift_id) from assigned;
        """))
        result.update(checkInAt=a.isoformat(),checkOutAt=b.isoformat())
        return result
    ended=[assignment(employee,i) for i in range(4)]
    foreign=assignment(colleague,5)
    active=assignment(employee,0,True)
    state=dict(run=run,baseUrl=BASE,frontend='http://localhost:23000',
        accounts=dict(admin='admin',manager=manager['username'],employee=employee['username'],otherManager=outsider['username']),
        locationId=location,periodId=period,assignments=ended)
    if seed_only:
        target=OUTPUT.with_name('attendance-demo.json')
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
        print('Demo fixtures ready:',target)
        return
    path='/api/attendance-corrections'
    def proposal(a):return dict(shiftAssignmentId=a['id'],checkInAt=a['checkInAt'],checkOutAt=a['checkOutAt'],breakMinutes=30,reason='Quên check-in / cần đối soát')
    query=f'?startDate={start}&endDate={end}'
    check('employee cannot list manager queue',raw(emp,'GET',path+query)[0]==403)
    check('employee cannot request another employee shift',raw(emp,'POST',path+'/me',proposal(foreign))[0]==404)
    check('employee cannot read another employee history',raw(emp,'GET',path+f"/me/history?shiftAssignmentId={foreign['id']}")[0]==404)
    check('manager cannot list another branch',raw(other,'GET',path+query+f'&locationId={location}')[0]==403)
    gps=dict(shiftAssignmentId=active['id'],latitude=config['latitude'],longitude=config['longitude'],accuracyMeters=15,
        deviceFingerprint='qa-network-'+run,deviceLabel='QA browser',deviceExplanation='Thiết bị kiểm thử',
        wifiSsid=config['wifiSsid'],wifiBssid=config['wifiBssid'])
    attendance=raw(emp,'POST','/api/attendances/check-in',gps,{'X-Forwarded-For':'198.51.100.88'})
    check('declared SSID and forged header do not verify network',attendance[0]==201 and attendance[1]['unverifiedWifi'] and attendance[1]['checkInIpAddress']!='198.51.100.88')
    # Use a distinct, real frontend request to confirm the trusted-proxy path.
    # Create a colleague's active shift with a unique time.
    peer_start=now-dt.timedelta(minutes=9)
    peer_end=peer_start+dt.timedelta(hours=8)
    peer_assignment=int(sql(f"""
        with s as (insert into work_shifts(schedule_period_id,start_at,end_at,break_minutes,status)
          values({period},'{peer_start.isoformat()}','{peer_end.isoformat()}',30,'FILLED') returning id)
        insert into shift_assignments(work_shift_id,user_id,position_id,assignment_source,status,assigned_by)
          select id,{colleague['id']},{position},'MANUAL','ASSIGNED',{admin_id} from s returning id;
    """))
    client_ip=attendance[1]['checkInIpAddress']
    admin.call('PUT',f'/api/locations/{location}',config|dict(allowedIpAddress=client_ip))
    proxy_peer=Api('http://127.0.0.1:23000',peer.token)
    peer_result=raw(proxy_peer,'POST','/api/attendances/check-in',gps|dict(shiftAssignmentId=peer_assignment,
        deviceFingerprint='qa-peer-'+run),{'X-Forwarded-For':'198.51.100.88'})
    check('trusted nginx replaces spoofed header and resolves real client',peer_result[0]==201 and
        not peer_result[1]['unverifiedWifi'] and peer_result[1]['checkInIpAddress']==client_ip)
    long_reason='Giải trình kiểm thử. '.ljust(1000,'x')
    long_note='Đã đối soát. '.ljust(1000,'y')
    first=emp.call('POST',path+'/me',proposal(ended[0])|dict(reason=long_reason))
    correction=first['id']
    check('submitting does not create/alter attendance',sql(f"select count(*) from attendances where shift_assignment_id={ended[0]['id']}")=='0')
    check('duplicate pending request is rejected',raw(emp,'POST',path+'/me',proposal(ended[0]))[0]==409)
    check('cross-branch review denied',raw(other,'PATCH',f'{path}/{correction}/review',dict(approved=True,note='Other'))[0]==403)
    check('employee review denied',raw(emp,'PATCH',f'{path}/{correction}/review',dict(approved=True,note='Self'))[0]==403)
    payroll_payload=dict(locationId=location,startDate=start,endDate=end)
    payroll=mgr.call('POST','/api/payroll/calculate',payroll_payload)
    record=next(p for p in payroll if p['userId']==employee['id'])
    check('pending correction blocks payroll confirmation',raw(mgr,'POST',f"/api/payroll/{record['id']}/confirm")[0]==400)
    reviewed=mgr.call('PATCH',f'{path}/{correction}/review',dict(approved=True,note=long_note))
    check('manager approves proposal',reviewed['status']=='APPROVED')
    history=mgr.call('GET',path+f"/history?shiftAssignmentId={ended[0]['id']}")
    latest=history[0]; after=json.loads(latest['afterData'])
    check('history retains before/after and reviewer',latest['action']=='CORRECTION_APPROVED' and
        json.loads(latest['beforeData']) is None and after['approvedById']==manager['id'] and after['actualMinutes']==450)
    check('maximum length reasons and review notes remain intact',latest['reason']==long_reason and after['note'].endswith(long_note))
    check('manual correction does not invent verified GPS/device/network',after['unverifiedWifi'] and after['unverifiedDevice'] and after['checkInLatitude'] is None)
    check('cannot approve twice',raw(mgr,'PATCH',f'{path}/{correction}/review',dict(approved=True,note='Again'))[0]==400)
    check('changed attendance marks draft payroll stale',next(p for p in mgr.call('GET','/api/payroll'+query+f'&locationId={location}') if p['id']==record['id'])['sourceChanged'])
    check('stale payroll cannot confirm',raw(mgr,'POST',f"/api/payroll/{record['id']}/confirm")[0]==400)
    stale=emp.call('POST',path+'/me',proposal(ended[1]))
    sql(f"insert into attendances(shift_assignment_id,break_minutes,status) values({ended[1]['id']},30,'ABSENT');")
    check('changed baseline cannot be overwritten',raw(mgr,'PATCH',f"{path}/{stale['id']}/review",dict(approved=True,note='Stale'))[0]==400)
    mgr.call('PATCH',f"{path}/{stale['id']}/review",dict(approved=False,note='Dữ liệu thay đổi, đề nghị gửi lại'))
    cancelled=emp.call('POST',path+'/me',proposal(ended[2]))
    check('owner cancels pending request',emp.call('PATCH',f"{path}/me/{cancelled['id']}/cancel")['status']=='CANCELLED')
    check('cancelled request cannot be reviewed',raw(mgr,'PATCH',f"{path}/{cancelled['id']}/review",dict(approved=True,note='Cancelled'))[0]==400)
    with ThreadPoolExecutor(max_workers=2) as pool:
        futures=[pool.submit(raw,emp,'POST',path+'/me',proposal(ended[3])) for _ in range(2)]
        results=[f.result() for f in futures]
    check('simultaneous submit creates one pending request',sorted(r[0] for r in results)==[201,409])
    concurrent=next(r[1] for r in results if r[0]==201)['id']
    with ThreadPoolExecutor(max_workers=2) as pool:
        jobs=[pool.submit(raw,mgr,'PATCH',f'{path}/{concurrent}/review',dict(approved=decision,note='Concurrent review')) for decision in [True,False]]
        results=[j.result() for j in jobs]
    check('concurrent reviews apply exactly one decision',sorted(r[0] for r in results)==[200,400])
    check('concurrent reviews create at most one attendance change',
        int(sql(f"select count(*) from attendance_history where shift_assignment_id={ended[3]['id']} and action='CORRECTION_APPROVED'"))<=1)
    payroll=mgr.call('POST','/api/payroll/calculate',payroll_payload)
    record=next(p for p in payroll if p['userId']==employee['id'])
    check('recalculation clears stale flag and includes approved work',not record['sourceChanged'] and record['workedMinutes']>=450)
    pending_race=emp.call('POST',path+'/me',proposal(ended[1]))
    with ThreadPoolExecutor(max_workers=2) as pool:
        approving=pool.submit(raw,mgr,'PATCH',f"{path}/{pending_race['id']}/review",dict(approved=True,note='Đối soát đồng thời'))
        confirming=pool.submit(raw,mgr,'POST',f"/api/payroll/{record['id']}/confirm")
        approval_result,confirmation_result=approving.result(),confirming.result()
    check('approval racing payroll confirmation cannot confirm an old snapshot',approval_result[0]==200 and confirmation_result[0]==400)
    payroll=mgr.call('POST','/api/payroll/calculate',payroll_payload)
    record=next(p for p in payroll if p['userId']==employee['id'])
    with ThreadPoolExecutor(max_workers=2) as pool:
        confirming=pool.submit(raw,mgr,'POST',f"/api/payroll/{record['id']}/confirm")
        submitting=pool.submit(raw,emp,'POST',path+'/me',proposal(ended[2]))
        confirmation_result,submission_result=confirming.result(),submitting.result()
    check('submit racing payroll confirmation cannot leave a pending request in closed payroll',
        (confirmation_result[0],submission_result[0]) in [(200,400),(400,201)],
        f'statuses: confirm={confirmation_result[0]}, submit={submission_result[0]}')
    if submission_result[0]==201:
        emp.call('PATCH',f"{path}/me/{submission_result[1]['id']}/cancel")
    check('payroll confirms after all pending work resolved',mgr.call('POST',f"/api/payroll/{record['id']}/confirm")['status']=='CONFIRMED')
    check('confirmed payroll blocks new correction',raw(emp,'POST',path+'/me',proposal(ended[0]))[0]==400)
    check('confirmed payroll blocks direct attendance adjustment',raw(mgr,'PUT',f"/api/attendances/{after['id']}/approve",
        dict(checkInAt=after['checkInAt'],checkOutAt=after['checkOutAt'],breakMinutes=30,note='Cannot change closed payroll'))[0]==400)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    OUTPUT.write_text(json.dumps(state|dict(passed=len(checks),checks=checks,payrollRecordId=record['id']),ensure_ascii=False,indent=2),encoding='utf-8')
    print('Completed',len(checks),'checks. Result:',OUTPUT)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seed-only',action='store_true',help='Create fresh demo accounts/shifts without running or closing payroll')
    main(parser.parse_args().seed_only)

