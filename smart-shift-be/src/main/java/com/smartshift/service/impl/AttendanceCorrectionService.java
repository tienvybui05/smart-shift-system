package com.smartshift.service.impl;

import com.smartshift.dto.attendance.*;
import com.smartshift.entity.*;
import com.smartshift.enums.*;
import com.smartshift.exception.*;
import com.smartshift.repository.*;
import com.smartshift.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AttendanceCorrectionService {
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceRepository attendances;
    private final ShiftAssignmentRepository assignments;
    private final UserRepository users;
    private final SchedulingAccessService access;
    private final AttendanceIntegrityService integrity;
    private final AttendanceHistoryService history;
    private final AttendanceServiceImpl attendanceLogic;
    private final NotificationService notifications;
    private final Clock clock;

    public List<AttendanceCorrectionResponse> list(String username, boolean mine, Long location,
            AttendanceCorrectionStatus status, LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start) || ChronoUnit.DAYS.between(start,end)>=93) {
            throw new BusinessRuleException("Chọn khoảng ngày hợp lệ, tối đa 93 ngày");
        }
        Long scope=mine ? null : access.resolveLocationFilter(username,location);
        // Query a broad UTC envelope, then filter each assignment in its own branch timezone.
        return corrections.search(mine ? username : null,scope,status,
                start.atStartOfDay(ZoneOffset.ofHours(14)).toInstant(),
                end.plusDays(1).atStartOfDay(ZoneOffset.ofHours(-12)).toInstant()).stream()
            .filter(c -> { LocalDate day=workDate(c.getShiftAssignment()); return !day.isBefore(start)&&!day.isAfter(end); })
            .map(this::response).toList();
    }

    @Transactional
    public AttendanceCorrectionResponse create(String username, AttendanceCorrectionRequest body) {
        ShiftAssignment preliminary=findAssignment(body.shiftAssignmentId());
        requireOwner(preliminary,username);
        User requester=preliminary.getUser();
        if (!"ROLE_EMPLOYEE".equals(requester.getRole().getName())) throw new AccessDeniedException("Chỉ nhân viên gửi yêu cầu công");
        ShiftAssignment assignment=integrity.lockAssignment(preliminary.getId());
        requireOwner(assignment,username);
        validateAssignment(assignment);
        validateTimes(assignment,body.checkInAt(),body.checkOutAt(),body.breakMinutes());
        integrity.requireMutable(assignment);
        if (corrections.existsByShiftAssignmentIdAndStatus(assignment.getId(),AttendanceCorrectionStatus.PENDING)) {
            throw new DuplicateResourceException("Ca này đã có yêu cầu điều chỉnh đang chờ");
        }
        Attendance before=attendances.findDetailedByShiftAssignmentIdForUpdate(assignment.getId()).orElse(null);
        AttendanceCorrection row=new AttendanceCorrection();
        row.setShiftAssignment(assignment); row.setRequester(assignment.getUser());
        row.setProposedCheckInAt(body.checkInAt()); row.setProposedCheckOutAt(body.checkOutAt());
        row.setProposedBreakMinutes(body.breakMinutes()); row.setReason(body.reason().trim());
        row.setStatus(AttendanceCorrectionStatus.PENDING);
        row.setBaselineAttendanceId(before==null ? null : before.getId());
        row.setBaselineUpdatedAt(before==null ? null : before.getUpdatedAt());
        row.setBeforeData(history.snapshot(before));
        corrections.saveAndFlush(row);
        notifications.createNotifications(users.findActiveNotificationReviewersForLocation(
                assignment.getWorkShift().getSchedulePeriod().getLocation().getId()),
            NotificationType.ATTENDANCE_CORRECTION_CREATED,"Có yêu cầu điều chỉnh công",
            requester.getFullName()+" gửi yêu cầu #"+row.getId()+": "+row.getReason(),
            NotificationReferenceType.ATTENDANCE_CORRECTION,row.getId());
        return response(row);
    }

    @Transactional
    public AttendanceCorrectionResponse cancel(String username, Long id) {
        Long assignmentId=reference(id);
        requireOwner(findAssignment(assignmentId),username);
        integrity.lockAssignment(assignmentId);
        AttendanceCorrection row=lock(id);
        requireOwner(row.getShiftAssignment(),username);
        requirePending(row); row.setStatus(AttendanceCorrectionStatus.CANCELLED);
        return response(corrections.saveAndFlush(row));
    }

    @Transactional
    public AttendanceCorrectionResponse review(String username, Long id, AttendanceCorrectionReview body) {
        Long assignmentId=reference(id);
        access.requireAssignment(username,assignmentId);
        User reviewer=users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người duyệt"));
        ShiftAssignment assignment=integrity.lockAssignment(assignmentId);
        AttendanceCorrection row=lock(id);
        if (reviewer.getId().equals(row.getRequester().getId())) throw new BusinessRuleException("Không được tự duyệt yêu cầu");
        requirePending(row);
        if (body.approved()) {
            validateAssignment(assignment);
            validateTimes(assignment,row.getProposedCheckInAt(),row.getProposedCheckOutAt(),row.getProposedBreakMinutes());
            integrity.requireMutable(assignment);
            Attendance attendance=attendances.findDetailedByShiftAssignmentIdForUpdate(assignment.getId()).orElse(null);
            if (!Objects.equals(row.getBaselineAttendanceId(),attendance==null ? null : attendance.getId())
                    || !Objects.equals(row.getBaselineUpdatedAt(),attendance==null ? null : attendance.getUpdatedAt())) {
                throw new BusinessRuleException("Dữ liệu công đã thay đổi sau khi gửi đơn; hãy từ chối và yêu cầu gửi lại");
            }
            String before=history.snapshot(attendance);
            if (attendance==null) {
                attendance=new Attendance(); attendance.setShiftAssignment(assignment);
                attendance.setUnverifiedDevice(true); attendance.setUnverifiedWifi(true);
            }
            attendanceLogic.applyApproval(new AttendanceApprovalRequest(row.getProposedCheckInAt(),
                row.getProposedCheckOutAt(),row.getProposedBreakMinutes(),body.note()),attendance);
            attendance.setApprovedBy(reviewer); attendance.setApprovedAt(clock.instant());
            attendance.setNote("Điều chỉnh #"+row.getId()+": "+body.note().trim());
            attendances.saveAndFlush(attendance);
            integrity.markPayrollStale(assignment);
            history.record(attendance,before,reviewer,"CORRECTION_APPROVED",
                row.getReason());
        }
        row.setStatus(body.approved() ? AttendanceCorrectionStatus.APPROVED : AttendanceCorrectionStatus.REJECTED);
        row.setReviewedBy(reviewer); row.setReviewedAt(clock.instant()); row.setReviewNote(body.note().trim());
        corrections.saveAndFlush(row);
        notifications.createNotification(row.getRequester(),
            body.approved() ? NotificationType.ATTENDANCE_CORRECTION_APPROVED : NotificationType.ATTENDANCE_CORRECTION_REJECTED,
            body.approved() ? "Yêu cầu điều chỉnh công đã duyệt" : "Yêu cầu điều chỉnh công bị từ chối",
            "Yêu cầu #"+row.getId()+": "+row.getReviewNote(),NotificationReferenceType.ATTENDANCE_CORRECTION,row.getId());
        return response(row);
    }

    public List<AttendanceHistoryResponse> history(String username, Long assignmentId, boolean mine) {
        ShiftAssignment assignment=findAssignment(assignmentId);
        if (mine) requireOwner(assignment,username); else access.requireAssignment(username,assignmentId);
        return history.list(assignmentId);
    }

    private void validateAssignment(ShiftAssignment assignment) {
        WorkShift shift=assignment.getWorkShift();
        if (!List.of(AssignmentStatus.ASSIGNED,AssignmentStatus.CONFIRMED).contains(assignment.getStatus())
                || shift.getStatus()==WorkShiftStatus.CANCELLED
                || !List.of(SchedulePeriodStatus.PUBLISHED,SchedulePeriodStatus.LOCKED).contains(shift.getSchedulePeriod().getStatus())) {
            throw new BusinessRuleException("Chỉ điều chỉnh ca được phân công trong lịch đã công bố");
        }
        Instant now=clock.instant();
        if (shift.getEndAt().isAfter(now)) throw new BusinessRuleException("Chỉ gửi/duyệt điều chỉnh khi ca đã kết thúc");
        if (shift.getEndAt().isBefore(now.minus(30,ChronoUnit.DAYS))) {
            throw new BusinessRuleException("Chỉ điều chỉnh công trong 30 ngày sau khi ca kết thúc");
        }
    }

    private void validateTimes(ShiftAssignment assignment, Instant in, Instant out, short pause) {
        WorkShift shift=assignment.getWorkShift();
        if (!out.isAfter(in) || out.isAfter(clock.instant()) || pause<0 || pause>=Duration.between(in,out).toMinutes()
                || in.isBefore(shift.getStartAt().minus(2,ChronoUnit.HOURS))
                || out.isAfter(shift.getEndAt().plus(12,ChronoUnit.HOURS))) {
            throw new BusinessRuleException("Giờ đề xuất không hợp lệ: vào tối đa 2 giờ trước ca, ra tối đa 12 giờ sau ca và không ở tương lai");
        }
    }

    private void requireOwner(ShiftAssignment assignment,String username) {
        if (!assignment.getUser().getUsername().equals(username)) throw new ResourceNotFoundException("Không tìm thấy phân công ca");
    }
    private void requirePending(AttendanceCorrection row) {
        if (row.getStatus()!=AttendanceCorrectionStatus.PENDING) throw new BusinessRuleException("Yêu cầu đã xử lý hoặc đã hủy");
    }
    private ShiftAssignment findAssignment(Long id) {
        return assignments.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công ca"));
    }
    private Long reference(Long id) {
        // Scalar lookup avoids caching a stale PENDING entity before the employee lock.
        return corrections.findAssignmentId(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu công"));
    }
    private AttendanceCorrection lock(Long id) {
        return corrections.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu công"));
    }
    private LocalDate workDate(ShiftAssignment a) {
        return a.getWorkShift().getStartAt().atZone(ZoneId.of(a.getWorkShift().getSchedulePeriod().getLocation().getTimezone())).toLocalDate();
    }
    private AttendanceCorrectionResponse response(AttendanceCorrection c) {
        ShiftAssignment a=c.getShiftAssignment(); WorkShift s=a.getWorkShift(); Location l=s.getSchedulePeriod().getLocation();
        User u=c.getRequester();
        return new AttendanceCorrectionResponse(c.getId(),a.getId(),u.getId(),u.getEmployeeCode(),u.getFullName(),
            l.getId(),l.getName(),l.getTimezone(),workDate(a),s.getStartAt(),s.getEndAt(),
            c.getProposedCheckInAt(),c.getProposedCheckOutAt(),c.getProposedBreakMinutes(),c.getReason(),c.getStatus(),
            c.getBeforeData(),c.getReviewedBy()==null ? null : c.getReviewedBy().getFullName(),
            c.getReviewedAt(),c.getReviewNote(),c.getCreatedAt());
    }
}

