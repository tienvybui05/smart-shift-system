package com.smartshift.service;

import com.smartshift.dto.assignment.*;
import com.smartshift.entity.*;
import com.smartshift.enums.*;
import com.smartshift.exception.*;
import com.smartshift.mapper.WorkShiftMapper;
import com.smartshift.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ScheduleBoardServiceTest {
    @Mock SchedulePeriodRepository periods;
    @Mock WorkShiftRepository shifts;
    @Mock ShiftRequirementRepository requirements;
    @Mock ShiftAssignmentRepository assignments;
    @Mock UserRepository users;
    @Mock AttendanceRepository attendances;
    @Mock SchedulingAccessService access;
    @Mock AssignmentConstraintService constraints;
    @Mock ShiftAssignmentService assignmentService;
    @Mock ScheduleAuditService audit;
    @Mock WorkShiftMapper mapper;
    @Mock EntityManager entityManager;
    ScheduleBoardService service;
    SchedulePeriod period;
    WorkShift sourceShift,target;
    User employee;
    Position position;
    ShiftAssignment source;
    ShiftRequirement requirement;
    @BeforeEach void setup() {
        Clock clock=Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);
        service=new ScheduleBoardService(periods,shifts,requirements,assignments,users,attendances,access,constraints,assignmentService,audit,mapper,clock);
        ReflectionTestUtils.setField(service,"entityManager",entityManager);
        var location=new Location(); location.setId(1L); location.setTimezone("Asia/Ho_Chi_Minh");
        period=new SchedulePeriod(); period.setId(10L); period.setLocation(location); period.setStatus(SchedulePeriodStatus.DRAFT);
        position=new Position(); position.setId(2L); position.setName("Phục vụ");
        var role=new Role(); role.setName("ROLE_EMPLOYEE");
        employee=new User(); employee.setId(3L); employee.setRole(role); employee.setLocation(location); employee.setPosition(position);
        employee.setActive(true); employee.setMaxConsecutiveDays((short)5);
        sourceShift=shift(20L,"2030-01-07T01:00:00Z","2030-01-07T05:00:00Z");
        target=shift(21L,"2030-01-07T02:00:00Z","2030-01-07T06:00:00Z");
        source=new ShiftAssignment(); source.setId(30L); source.setUser(employee); source.setWorkShift(sourceShift); source.setPosition(position); source.setStatus(AssignmentStatus.ASSIGNED); source.setAssignmentSource(AssignmentSource.AUTO);
        requirement=new ShiftRequirement(); requirement.setWorkShift(target); requirement.setPosition(position); requirement.setMinEmployees((short)1); requirement.setMaxEmployees((short)1);
    }
    WorkShift shift(Long id,String start,String end) {
        var shift=new WorkShift(); shift.setId(id); shift.setSchedulePeriod(period); shift.setStatus(WorkShiftStatus.OPEN);
        shift.setStartAt(Instant.parse(start)); shift.setEndAt(Instant.parse(end)); return shift;
    }
    CalendarPlacementRequest body(long version) { return new CalendarPlacementRequest(3L,21L,30L,20L,version,"Chuyển qua lịch"); }
    void loadSource() {
        when(periods.findById(10L)).thenReturn(Optional.of(period));
        when(shifts.findById(21L)).thenReturn(Optional.of(target));
        when(users.findById(3L)).thenReturn(Optional.of(employee));
        when(assignments.findById(30L)).thenReturn(Optional.of(source));
    }
    void eligible() {
        when(requirements.findByWorkShiftIdAndPositionId(anyLong(),eq(2L))).thenAnswer(call ->
            call.<Long>getArgument(0).equals(21L) ? Optional.of(requirement) : Optional.empty());
        when(constraints.evaluate(employee,target,position,Set.of(30L))).thenReturn(new AssignmentConstraintResult(AvailabilityType.AVAILABLE,List.of(),new BigDecimal("4.00"),new BigDecimal("4.00")));
    }
    void lock() {
        when(periods.lockCalendarPeriod(10L)).thenReturn(Optional.of(10L));
        when(assignments.findWorkShiftIdByAssignmentId(30L)).thenReturn(Optional.of(20L));
        when(shifts.findByIdForUpdate(20L)).thenReturn(Optional.of(sourceShift));
        when(shifts.findByIdForUpdate(21L)).thenReturn(Optional.of(target));
        when(users.lockAttendanceEmployee(3L)).thenReturn(Optional.of(3L));
    }
    @Test void previewExcludesSourceAndDoesNotMutateAssignments() {
        loadSource(); eligible();
        var result=service.preview("manager",10L,body(0));
        assertTrue(result.eligible()); assertEquals(20L,source.getWorkShift().getId());
        verify(constraints).evaluate(employee,target,position,Set.of(30L));
        verify(assignments,never()).saveAndFlush(any()); verifyNoInteractions(audit,entityManager,assignmentService);
    }
    @Test void staleVersionStopsPlacementBeforeAnyMutation() {
        loadSource(); source.setVersion(2);
        assertThrows(DuplicateResourceException.class,()->service.preview("manager",10L,body(1)));
        assertEquals(20L,source.getWorkShift().getId()); verifyNoInteractions(constraints,audit);
    }
    @Test void invalidPlacementKeepsSourceAndDoesNotWriteAudit() {
        loadSource(); lock(); eligible();
        when(constraints.evaluate(employee,target,position,Set.of(30L))).thenReturn(new AssignmentConstraintResult(AvailabilityType.AVAILABLE,List.of("Vượt giờ"),BigDecimal.TEN,BigDecimal.TEN));
        assertThrows(BusinessRuleException.class,()->service.place("manager",10L,body(0)));
        assertEquals(20L,source.getWorkShift().getId()); verify(assignments,never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }
    @Test void validMovePreservesAssignmentIdentityAndAuditsBothShifts() {
        loadSource(); lock(); eligible();
        when(users.findByUsername("manager")).thenReturn(Optional.of(employee));
        when(assignments.saveAndFlush(source)).thenAnswer(call -> { source.setVersion(1); return source; });
        var result=service.place("manager",10L,body(0));
        assertEquals(30L,result.assignmentId()); assertEquals(21L,source.getWorkShift().getId()); assertEquals(1,result.version());
        assertEquals(AssignmentSource.MANUAL,source.getAssignmentSource());
        verify(assignments,never()).delete(any()); verify(audit,times(2)).record(anyString(),any(),any(),eq(ScheduleAuditAction.UPDATED),eq(ScheduleAuditTargetType.SHIFT_ASSIGNMENT),eq(30L),anyString(),any(),any());
        var order=inOrder(periods,shifts,users,entityManager);
        order.verify(periods).lockCalendarPeriod(10L); order.verify(shifts).findByIdForUpdate(20L); order.verify(shifts).findByIdForUpdate(21L);
        order.verify(users).lockAttendanceEmployee(3L); order.verify(entityManager).clear();
    }
    @Test void attendancePreventsMovingHistoricalAssignment() {
        loadSource(); eligible(); when(attendances.existsByShiftAssignmentId(30L)).thenReturn(true);
        var result=service.preview("manager",10L,body(0));
        assertFalse(result.eligible()); assertTrue(result.violations().stream().anyMatch(s -> s.contains("đã có công")));
    }
    @Test void publishedPeriodCannotAcceptPlacementEvenForEligibleEmployee() {
        loadSource(); eligible(); period.setStatus(SchedulePeriodStatus.PUBLISHED);
        assertFalse(service.preview("manager",10L,body(0)).eligible());
    }
    @Test void foreignEmployeeIsRejectedBeforeProjectingPrivateHours() {
        when(periods.findById(10L)).thenReturn(Optional.of(period)); when(shifts.findById(21L)).thenReturn(Optional.of(target));
        when(users.findById(3L)).thenReturn(Optional.of(employee));
        var elsewhere=new Location(); elsewhere.setId(99L); employee.setLocation(elsewhere);
        assertThrows(BusinessRuleException.class,()->service.preview("manager",10L,body(0)));
        verifyNoInteractions(constraints,assignments);
    }
}
