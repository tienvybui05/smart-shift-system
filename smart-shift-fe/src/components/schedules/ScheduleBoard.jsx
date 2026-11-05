import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Drawer, Empty, Input, Segmented, Select, Skeleton, Tooltip, message } from 'antd'
import { ArrowLeftOutlined, ArrowRightOutlined, CalendarOutlined, CheckCircleOutlined, ClockCircleOutlined, CloseOutlined, HolderOutlined, LockOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, TeamOutlined, UserOutlined, WarningOutlined } from '@ant-design/icons'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/react/daygrid'
import monarchTheme from '@fullcalendar/react/themes/monarch'
import vi from '@fullcalendar/react/locales/vi'
import '@fullcalendar/react/skeleton.css'
import '@fullcalendar/react/themes/monarch/theme.css'
import '@fullcalendar/react/themes/monarch/palettes/purple.css'
import { getScheduleBoard, placeCalendarEmployee, previewCalendarPlacement } from '../../services/scheduleBoardService.js'
import { getApiErrorMessage } from '../../api/apiError.js'
import './ScheduleBoard.css'

const transferType = 'application/x-smartshift-roster'
const time = value => value?.slice(0, 5) || '--:--'
const initials = name => name?.trim().split(/\s+/).slice(-2).map(word => word[0]).join('').toUpperCase() || 'NV'
const avatarColor = id => ['purple', 'teal', 'blue', 'orange', 'rose'][Number(id) % 5]
function nextDay(day) { const date = new Date(`${day}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + 1); return date.toISOString().slice(0, 10) }
function branchDay(value, zone) { return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value)) }
function netHours(shift) { return Math.max(0, (Number(shift.durationMinutes) - Number(shift.breakMinutes)) / 60) }

export default function ScheduleBoard({ periodId, statusFilter, refreshKey, onChanged, onOpenShift }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState(null)
  const [retry, setRetry] = useState(0)
  const [pending, setPending] = useState(false)
  const [selected, setSelected] = useState(null)
  const [hover, setHover] = useState(null)
  const [search, setSearch] = useState('')
  const [position, setPosition] = useState(null)
  const [view, setView] = useState(() => window.innerWidth < 760 ? 'dayGridDay' : 'dayGridWeek')
  const [range, setRange] = useState(null)
  const [peopleOpen, setPeopleOpen] = useState(false)
  const calendar = useRef(null)
  const previewCache = useRef(new Map())
  const hoverTarget = useRef(null)
  const currentSelection = useRef(null)
  const saving = useRef(false)
  const mounted = useRef(true)
  const [api, context] = message.useMessage()

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])
  useEffect(() => {
    let live = true
    getScheduleBoard(periodId).then(result => { if (live) { setData(result); setError('') } })
      .catch(err => { if (live) setError(getApiErrorMessage(err, 'Không tải được lịch trực quan')) })
    return () => { live = false }
  }, [periodId, refreshKey, retry])

  const editable = data?.periodStatus === 'DRAFT'
  const cards = useMemo(() => data?.shifts.filter(card => !statusFilter || card.shift.status === statusFilter) || [], [data, statusFilter])
  const active = data?.shifts.filter(card => card.shift.status !== 'CANCELLED') || []
  const stats = active.reduce((total, card) => {
    total.assigned += card.assignments.length
    card.requirements.forEach(req => { total.required += req.minEmployees; total.missing += Math.max(0, req.minEmployees - card.assignments.filter(a => a.positionId === req.positionId).length) })
    return total
  }, { assigned: 0, required: 0, missing: 0 })
  const engaged = new Set(active.flatMap(card => card.assignments.map(a => a.userId))).size
  const positions = [...new Map((data?.employees || []).map(e => [e.positionId, e.positionName])).entries()]
  const people = (data?.employees || []).filter(e => (!position || position === e.positionId) && `${e.fullName} ${e.employeeCode}`.toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi')))
  const loads = new Map()
  for (const card of active) if (!range || (card.shift.workDate >= range.start && card.shift.workDate < range.end)) {
    for (const person of card.assignments) loads.set(person.userId, (loads.get(person.userId) || 0) + netHours(card.shift))
  }
  function choose(person, source = null) {
    if (!editable || saving.current) return
    const choice = { person, source, token: {} }
    currentSelection.current = choice
    hoverTarget.current = null
    previewCache.current.clear()
    setSelected(choice); setHover(null); setActionError(null); setPeopleOpen(false)
  }
  function cancelSelection() {
    hoverTarget.current = null
    currentSelection.current = null; setSelected(null); setHover(null); previewCache.current.clear()
  }
  function payload(choice, target) {
    return { userId: choice.person.id, targetWorkShiftId: target,
      sourceAssignmentId: choice.source?.id, sourceWorkShiftId: choice.source?.workShiftId,
      expectedVersion: choice.source?.version, reason: choice.source ? 'Chuyển phân công bằng lịch kéo thả' : 'Phân công bằng lịch kéo thả' }
  }
  function startDrag(event, person, source = null) {
    if (!editable || saving.current) { event.preventDefault(); return }
    choose(person, source)
    event.dataTransfer.effectAllowed = source ? 'move' : 'copy'
    event.dataTransfer.setData(transferType, JSON.stringify({ periodId, userId: person.id }))
  }
  async function preview(card) {
    const choice = currentSelection.current
    if (!choice || saving.current || !card.editable) return
    const key = card.shift.id
    // Custom event content can remount during a native drag. Do not restart the same preview.
    if (hoverTarget.current === key) return
    hoverTarget.current = card.shift.id
    setHover({ id: card.shift.id, loading: true })
    let request = previewCache.current.get(key)
    if (!request) {
      request = previewCalendarPlacement(periodId, payload(choice, card.shift.id))
      previewCache.current.set(key, request)
    }
    try {
      const result = await request
      if (mounted.current && currentSelection.current?.token === choice.token && hoverTarget.current === card.shift.id) setHover({ id: card.shift.id, ...result })
    } catch (err) {
      if (mounted.current && currentSelection.current?.token === choice.token && hoverTarget.current === card.shift.id) setHover({ id: card.shift.id, eligible: false, violations: [getApiErrorMessage(err, 'Không kiểm tra được ca này')] })
    }
  }
  async function place(card) {
    const choice = currentSelection.current
    if (!choice || saving.current || !card.editable) return
    saving.current = true; setPending(true); setActionError(null)
    const original = data
    const temporary = { id: choice.source?.id || `pending-${choice.person.id}`, userId: choice.person.id,
      employeeCode: choice.person.employeeCode, fullName: choice.person.fullName, positionId: choice.person.positionId,
      positionName: choice.person.positionName, source: 'MANUAL', version: choice.source?.version || 0, editable: false, pending: true }
    setData(current => ({ ...current, shifts: current.shifts.map(c => ({ ...c, assignments: [
      ...c.assignments.filter(a => a.id !== choice.source?.id), ...(c.shift.id === card.shift.id ? [temporary] : []),
    ] })) }))
    try {
      const result = await placeCalendarEmployee(periodId, payload(choice, card.shift.id))
      const updated = await getScheduleBoard(periodId)
      if (!mounted.current) return
      setData(updated)
      api.success(`${choice.source ? 'Đã chuyển' : 'Đã phân công'} ${choice.person.fullName}`)
      if (result.warnings.length) api.warning(result.warnings.join('; '))
      onChanged?.()
    } catch (err) {
      if (!mounted.current) return
      setData(original)
      setActionError({ shiftId: card.shift.id, message: getApiErrorMessage(err, 'Chưa xác nhận được thao tác; hãy kiểm tra lịch đã tải lại') })
      // A network timeout is ambiguous. Reconcile with persisted data even after a visual rollback.
      try { const current = await getScheduleBoard(periodId); if (mounted.current) setData(current) } catch { /* Keep last known state and show the error. */ }
    } finally {
      saving.current = false
      if (mounted.current) { setPending(false); cancelSelection() }
    }
  }
  function drop(event, card) {
    event.preventDefault(); event.stopPropagation()
    if (!event.dataTransfer.types.includes(transferType)) return
    try { if (JSON.parse(event.dataTransfer.getData(transferType)).periodId !== periodId) return } catch { return }
    place(card)
  }
  function datesChanged(info) {
    const zone = data?.timezone || 'Asia/Ho_Chi_Minh'
    const start = info.startStr?.slice(0, 10) || branchDay(info.start, zone)
    const end = info.endStr?.slice(0, 10) || branchDay(info.end, zone)
    const endDate = new Date(`${end}T12:00:00Z`)
    endDate.setUTCDate(endDate.getUTCDate() - 1)
    const format = new Intl.DateTimeFormat('vi', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' })
    const title = info.view.type === 'dayGridWeek' ? `${format.format(new Date(`${start}T12:00:00Z`))} — ${format.format(endDate)}` : info.view.title
    setRange({ start, end, title })
  }
  function changeView(value) { setView(value); calendar.current?.getApi().changeView(value) }

  function personCard(person) {
    const hours = loads.get(person.id) || 0
    const chosen = selected?.person.id === person.id && !selected.source
    return <button key={person.id} type="button" className={`roster-person ${chosen ? 'is-selected' : ''}`}
      data-employee-id={person.id} draggable={editable && !pending} disabled={!editable || pending}
      onDragStart={e => startDrag(e, person)} onDragEnd={() => setHover(null)} onClick={() => choose(person)}
      aria-label={`Chọn ${person.fullName} để phân công`}>
      <span className={`roster-avatar is-${avatarColor(person.id)}`}>{initials(person.fullName)}</span>
      <span className="roster-person-copy"><strong>{person.fullName}</strong><small>{person.positionName}</small>
        <span className="roster-load"><i style={{ width: `${Math.min(100, hours / Number(person.maxHoursPerWeek || 40) * 100)}%` }} /></span>
        <small><b>{Number(hours.toFixed(2))}h</b> đang hiển thị · {person.maxHoursPerWeek}h/tuần tối đa</small>
      </span><HolderOutlined className="roster-grip" />
    </button>
  }
  const peoplePanel = <>
    <div className="roster-people-heading"><span className="roster-mini-icon"><TeamOutlined /></span><div><h3>Đội ngũ của bạn</h3><p>{data?.employees.length || 0} nhân viên sẵn sàng tra cứu</p></div></div>
    <Input aria-label="Tìm nhân viên trên lịch" prefix={<SearchOutlined />} placeholder="Tìm tên, mã nhân viên…" value={search} onChange={e => setSearch(e.target.value)} allowClear />
    <Select aria-label="Lọc vị trí nhân viên trên lịch" allowClear value={position} placeholder="Tất cả vị trí" onChange={setPosition} options={positions.map(([value, label]) => ({ value, label }))} />
    <div className="roster-people-tip"><HolderOutlined /><span>Kéo nhân viên vào một ca, hoặc chọn người rồi bấm <b>Phân công</b> trên ca.</span></div>
    <div className="roster-people-list">{people.slice(0, 60).map(personCard)}{people.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không tìm thấy nhân viên" />}</div>
    {people.length > 60 && <small className="roster-more-people">Đang hiện 60/{people.length}. Tìm theo tên để thu hẹp.</small>}
  </>

  function eventContent(info) {
    const card = info.event.extendedProps.card
    const shift = card.shift
    const assigned = card.assignments.length
    const minimum = card.requirements.reduce((sum, r) => sum + r.minEmployees, 0)
    const missing = card.requirements.reduce((sum, r) => sum + Math.max(0, r.minEmployees - card.assignments.filter(a => a.positionId === r.positionId).length), 0)
    const state = shift.status === 'CANCELLED' ? 'cancelled' : minimum === 0 ? 'unconfigured' : missing ? 'shortage' : 'filled'
    const current = hover?.id === shift.id ? hover : null
    const selectedTarget = current ? current.loading ? 'checking' : current.eligible ? 'valid' : 'invalid' : ''
    return <article className={`roster-shift is-${state} ${selectedTarget ? `drop-${selectedTarget}` : ''} ${actionError?.shiftId === shift.id ? 'drop-invalid' : ''}`}
      data-shift-id={shift.id} data-testid={`calendar-shift-${shift.id}`} aria-label={`${shift.shiftTemplateName} ${shift.workDate} ${time(shift.startTime)}`}
      onDragOver={e => { if (selected && card.editable && !pending) { e.preventDefault(); e.dataTransfer.dropEffect = selected.source ? 'move' : 'copy' } }}
      onDragEnter={e => { if (!e.currentTarget.contains(e.relatedTarget)) preview(card) }} onDrop={e => drop(e, card)}>
      <div className="roster-shift-top"><span className="roster-shift-name" title={shift.shiftTemplateName}><i style={{ background: shift.colorCode || '#7665df' }} />{shift.shiftTemplateName}</span>
        <span className={`roster-state-dot is-${state}`} title={state === 'shortage' ? `Thiếu ${missing} người` : state === 'filled' ? 'Đủ nhân sự tối thiểu' : 'Chưa sẵn sàng'} /></div>
      <button className="roster-shift-time" onClick={() => onOpenShift(shift)} type="button">{time(shift.startTime)}<span>—</span>{time(shift.endTime)}{shift.overnight && <sup>+1</sup>}</button>
      <div className="roster-shift-meta"><span>{Number(netHours(shift).toFixed(2))}h làm</span><span>{assigned}/{minimum} người</span></div>
      <div className="roster-role-line" title={card.requirements.map(r => `${r.positionName}: ${r.assignedEmployees}/${r.minEmployees}`).join(' · ')}>{card.requirements.map(r => r.positionName).join(' · ') || 'Chưa có nhu cầu'}</div>
      <div className="roster-assignees">{card.assignments.map(person => <Tooltip key={person.id} title={`${person.fullName} · ${person.positionName}${person.source === 'AUTO' ? ' · Xếp tự động' : ''}`}>
        <button type="button" className={`roster-assignee ${person.pending ? 'is-pending' : ''}`} data-assignment-id={person.id}
          draggable={editable && person.editable && !pending} disabled={!editable || !person.editable || pending}
          onDragStart={e => { e.stopPropagation(); startDrag(e, { ...person, id: person.userId }, { ...person, workShiftId: shift.id }) }}
          onDragEnd={() => setHover(null)} onClick={() => choose({ ...person, id: person.userId }, { ...person, workShiftId: shift.id })}
          aria-label={`Chuyển ${person.fullName} từ ca ${time(shift.startTime)}`}>
          <span className={`roster-avatar roster-avatar-small is-${avatarColor(person.userId)}`}>{initials(person.fullName)}</span><span>{person.fullName}</span>{person.pending ? <ClockCircleOutlined spin /> : <HolderOutlined />}
        </button></Tooltip>)}</div>
      {!assigned && <div className="roster-unassigned"><UserOutlined /><span>Chưa phân công</span></div>}
      {current && <div className={`roster-preview ${current.eligible ? 'is-valid' : ''}`} role="status">
        {current.loading ? 'Đang kiểm tra…' : current.eligible ? <><CheckCircleOutlined /> Hợp lệ · {current.projectedDailyHours}h/ngày{current.warnings?.length > 0 && <small>{current.warnings[0]}</small>}</> : <><WarningOutlined /> {current.violations?.join('; ')}</>}
      </div>}
      <button type="button" className={`roster-drop-action ${selected && card.editable ? 'has-selection' : ''}`} disabled={!card.editable || pending}
        onMouseEnter={() => preview(card)} onFocus={() => preview(card)} onClick={() => selected ? place(card) : onOpenShift(shift)}>
        {!card.editable ? <><LockOutlined /> Chỉ xem</> : <><PlusOutlined /> {selected ? selected.source ? 'Chuyển vào ca' : 'Phân công' : 'Thêm nhân viên'}</>}
      </button>
    </article>
  }

  if (error) return <Alert type="error" title={error} action={<Button onClick={() => setRetry(v => v + 1)}>Thử lại</Button>} />
  if (!data) return <div className="roster-loading"><Skeleton active paragraph={{ rows: 10 }} /></div>
  return <section className={`roster-board ${pending ? 'is-saving' : ''}`} aria-busy={pending}>
    {context}
    <div className="roster-overview">
      <div className="roster-stat"><span className="roster-stat-icon is-purple"><CalendarOutlined /></span><div><small>CA LÀM TRONG KỲ</small><strong>{active.length}<span>ca</span></strong></div></div>
      <div className="roster-stat"><span className="roster-stat-icon is-blue"><TeamOutlined /></span><div><small>NHÂN VIÊN ĐÃ XẾP</small><strong>{engaged}<span>/{data.employees.length} người</span></strong></div></div>
      <div className="roster-stat"><span className="roster-stat-icon is-teal"><CheckCircleOutlined /></span><div><small>PHÂN CÔNG HIỆN CÓ</small><strong>{stats.assigned}<span>/{stats.required} tối thiểu</span></strong></div></div>
      <div className="roster-stat"><span className={`roster-stat-icon ${stats.missing ? 'is-orange' : 'is-teal'}`}><WarningOutlined /></span><div><small>VỊ TRÍ CÒN THIẾU</small><strong className={stats.missing ? 'roster-stat-warning' : ''}>{stats.missing}<span>cần bổ sung</span></strong></div></div>
    </div>
    {!editable && <Alert type="info" showIcon title="Lịch đã công bố hoặc khóa — chế độ xem" description="Các thao tác kéo thả chỉ áp dụng cho ca tương lai trong kỳ Nháp. Thay đổi lịch chính thức qua quy trình đổi/nhường ca." />}
    {actionError && <Alert type="error" showIcon closable onClose={() => setActionError(null)} title="Chưa lưu được phân công" description={actionError.message} />}
    {selected && <div className="roster-selection" role="status"><span className={`roster-avatar roster-avatar-small is-${avatarColor(selected.person.id)}`}>{initials(selected.person.fullName)}</span><span><b>{selected.person.fullName}</b> · {selected.source ? 'Chọn ca đích để chuyển' : 'Chọn ca để phân công'}</span><Button type="text" aria-label="Hủy chọn nhân viên" icon={<CloseOutlined />} disabled={pending} onClick={cancelSelection} /></div>}
    <div className="roster-layout">
      <aside className="roster-people">{peoplePanel}</aside>
      <div className="roster-calendar-shell">
        <div className="roster-calendar-toolbar"><div className="roster-calendar-title"><span className="roster-calendar-symbol"><CalendarOutlined /></span><div><h2>{range?.title || data.periodName}</h2><p>{data.locationName}<span>·</span>{data.timezone}<span className={`roster-period-badge ${editable ? 'is-draft' : ''}`}>{editable ? 'Bản nháp' : data.periodStatus === 'PUBLISHED' ? 'Đã công bố' : 'Đã khóa'}</span></p></div></div>
          <div className="roster-calendar-controls"><Button className="roster-people-mobile" icon={<TeamOutlined />} onClick={() => setPeopleOpen(true)}>Nhân viên</Button><Button size="small" onClick={() => calendar.current?.getApi().gotoDate(data.startDate)}>Đầu kỳ</Button><Button size="small" aria-label="Khoảng trước" icon={<ArrowLeftOutlined />} disabled={range && range.start <= data.startDate} onClick={() => calendar.current?.getApi().prev()} /><Button size="small" aria-label="Khoảng tiếp" icon={<ArrowRightOutlined />} disabled={range && range.end > data.endDate} onClick={() => calendar.current?.getApi().next()} /><Segmented size="small" value={view} onChange={changeView} options={[{ label: 'Ngày', value: 'dayGridDay' }, { label: 'Tuần', value: 'dayGridWeek' }, { label: 'Tháng', value: 'dayGridMonth' }]} /><Button type="text" aria-label="Làm mới lịch trực quan" icon={<ReloadOutlined spin={pending} />} disabled={pending} onClick={() => setRetry(v => v + 1)} /></div>
        </div>
        <div className="roster-legend"><span><i className="is-filled" />Đủ người</span><span><i className="is-shortage" />Thiếu người</span><span><i className="is-cancelled" />Đã hủy</span><small>{pending ? 'Đang lưu thay đổi…' : editable ? 'Kéo thẻ nhân viên giữa các ca để chuyển phân công' : 'Bản lịch chính thức'}</small></div>
        <div className={`roster-calendar is-${view}`}>
          <FullCalendar ref={calendar} plugins={[dayGridPlugin, monarchTheme]} locale={vi} timeZone={data.timezone}
            initialView={view} initialDate={data.startDate} headerToolbar={false} firstDay={1} height="auto"
            validRange={{ start: data.startDate, end: nextDay(data.endDate) }} fixedWeekCount={false}
            events={cards.map(card => ({ id: String(card.shift.id), title: `${time(card.shift.startTime)} ${card.shift.shiftTemplateName}`, start: card.shift.workDate, allDay: true, extendedProps: { card } }))}
            eventContent={eventContent} eventClass="roster-calendar-event" eventInnerClass="roster-calendar-event-inner" eventOrder="title"
            dayHeaderClass="roster-calendar-day-header" dayCellClass="roster-calendar-day"
            dayMaxEvents={false} displayEventTime={false} datesSet={datesChanged}
            dayHeaderContent={info => <div className="roster-day-heading"><span>{info.weekdayText || info.text}</span>{view !== 'dayGridMonth' && <strong>{info.dayNumberText}</strong>}</div>} />
          {cards.length === 0 && <div className="roster-no-shifts"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có ca phù hợp. Tạo ca hoặc đổi bộ lọc để bắt đầu." /></div>}
        </div>
        <div className="roster-calendar-footer"><span><CheckCircleOutlined /> Kiểm tra lịch rảnh, vị trí, giờ làm và giờ nghỉ khi lưu</span><span>{data.periodName}</span></div>
      </div>
    </div>
    <Drawer title="Nhân viên để phân công" open={peopleOpen} onClose={() => setPeopleOpen(false)} width={330}><div className="roster-people roster-people-drawer">{peoplePanel}</div></Drawer>
  </section>
}
