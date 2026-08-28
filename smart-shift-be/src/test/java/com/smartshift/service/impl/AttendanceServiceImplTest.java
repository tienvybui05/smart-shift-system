package com.smartshift.service.impl;

import com.smartshift.dto.attendance.AttendanceApprovalRequest;
import com.smartshift.dto.attendance.AttendanceGpsRequest;
import com.smartshift.entity.Attendance;
import com.smartshift.entity.Location;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AttendanceStatus;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.mapper.AttendanceMapper;
import com.smartshift.repository.AttendanceRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.SchedulingAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private static final Long ASSIGNMENT_ID = 10L;
    private static final BigDecimal LOCATION_LATITUDE =
        new BigDecimal("10.776889");
    private static final BigDecimal LOCATION_LONGITUDE =
        new BigDecimal("106.700806");

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchedulingAccessService schedulingAccessService;

    private AttendanceServiceImpl attendanceService;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceServiceImpl(
            attendanceRepository,
            shiftAssignmentRepository,
            userRepository,
            schedulingAccessService,
            new AttendanceMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void checkInSucceedsInsideLocationRadius() {
        ShiftAssignment assignment = assignment(
            NOW,
            NOW.plusSeconds(8 * 60 * 60)
        );
        stubNewAttendance(assignment);

        var response = attendanceService.checkIn("employee", gpsRequest(
            LOCATION_LATITUDE,
            LOCATION_LONGITUDE,
            "15.00"
        ));

        assertEquals(NOW, response.checkInAt());
        assertEquals(AttendanceStatus.PRESENT, response.status());
        assertEquals(new BigDecimal("0.00"), response.checkInDistanceMeters());
        assertEquals(0, response.lateMinutes());
    }

    @Test
    void lateCheckInRecordsLateMinutes() {
        ShiftAssignment assignment = assignment(
            NOW.minusSeconds(10 * 60),
            NOW.plusSeconds(8 * 60 * 60)
        );
        stubNewAttendance(assignment);

        var response = attendanceService.checkIn("employee", gpsRequest(
            LOCATION_LATITUDE,
            LOCATION_LONGITUDE,
            "10.00"
        ));

        assertEquals(AttendanceStatus.LATE, response.status());
        assertEquals(10, response.lateMinutes());
    }

    @Test
    void checkInRejectsPositionOutsideLocationRadius() {
        ShiftAssignment assignment = assignment(
            NOW,
            NOW.plusSeconds(8 * 60 * 60)
        );
        when(shiftAssignmentRepository.findByIdForUpdate(ASSIGNMENT_ID))
            .thenReturn(Optional.of(assignment));
        when(attendanceRepository.findByShiftAssignmentId(ASSIGNMENT_ID))
            .thenReturn(Optional.empty());

        assertThrows(
            BusinessRuleException.class,
            () -> attendanceService.checkIn("employee", gpsRequest(
                new BigDecimal("10.786889"),
                LOCATION_LONGITUDE,
                "10.00"
            ))
        );
    }

    @Test
    void checkInRejectsInaccurateGpsReading() {
        ShiftAssignment assignment = assignment(
            NOW,
            NOW.plusSeconds(8 * 60 * 60)
        );
        when(shiftAssignmentRepository.findByIdForUpdate(ASSIGNMENT_ID))
            .thenReturn(Optional.of(assignment));
        when(attendanceRepository.findByShiftAssignmentId(ASSIGNMENT_ID))
            .thenReturn(Optional.empty());

        assertThrows(
            BusinessRuleException.class,
            () -> attendanceService.checkIn("employee", gpsRequest(
                LOCATION_LATITUDE,
                LOCATION_LONGITUDE,
                "201.00"
            ))
        );
    }

    @Test
    void checkInRejectsDuplicateAttendance() {
        ShiftAssignment assignment = assignment(
            NOW,
            NOW.plusSeconds(8 * 60 * 60)
        );
        when(shiftAssignmentRepository.findByIdForUpdate(ASSIGNMENT_ID))
            .thenReturn(Optional.of(assignment));
        when(attendanceRepository.findByShiftAssignmentId(ASSIGNMENT_ID))
            .thenReturn(Optional.of(attendance(assignment, NOW)));

        assertThrows(
            DuplicateResourceException.class,
            () -> attendanceService.checkIn("employee", gpsRequest(
                LOCATION_LATITUDE,
                LOCATION_LONGITUDE,
                "10.00"
            ))
        );
    }

    @Test
    void earlyCheckoutRecordsEarlyLeaveMinutes() {
        ShiftAssignment assignment = assignment(
            NOW.minusSeconds(60 * 60),
            NOW.plusSeconds(60 * 60)
        );
        Attendance attendance = attendance(
            assignment,
            NOW.minusSeconds(60 * 60)
        );
        when(shiftAssignmentRepository.findByIdForUpdate(ASSIGNMENT_ID))
            .thenReturn(Optional.of(assignment));
        when(attendanceRepository.findDetailedByShiftAssignmentIdForUpdate(
            ASSIGNMENT_ID
        )).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any(Attendance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = attendanceService.checkOut("employee", gpsRequest(
            LOCATION_LATITUDE,
            LOCATION_LONGITUDE,
            "10.00"
        ));

        assertEquals(NOW, response.checkOutAt());
        assertEquals(AttendanceStatus.EARLY_LEAVE, response.status());
        assertEquals(60, response.earlyLeaveMinutes());
        assertEquals(30, response.actualMinutes());
    }

    @Test
    void managerCanApproveAttendanceAsAbsent() {
        ShiftAssignment assignment = assignment(
            NOW.minusSeconds(9 * 60 * 60),
            NOW.minusSeconds(60 * 60)
        );
        Attendance attendance = attendance(
            assignment,
            NOW.minusSeconds(9 * 60 * 60)
        );
        attendance.setId(20L);
        User reviewer = new User();
        reviewer.setId(30L);
        reviewer.setFullName("Quản lý ca");

        when(attendanceRepository.findDetailedByIdForUpdate(20L))
            .thenReturn(Optional.of(attendance));
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(reviewer));
        when(attendanceRepository.save(any(Attendance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = attendanceService.approveAttendance(
            "manager",
            20L,
            new AttendanceApprovalRequest(null, null, (short) 0, "Vắng mặt")
        );

        verify(schedulingAccessService).requireAssignment(
            "manager",
            ASSIGNMENT_ID
        );
        assertEquals(AttendanceStatus.ABSENT, response.status());
        assertNull(response.checkInAt());
        assertNull(response.checkOutAt());
        assertEquals(30L, response.approvedById());
        assertEquals(NOW, response.approvedAt());
    }

    @Test
    void historyRejectsDateRangeLongerThanNinetyThreeDays() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);

        BusinessRuleException exception = assertThrows(
            BusinessRuleException.class,
            () -> attendanceService.getMyAttendances(
                "employee",
                startDate,
                startDate.plusDays(93)
            )
        );

        assertTrue(exception.getMessage().contains("93 ngày"));
    }

    private void stubNewAttendance(ShiftAssignment assignment) {
        when(shiftAssignmentRepository.findByIdForUpdate(ASSIGNMENT_ID))
            .thenReturn(Optional.of(assignment));
        when(attendanceRepository.findByShiftAssignmentId(ASSIGNMENT_ID))
            .thenReturn(Optional.empty());
        when(attendanceRepository.save(any(Attendance.class)))
            .thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setId(20L);
                return saved;
            });
    }

    private AttendanceGpsRequest gpsRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        String accuracyMeters
    ) {
        return new AttendanceGpsRequest(
            ASSIGNMENT_ID,
            latitude,
            longitude,
            new BigDecimal(accuracyMeters)
        );
    }

    private Attendance attendance(
        ShiftAssignment assignment,
        Instant checkInAt
    ) {
        Attendance attendance = new Attendance();
        attendance.setShiftAssignment(assignment);
        attendance.setCheckInAt(checkInAt);
        attendance.setBreakMinutes((short) 30);
        attendance.setStatus(AttendanceStatus.PRESENT);
        return attendance;
    }

    private ShiftAssignment assignment(Instant startAt, Instant endAt) {
        Location location = new Location();
        location.setId(1L);
        location.setName("Chi nhánh Hồ Chí Minh 01");
        location.setTimezone("Asia/Ho_Chi_Minh");
        location.setLatitude(LOCATION_LATITUDE);
        location.setLongitude(LOCATION_LONGITUDE);
        location.setAttendanceRadiusMeters(200);

        SchedulePeriod period = new SchedulePeriod();
        period.setId(2L);
        period.setLocation(location);
        period.setStatus(SchedulePeriodStatus.PUBLISHED);

        WorkShift workShift = new WorkShift();
        workShift.setId(3L);
        workShift.setSchedulePeriod(period);
        workShift.setStartAt(startAt);
        workShift.setEndAt(endAt);
        workShift.setBreakMinutes((short) 30);
        workShift.setStatus(WorkShiftStatus.FILLED);

        User employee = new User();
        employee.setId(4L);
        employee.setEmployeeCode("EMP001");
        employee.setUsername("employee");
        employee.setFullName("Nhân viên thử nghiệm");

        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setUser(employee);
        assignment.setWorkShift(workShift);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        return assignment;
    }
}
