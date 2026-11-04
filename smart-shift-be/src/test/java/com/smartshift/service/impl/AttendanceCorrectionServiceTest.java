package com.smartshift.service.impl;
import com.smartshift.dto.attendance.*;
import com.smartshift.entity.*;
import com.smartshift.enums.*;
import com.smartshift.exception.*;
import com.smartshift.mapper.AttendanceMapper;
import com.smartshift.repository.*;
import com.smartshift.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceCorrectionServiceTest {
    private final Instant now=Instant.parse("2026-11-04T12:00:00Z");
    @Mock AttendanceCorrectionRepository corrections;
    @Mock AttendanceRepository attendances;
    @Mock ShiftAssignmentRepository assignments;
    @Mock UserRepository users;
    @Mock SchedulingAccessService access;
    @Mock AttendanceIntegrityService integrity;
    @Mock AttendanceHistoryService history;
    @Mock NotificationService notifications;
    AttendanceCorrectionService service;
    ShiftAssignment assignment;
    User employee, reviewer;
    @BeforeEach void setUp() {
        var clock=Clock.fixed(now,ZoneOffset.UTC);
        var logic=new AttendanceServiceImpl(attendances,assignments,users,access,mock(EmployeeDeviceService.class),
            new AttendanceMapper(),clock,integrity,history);
        service=new AttendanceCorrectionService(corrections,attendances,assignments,users,access,integrity,history,logic,notifications,clock);
        Role role=new Role();role.setName("ROLE_EMPLOYEE");
        employee=new User();employee.setId(4L);employee.setUsername("employee");employee.setRole(role);employee.setFullName("Employee");
        reviewer=new User();reviewer.setId(8L);reviewer.setUsername("manager");reviewer.setFullName("Manager");
        Location location=new Location();location.setId(1L);location.setTimezone("Asia/Ho_Chi_Minh");
        SchedulePeriod period=new SchedulePeriod();period.setLocation(location);period.setStatus(SchedulePeriodStatus.PUBLISHED);
        WorkShift shift=new WorkShift();shift.setSchedulePeriod(period);shift.setStatus(WorkShiftStatus.FILLED);
        shift.setStartAt(now.minusSeconds(9*3600));shift.setEndAt(now.minusSeconds(3600));shift.setBreakMinutes((short)30);
        assignment=new ShiftAssignment();assignment.setId(10L);assignment.setUser(employee);assignment.setWorkShift(shift);assignment.setStatus(AssignmentStatus.ASSIGNED);
    }
    private AttendanceCorrectionRequest body() { return new AttendanceCorrectionRequest(10L,assignment.getWorkShift().getStartAt(),
        assignment.getWorkShift().getEndAt(),(short)30,"Quên chấm công"); }
    private void submitStubs() {
        when(assignments.findDetailedById(10L)).thenReturn(Optional.of(assignment));
        when(integrity.lockAssignment(10L)).thenReturn(assignment);
    }
    private AttendanceCorrection pending() {
        var row=new AttendanceCorrection();row.setId(7L);row.setShiftAssignment(assignment);row.setRequester(employee);
        row.setStatus(AttendanceCorrectionStatus.PENDING);row.setReason("Quên chấm công");row.setBeforeData("null");
        row.setProposedCheckInAt(body().checkInAt());row.setProposedCheckOutAt(body().checkOutAt());row.setProposedBreakMinutes((short)30);
        return row;
    }
    private void reviewStubs(AttendanceCorrection row) {
        when(corrections.findAssignmentId(7L)).thenReturn(Optional.of(10L));
        when(users.findByUsername("manager")).thenReturn(Optional.of(reviewer));
        when(integrity.lockAssignment(10L)).thenReturn(assignment);
        when(corrections.findForUpdate(7L)).thenReturn(Optional.of(row));
        lenient().when(corrections.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }
    @Test void submissionOnlyCreatesPendingRequestAndNeverChangesAttendance() {
        submitStubs();
        when(corrections.saveAndFlush(any())).thenAnswer(i->{AttendanceCorrection c=i.getArgument(0);c.setId(7L);return c;});
        var response=service.create("employee",body());
        assertEquals(AttendanceCorrectionStatus.PENDING,response.status());
        verify(attendances,never()).saveAndFlush(any());
        verify(integrity,never()).markPayrollStale(any());
        verify(notifications).createNotifications(any(),eq(NotificationType.ATTENDANCE_CORRECTION_CREATED),any(),any(),any(),eq(7L));
    }
    @Test void refusesAnotherEmployeesAssignmentBeforeLocking() {
        when(assignments.findDetailedById(10L)).thenReturn(Optional.of(assignment));
        assertThrows(ResourceNotFoundException.class,()->service.create("intruder",body()));
        verifyNoInteractions(integrity);
    }
    @Test void duplicatePendingRequestIsRejected() {
        submitStubs();when(corrections.existsByShiftAssignmentIdAndStatus(10L,AttendanceCorrectionStatus.PENDING)).thenReturn(true);
        assertThrows(DuplicateResourceException.class,()->service.create("employee",body()));
        verify(corrections,never()).saveAndFlush(any());
    }
    @Test void cannotSubmitForFutureOrOlderThanThirtyDays() {
        submitStubs();
        assignment.getWorkShift().setEndAt(now.plusSeconds(3600));
        assertThrows(BusinessRuleException.class,()->service.create("employee",body()));
        assignment.getWorkShift().setEndAt(now.minusSeconds(31*86400));
        assertThrows(BusinessRuleException.class,()->service.create("employee",body()));
    }
    @Test void rejectsInvalidProposedTimes() {
        submitStubs();
        var invalid=new AttendanceCorrectionRequest(10L,body().checkInAt(),now.plusSeconds(1),(short)30,"Reason");
        assertThrows(BusinessRuleException.class,()->service.create("employee",invalid));
    }
    @Test void approvalCreatesMissingRecordPreservesUnverifiedFlagsAndRecordsHistory() {
        var row=pending();reviewStubs(row);
        var response=service.review("manager",7L,new AttendanceCorrectionReview(true,"Đã đối soát"));
        assertEquals(AttendanceCorrectionStatus.APPROVED,response.status());
        var captor=ArgumentCaptor.forClass(Attendance.class);
        verify(attendances).saveAndFlush(captor.capture());
        Attendance applied=captor.getValue();
        assertEquals(body().checkInAt(),applied.getCheckInAt());
        assertEquals(body().checkOutAt(),applied.getCheckOutAt());
        assertEquals(reviewer,applied.getApprovedBy());
        assertTrue(applied.isUnverifiedWifi());assertTrue(applied.isUnverifiedDevice());
        assertNull(applied.getCheckInLatitude());
        verify(history).record(eq(applied),any(),eq(reviewer),eq("CORRECTION_APPROVED"),contains("Quên chấm công"));
        verify(integrity).markPayrollStale(assignment);
    }
    @Test void rejectionNeverChangesAttendanceOrPayroll() {
        var row=pending();reviewStubs(row);
        assertEquals(AttendanceCorrectionStatus.REJECTED,service.review("manager",7L,new AttendanceCorrectionReview(false,"Không đủ căn cứ")).status());
        verifyNoInteractions(attendances);
        verify(integrity,never()).markPayrollStale(any());
    }
    @Test void selfReviewAndCrossBranchReviewAreDenied() {
        var row=pending();when(corrections.findAssignmentId(7L)).thenReturn(Optional.of(10L));
        when(users.findByUsername("employee")).thenReturn(Optional.of(employee));
        when(integrity.lockAssignment(10L)).thenReturn(assignment);
        when(corrections.findForUpdate(7L)).thenReturn(Optional.of(row));
        assertThrows(BusinessRuleException.class,()->service.review("employee",7L,new AttendanceCorrectionReview(true,"Self")));
        doThrow(new AccessDeniedException("outside branch")).when(access).requireAssignment("other",10L);
        assertThrows(AccessDeniedException.class,()->service.review("other",7L,new AttendanceCorrectionReview(true,"Other")));
        verify(attendances,never()).saveAndFlush(any());
    }
    @Test void cannotApplyChangedAttendanceOrProcessTwice() {
        var row=pending();row.setBaselineAttendanceId(20L);row.setBaselineUpdatedAt(now.minusSeconds(120));
        reviewStubs(row);
        Attendance changed=new Attendance();changed.setId(20L);changed.setUpdatedAt(now);
        when(attendances.findDetailedByShiftAssignmentIdForUpdate(10L)).thenReturn(Optional.of(changed));
        assertThrows(BusinessRuleException.class,()->service.review("manager",7L,new AttendanceCorrectionReview(true,"Changed")));
        row.setStatus(AttendanceCorrectionStatus.APPROVED);
        assertThrows(BusinessRuleException.class,()->service.review("manager",7L,new AttendanceCorrectionReview(true,"Again")));
        verify(attendances,never()).saveAndFlush(any());
    }
    @Test void confirmedPayrollBlocksApproval() {
        var row=pending();reviewStubs(row);
        doThrow(new BusinessRuleException("confirmed payroll")).when(integrity).requireMutable(assignment);
        assertThrows(BusinessRuleException.class,()->service.review("manager",7L,new AttendanceCorrectionReview(true,"Payroll")));
        verify(attendances,never()).saveAndFlush(any());
    }
    @Test void employeeCanCancelOnlyPendingOwnRequest() {
        var row=pending();
        when(corrections.findAssignmentId(7L)).thenReturn(Optional.of(10L));
        when(assignments.findDetailedById(10L)).thenReturn(Optional.of(assignment));
        when(corrections.findForUpdate(7L)).thenReturn(Optional.of(row));
        when(corrections.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        assertEquals(AttendanceCorrectionStatus.CANCELLED,service.cancel("employee",7L).status());
        assertThrows(BusinessRuleException.class,()->service.cancel("employee",7L));
    }
}

