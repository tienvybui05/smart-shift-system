package com.smartshift.service;

import com.smartshift.entity.*;
import com.smartshift.exception.*;
import com.smartshift.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;

@Service @RequiredArgsConstructor
public class AttendanceIntegrityService {
    private final UserRepository users;
    private final ShiftAssignmentRepository assignments;
    private final PayrollRecordRepository payroll;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** Shared lock order: employee -> assignment -> attendance -> payroll rows. */
    public ShiftAssignment lockAssignment(Long id) {
        ShiftAssignment read = assignments.findDetailedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công ca"));
        users.lockAttendanceEmployee(read.getUser().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
        ShiftAssignment locked = assignments.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công ca"));
        Long expectedEmployee = read.getUser().getId();
        // A prior permission lookup may already have put this assignment in the first-level cache.
        entityManager.refresh(locked, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!expectedEmployee.equals(locked.getUser().getId())) {
            throw new BusinessRuleException("Phân công vừa thay đổi nhân viên; hãy tải lại");
        }
        return locked;
    }

    public void requireMutable(ShiftAssignment assignment) {
        LocalDate[] dates = dates(assignment);
        if (payroll.hasConfirmedOverlap(assignment.getUser().getId(), dates[0], dates[1])) {
            throw new BusinessRuleException("Công thuộc kỳ lương đã xác nhận, không thể thay đổi");
        }
    }

    public void markPayrollStale(ShiftAssignment assignment) {
        LocalDate[] dates = dates(assignment);
        payroll.markDraftSourceChanged(assignment.getUser().getId(), dates[0], dates[1]);
    }

    private LocalDate[] dates(ShiftAssignment assignment) {
        WorkShift shift=assignment.getWorkShift();
        ZoneId zone=ZoneId.of(shift.getSchedulePeriod().getLocation().getTimezone());
        return new LocalDate[]{shift.getStartAt().atZone(zone).toLocalDate(),
            shift.getEndAt().minusNanos(1).atZone(zone).toLocalDate()};
    }
}

