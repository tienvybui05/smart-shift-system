package com.smartshift.service;

import com.smartshift.dto.assignment.*;
import com.smartshift.dto.schedule.ScheduleBoardResponse;
import com.smartshift.entity.*;
import com.smartshift.enums.*;
import com.smartshift.exception.*;
import com.smartshift.mapper.WorkShiftMapper;
import com.smartshift.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.smartshift.service.ScheduleAuditSnapshots.assignment;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ScheduleBoardService {
    private static final List<AssignmentStatus> ACTIVE=List.of(AssignmentStatus.ASSIGNED,AssignmentStatus.CONFIRMED);
    private final SchedulePeriodRepository periods;
    private final WorkShiftRepository shifts;
    private final ShiftRequirementRepository requirements;
    private final ShiftAssignmentRepository assignments;
    private final UserRepository users;
    private final AttendanceRepository attendances;
    private final SchedulingAccessService access;
    private final AssignmentConstraintService constraints;
    private final ShiftAssignmentService assignmentService;
    private final ScheduleAuditService audit;
    private final WorkShiftMapper mapper;
    private final Clock clock;
    @PersistenceContext private EntityManager entityManager;

    public ScheduleBoardResponse board(String username,Long periodId) {
        access.requireSchedulePeriod(username,periodId);
        SchedulePeriod period=period(periodId);
        var work=shifts.search(periodId,null);
        var ids=work.stream().map(WorkShift::getId).toList();
        var req=ids.isEmpty() ? List.<ShiftRequirement>of() : requirements.findAllByWorkShiftIds(ids);
        var assigned=ids.isEmpty() ? List.<ShiftAssignment>of() : assignments.findAllByWorkShiftIdsAndStatusIn(ids,ACTIVE);
        var byShift=assigned.stream().collect(Collectors.groupingBy(a -> a.getWorkShift().getId()));
        Set<Long> recorded=new HashSet<>(attendances.findAssignmentIdsForCalendar(periodId));
        List<ScheduleBoardResponse.Card> cards=new ArrayList<>();
        for(var shift:work) {
            var roster=byShift.getOrDefault(shift.getId(),List.of());
            List<AssignmentRequirementProgressResponse> progress=req.stream().filter(r -> r.getWorkShift().getId().equals(shift.getId()))
                .map(r -> {
                    int count=(int)roster.stream().filter(a -> a.getPosition().getId().equals(r.getPosition().getId())).count();
                    return new AssignmentRequirementProgressResponse(r.getId(),r.getPosition().getId(),r.getPosition().getCode(),
                        r.getPosition().getName(),r.getMinEmployees(),r.getMaxEmployees(),count,count>=r.getMinEmployees(),count>=r.getMaxEmployees());
                }).toList();
            boolean editable=editable(period,shift);
            cards.add(new ScheduleBoardResponse.Card(mapper.toResponse(shift),editable,progress,roster.stream().map(a ->
                new ScheduleBoardResponse.Assignment(a.getId(),a.getUser().getId(),a.getUser().getEmployeeCode(),a.getUser().getFullName(),
                    a.getPosition().getId(),a.getPosition().getName(),a.getAssignmentSource(),a.getVersion(),editable&&!recorded.contains(a.getId()))).toList()));
        }
        var employees=users.findCalendarEmployees(period.getLocation().getId()).stream().map(u -> new ScheduleBoardResponse.Employee(u.getId(),
            u.getEmployeeCode(),u.getFullName(),u.getPosition().getId(),u.getPosition().getName(),u.getMaxHoursPerDay(),u.getMaxHoursPerWeek(),u.getMinRestHours())).toList();
        return new ScheduleBoardResponse(periodId,period.getName(),period.getStatus(),period.getStartDate(),period.getEndDate(),
            period.getLocation().getId(),period.getLocation().getName(),period.getLocation().getTimezone(),clock.instant(),cards,employees);
    }

    public CalendarPlacementPreview preview(String username,Long periodId,CalendarPlacementRequest body) {
        authorize(username,periodId,body);
        return evaluate(periodId,body).preview;
    }

    @Transactional
    public CalendarPlacementResponse place(String username,Long periodId,CalendarPlacementRequest body) {
        authorize(username,periodId,body);
        periods.lockCalendarPeriod(periodId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch"));
        // Match ordinary manual assignment's lock order: period -> shifts -> employee.
        Set<Long> shiftIds=new TreeSet<>(); shiftIds.add(body.targetWorkShiftId());
        if(body.sourceAssignmentId()!=null) assignments.findWorkShiftIdByAssignmentId(body.sourceAssignmentId()).ifPresent(shiftIds::add);
        for(Long id:shiftIds) shifts.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Ca vừa bị thay đổi"));
        users.lockAttendanceEmployee(body.userId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
        // Permission checks can populate the first-level cache. No mutations occur before this clear.
        entityManager.clear();
        Context context=evaluate(periodId,body);
        if(!context.preview.eligible()) throw new BusinessRuleException(String.join("; ",context.preview.violations()));
        ShiftAssignment saved;
        Long sourceId=null;
        if(context.source==null) {
            assignmentService.assignEmployee(body.targetWorkShiftId(),new ShiftAssignmentRequest(body.userId(),body.reason()),username);
            saved=assignments.findByWorkShiftIdAndUserId(body.targetWorkShiftId(),body.userId()).orElseThrow();
        } else {
            saved=context.source;
            WorkShift source=saved.getWorkShift(); sourceId=source.getId();
            var before=assignment(saved);
            saved.setWorkShift(context.target); saved.setPosition(context.employee.getPosition());
            saved.setStatus(AssignmentStatus.ASSIGNED); saved.setAssignmentSource(AssignmentSource.MANUAL); saved.setScore(null);
            saved.setAssignedBy(users.findByUsername(username).orElseThrow()); saved.setNote(body.reason().trim());
            assignments.saveAndFlush(saved);
            refresh(source); refresh(context.target);
            audit.record(username,context.target.getSchedulePeriod(),context.target,ScheduleAuditAction.UPDATED,
                ScheduleAuditTargetType.SHIFT_ASSIGNMENT,saved.getId(),body.reason(),before,assignment(saved));
            audit.record(username,source.getSchedulePeriod(),source,ScheduleAuditAction.UPDATED,
                ScheduleAuditTargetType.SHIFT_ASSIGNMENT,saved.getId(),body.reason(),before,assignment(saved));
        }
        return new CalendarPlacementResponse(saved.getId(),body.userId(),sourceId,context.target.getId(),saved.getVersion(),context.preview.warnings());
    }

    private void authorize(String username,Long periodId,CalendarPlacementRequest body) {
        access.requireSchedulePeriod(username,periodId);
        access.requireWorkShift(username,body.targetWorkShiftId());
        if(body.sourceAssignmentId()!=null) access.requireAssignment(username,body.sourceAssignmentId());
    }
    private Context evaluate(Long periodId,CalendarPlacementRequest body) {
        SchedulePeriod period=period(periodId);
        WorkShift target=shifts.findById(body.targetWorkShiftId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca đích"));
        User employee=users.findById(body.userId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
        if(!target.getSchedulePeriod().getId().equals(periodId)) throw new BusinessRuleException("Chỉ kéo thả trong cùng một kỳ lịch");
        if(employee.getLocation()==null || !employee.getLocation().getId().equals(period.getLocation().getId())
                || employee.getRole()==null || !"ROLE_EMPLOYEE".equals(employee.getRole().getName()) || !employee.isActive())
            throw new BusinessRuleException("Chỉ phân công nhân viên đang hoạt động trong chi nhánh của kỳ lịch");
        List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();
        if(!editable(period,target)) errors.add("Chỉ phân công ca tương lai đang mở/đủ người trong kỳ Nháp");
        ShiftAssignment source=null;
        if(body.sourceAssignmentId()!=null) {
            source=assignments.findById(body.sourceAssignmentId()).orElseThrow(() -> new ResourceNotFoundException("Phân công nguồn không còn tồn tại"));
            if(body.sourceWorkShiftId()==null || body.expectedVersion()==null || !source.getWorkShift().getId().equals(body.sourceWorkShiftId())
                    || source.getVersion()!=body.expectedVersion()) throw new DuplicateResourceException("Phân công vừa thay đổi; hãy tải lại lịch trước khi chuyển");
            if(!source.getUser().getId().equals(body.userId()) || !source.getWorkShift().getSchedulePeriod().getId().equals(periodId))
                throw new BusinessRuleException("Nhân viên hoặc kỳ lịch không khớp phân công nguồn");
            if(!ACTIVE.contains(source.getStatus()) || !editable(period,source.getWorkShift())) errors.add("Phân công nguồn không còn được chỉnh sửa");
            if(source.getWorkShift().getId().equals(target.getId())) errors.add("Nhân viên đang nằm trong ca này");
            if(attendances.existsByShiftAssignmentId(source.getId())) errors.add("Phân công đã có công, không thể chuyển sang ca khác");
            var sourceReq=requirements.findByWorkShiftIdAndPositionId(source.getWorkShift().getId(),source.getPosition().getId());
            if(sourceReq.isPresent() && assignments.countByWorkShiftIdAndPositionIdAndStatusIn(source.getWorkShift().getId(),source.getPosition().getId(),ACTIVE)-1<sourceReq.get().getMinEmployees())
                warnings.add("Ca nguồn sẽ thiếu người sau khi chuyển; cần bổ sung trước công bố");
        }
        var requirement=employee.getPosition()==null ? Optional.<ShiftRequirement>empty()
            : requirements.findByWorkShiftIdAndPositionId(target.getId(),employee.getPosition().getId());
        if(requirement.isEmpty()) errors.add("Vị trí của nhân viên không nằm trong nhu cầu ca đích");
        else if(assignments.countByWorkShiftIdAndPositionIdAndStatusIn(target.getId(),employee.getPosition().getId(),ACTIVE)>=requirement.get().getMaxEmployees())
            errors.add("Vị trí này đã đạt số nhân viên tối đa");
        if(assignments.findByWorkShiftIdAndUserId(target.getId(),employee.getId()).isPresent()) errors.add("Nhân viên đã có phân công trong ca đích");
        BigDecimal daily=BigDecimal.ZERO,weekly=BigDecimal.ZERO;
        if(employee.getPosition()!=null) {
            var result=constraints.evaluate(employee,target,employee.getPosition(),source==null ? Set.of() : Set.of(source.getId()));
            errors.addAll(result.violations()); daily=result.projectedDailyHours(); weekly=result.projectedWeeklyHours();
            if(exceedsConsecutiveDays(employee,target,source)) errors.add("Vượt số ngày làm liên tiếp tối đa của nhân viên");
        }
        return new Context(target,employee,source,new CalendarPlacementPreview(errors.isEmpty(),errors,warnings,daily,weekly));
    }
    private boolean exceedsConsecutiveDays(User employee,WorkShift target,ShiftAssignment source) {
        int maximum=employee.getMaxConsecutiveDays();
        ZoneId zone=ZoneId.of(target.getSchedulePeriod().getLocation().getTimezone());
        LocalDate day=target.getStartAt().atZone(zone).toLocalDate();
        var work=assignments.findActiveAssignmentsInRange(employee.getId(),ACTIVE,
            day.minusDays(maximum).atStartOfDay(zone).toInstant(),day.plusDays(maximum+1).atStartOfDay(zone).toInstant());
        Set<LocalDate> days=new HashSet<>(); days.add(day);
        for(var a:work) if((source==null || !a.getId().equals(source.getId())) && a.getWorkShift().getStatus()!=WorkShiftStatus.CANCELLED)
            days.add(a.getWorkShift().getStartAt().atZone(zone).toLocalDate());
        int count=1;
        for(LocalDate d=day.minusDays(1);days.contains(d);d=d.minusDays(1)) count++;
        for(LocalDate d=day.plusDays(1);days.contains(d);d=d.plusDays(1)) count++;
        return count>maximum;
    }
    private boolean editable(SchedulePeriod period,WorkShift shift) {
        return period.getStatus()==SchedulePeriodStatus.DRAFT && List.of(WorkShiftStatus.OPEN,WorkShiftStatus.FILLED).contains(shift.getStatus())
            && shift.getStartAt().isAfter(clock.instant());
    }
    private void refresh(WorkShift shift) {
        var req=requirements.findAllByWorkShiftId(shift.getId());
        boolean filled=req.stream().mapToInt(ShiftRequirement::getMinEmployees).sum()>0 && req.stream().allMatch(r ->
            assignments.countByWorkShiftIdAndPositionIdAndStatusIn(shift.getId(),r.getPosition().getId(),ACTIVE)>=r.getMinEmployees());
        shift.setStatus(filled ? WorkShiftStatus.FILLED : WorkShiftStatus.OPEN); shifts.saveAndFlush(shift);
    }
    private SchedulePeriod period(Long id) {
        return periods.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch"));
    }
    private record Context(WorkShift target,User employee,ShiftAssignment source,CalendarPlacementPreview preview) {}
}
