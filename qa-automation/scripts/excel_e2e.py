"""Read real XLSX exports with the Python standard library; isolated attendance_qa fixtures only."""
import datetime as dt
from decimal import Decimal
import io
import json
from pathlib import Path
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET
import zipfile
from attendance_workflow_e2e import BASE, sql
from hybrid_e2e import Api, user, period

OUTPUT=Path(__file__).resolve().parents[1]/'results/excel'
NS={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
ZONE=dt.timezone(dt.timedelta(hours=7))


def get(api,path):
    headers={'Authorization':'Bearer '+api.token} if api.token else {}
    request=urllib.request.Request(api.base+path,headers=headers)
    try:
        with urllib.request.urlopen(request,timeout=120) as response:
            return response.status,dict(response.headers),response.read()
    except urllib.error.HTTPError as error:
        return error.code,dict(error.headers),error.read()


def workbook(content):
    archive=zipfile.ZipFile(io.BytesIO(content))
    assert archive.testzip() is None
    meta=ET.fromstring(archive.read('xl/workbook.xml'))
    names=[s.attrib['name'] for s in meta.find('s:sheets',NS)]
    shared=[]
    if 'xl/sharedStrings.xml' in archive.namelist():
        shared=[''.join(t.itertext()) for t in ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('s:si',NS)]
    sheets={}
    for index,name in enumerate(names,1):
        root=ET.fromstring(archive.read(f'xl/worksheets/sheet{index}.xml'))
        rows=[]
        for row in root.findall('s:sheetData/s:row',NS):
            cells={}
            for cell in row.findall('s:c',NS):
                kind=cell.attrib.get('t','n')
                value=cell.find('s:v',NS)
                if kind=='inlineStr': value=''.join(t.text or '' for t in cell.findall('s:is//s:t',NS))
                elif kind=='s': value=shared[int(value.text)]
                elif value is not None: value=Decimal(value.text) if kind=='n' else value.text
                else: value=None
                cells[''.join(c for c in cell.attrib['r'] if c.isalpha())]=(kind,value)
            rows.append(cells)
        header={key:value[1] for key,value in rows[0].items()}
        records=[{header[key]:value for key,value in row.items() if key in header} for row in rows[1:]]
        sheets[name]=dict(rows=records,root=root)
    return sheets


def main():
    assert sql('select current_database()')=='attendance_qa'
    admin=Api(BASE).login('admin')
    admin_id=admin.call('GET','/api/auth/me')['id']
    run=dt.datetime.now().strftime('%m%d%H%M%S%f')
    roles={r['name']:r['id'] for r in admin.call('GET','/api/roles')}
    position=admin.call('GET','/api/positions')[0]['id']
    cfg=dict(code='XLS_'+run,name='Xuất Excel '+run,address='Isolated XLSX fixture',timezone='Asia/Ho_Chi_Minh',
             latitude=10.776889,longitude=106.700806,attendanceRadiusMeters=200,wifiRequired=False,active=True)
    location=admin.call('POST','/api/locations',cfg)['id']
    foreign=admin.call('POST','/api/locations',cfg|dict(code='XLSB_'+run,name='Other export branch'))['id']
    manager=user(admin,'xls_m_'+run,'Quản lý xuất Excel',roles['ROLE_MANAGER'],location)
    outsider=user(admin,'xls_o_'+run,'Other export manager',roles['ROLE_MANAGER'],foreign)
    employees=[user(admin,'xls_e0_'+run,'=SUM(1,1)',roles['ROLE_EMPLOYEE'],location,position),
               user(admin,'xls_e1_'+run,'Nguyễn Thị Hồng',roles['ROLE_EMPLOYEE'],location,position,rate=45000),
               user(admin,'xls_e2_'+run,'Foreign employee',roles['ROLE_EMPLOYEE'],foreign,position)]
    mgr,other=admin.login(manager['username']),admin.login(outsider['username'])
    employee=admin.login(employees[0]['username'])
    day=dt.datetime.now(ZONE).date()-dt.timedelta(days=5)
    start,end=str(day),str(day+dt.timedelta(days=4))
    pid=period(admin,location,'XLSX fixture '+run,day,day+dt.timedelta(days=4))
    foreign_pid=period(admin,foreign,'Other XLSX '+run,day,day+dt.timedelta(days=4))
    sql(f"update schedule_periods set status='PUBLISHED',published_by={admin_id},published_at=now() where id in ({pid},{foreign_pid});")
    literal='=HYPERLINK("https://example.test","Ghi chú tiếng Việt")'

    def attendance(person,offset,clock,hours,status,approved=True,absent=False):
        a=dt.datetime.combine(day+dt.timedelta(days=offset),dt.time.fromisoformat(clock),ZONE)
        b=a+dt.timedelta(hours=hours)
        late=5 if status=='LATE' else 0
        check_in=a+dt.timedelta(minutes=late)
        period_id=foreign_pid if person==2 else pid
        times='null,null' if absent else f"'{check_in.isoformat()}','{b.isoformat()}'"
        approved_by=manager['id'] if approved else 'null'
        approved_at='now()' if approved else 'null'
        return int(sql(f"""
          with s as (insert into work_shifts(schedule_period_id,start_at,end_at,break_minutes,status)
            values({period_id},'{a.isoformat()}','{b.isoformat()}',30,'COMPLETED') returning id),
          req as (insert into shift_requirements(work_shift_id,position_id,min_employees,max_employees,priority)
            select id,{position},1,1,1 from s),
          assigned as (insert into shift_assignments(work_shift_id,user_id,position_id,assignment_source,status,assigned_by)
            select id,{employees[person]['id']},{position},'MANUAL','ASSIGNED',{admin_id} from s returning id)
          insert into attendances(shift_assignment_id,check_in_at,check_out_at,break_minutes,status,
            late_minutes,early_leave_minutes,overtime_minutes,approved_by,approved_at,note,is_unverified_wifi,is_unverified_device)
          select id,{times},30,'{status}',{late},0,0,{approved_by},{approved_at},'{literal}',true,true from assigned returning id;
        """))

    attendance(0,0,'22:00',8,'LATE')
    attendance(1,2,'08:00',4,'PRESENT')
    attendance(0,4,'08:00',4,'ABSENT',approved=False,absent=True)
    attendance(2,2,'08:00',4,'PRESENT')
    pay=mgr.call('POST','/api/payroll/calculate',dict(locationId=location,startDate=start,endDate=end))
    draft=next(r for r in pay if r['userId']==employees[0]['id'])
    confirmed=next(r for r in pay if r['userId']==employees[1]['id'])
    mgr.call('PATCH',f"/api/payroll/{draft['id']}/bonus",dict(bonusAmount='1234.56',bonusNote=literal))
    mgr.call('POST',f"/api/payroll/{confirmed['id']}/confirm")
    sql(f"update payroll_records set source_changed=true where id={draft['id']};")
    query=f'?startDate={start}&endDate={end}'
    checks=[]

    def check(label,condition):
        assert condition,label
        checks.append(label); print('PASS',label,flush=True)

    for kind,path in [('attendance','/api/attendances/export'),('payroll','/api/payroll/export')]:
        check(kind+' export requires authentication',get(Api(BASE),path+query)[0]==401)
        check(kind+' export denies employee',get(employee,path+query)[0]==403)
        check(kind+' export denies foreign manager',get(other,path+query+f'&locationId={location}')[0]==403)
        check(kind+' export rejects invalid date range',get(mgr,path+f'?startDate={end}&endDate={start}')[0]==400)
    status,headers,content=get(mgr,'/api/attendances/export'+query)
    check('returns actual XLSX MIME and attachment filename',status==200 and headers.get('Content-Type','').startswith('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') and '.xlsx' in headers.get('Content-Disposition','') and content[:2]==b'PK')
    check('private export is not cached', 'no-store' in headers.get('Cache-Control',''))
    sheets=workbook(content)
    check('attendance workbook has metadata detail and summary',list(sheets)==['Thông tin','Chấm công','Tổng hợp'])
    rows=sheets['Chấm công']['rows']
    original=mgr.call('GET','/api/attendances'+query)
    check('manager export matches JSON filter and contains only own branch',len(rows)==len(original)==3 and all(r['Chi nhánh'][1]==cfg['name'] for r in rows))
    check('literal formula-like names and notes stay string cells',any(r['Họ tên'][1]=='=SUM(1,1)' and r['Họ tên'][0] in ['s','inlineStr'] for r in rows) and not sheets['Chấm công']['root'].findall('.//s:f',NS))
    night=next(r for r in rows if r['Trạng thái'][1]=='Đi trễ')
    check('minutes and hours are Excel numeric cells',night['Giờ công (phút)']==('n',Decimal(445)) and night['Giờ công (giờ)'][0]=='n')
    in_time=dt.datetime(1899,12,30)+dt.timedelta(days=float(night['Vào thực tế'][1]))
    out_time=dt.datetime(1899,12,30)+dt.timedelta(days=float(night['Ra thực tế'][1]))
    check('overnight timestamps use branch timezone and retain next day',in_time.date()==day and in_time.hour==22 and in_time.minute==5 and out_time.date()==day+dt.timedelta(days=1) and out_time.hour==6)
    absent=next(r for r in rows if r['Trạng thái'][1]=='Vắng mặt')
    check('absent attendance keeps missing timestamps and minutes blank',absent['Vào thực tế'][1] is None and absent['Giờ công (phút)'][1] is None)
    status,_,filtered=get(mgr,'/api/attendances/export'+query+'&status=LATE')
    check('attendance status filter applies to workbook',status==200 and len(workbook(filtered)['Chấm công']['rows'])==1)
    status,_,empty=get(mgr,'/api/attendances/export'+query+'&status=EARLY_LEAVE')
    check('empty report is still a valid workbook with headers',status==200 and not workbook(empty)['Chấm công']['rows'])
    check('admin can explicitly export another branch',get(admin,'/api/attendances/export'+query+f'&locationId={foreign}')[0]==200)
    payroll_before=mgr.call('GET','/api/payroll'+query)
    status,pay_headers,pay_content=get(mgr,'/api/payroll/export'+query)
    pay_sheets=workbook(pay_content); pay_rows=pay_sheets['Bảng lương']['rows']
    check('payroll export uses saved snapshots without changing them',status==200 and mgr.call('GET','/api/payroll'+query)==payroll_before)
    check('payroll keeps draft confirmed and stale labels',any(r['Trạng thái'][1]=='Nháp' and r['Nguồn công'][1]=='Cần tính lại công' for r in pay_rows) and any(r['Trạng thái'][1]=='Đã xác nhận' for r in pay_rows))
    expected=sum(Decimal(str(r['totalAmount'])) for r in payroll_before)
    check('payroll summary preserves exact sum including fractional bonus',pay_sheets['Tổng hợp']['rows'][0]['Tổng lương dự tính (VND)'][1]==expected)
    check('payroll numbers can be summed in Excel',all(r['Tổng lương dự tính (VND)'][0]=='n' for r in pay_rows))
    check('payroll note is literal and workbook contains no formulas',not pay_sheets['Bảng lương']['root'].findall('.//s:f',NS) and any(r['Ghi chú thưởng'][1]==literal for r in pay_rows))
    OUTPUT.mkdir(parents=True,exist_ok=True)
    (OUTPUT/'attendance.xlsx').write_bytes(content); (OUTPUT/'payroll.xlsx').write_bytes(pay_content)
    manifest=dict(run=run,passed=len(checks),checks=checks,startDate=start,endDate=end,locationId=location,
                  manager=manager['username'],employee=employees[0]['username'],payrollAttachment=pay_headers.get('Content-Disposition'))
    (OUTPUT/'result.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Completed',len(checks),'checks:',OUTPUT)


if __name__=='__main__': main()
