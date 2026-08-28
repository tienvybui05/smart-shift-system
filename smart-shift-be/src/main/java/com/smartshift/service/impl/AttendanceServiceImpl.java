package com.smartshift.service.impl;

import com.smartshift.dto.attendance.AttendanceApprovalRequest;
import com.smartshift.dto.attendance.AttendanceGpsRequest;
import com.smartshift.dto.attendance.AttendanceResponse;
import com.smartshift.entity.Attendance;
import com.smartshift.entity.Location;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AttendanceStatus;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.AttendanceMapper;
import com.smartshift.repository.AttendanceRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.AttendanceService;
import com.smartshift.service.SchedulingAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceServiceImpl implements AttendanceService {

    private static final Duration CHECK_IN_EARLY_WINDOW = Duration.ofHours(2);
    private static final Duration CHECK_OUT_LATE_WINDOW = Duration.ofHours(12);
    private static final Duration LATE_TOLERANCE = Duration.ofMinutes(5);
    private static final Duration EARLY_LEAVE_TOLERANCE = Duration.ofMinutes(5);
    private static final BigDecimal MAX_GPS_ACCURACY_METERS =
        new BigDecimal("200.00");
    private static final int MAX_HISTORY_DAYS = 93;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final AttendanceRepository attendanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final UserRepository userRepository;
    private final SchedulingAccessService schedulingAccessService;
    private final AttendanceMapper attendanceMapper;
    private final Clock clock;

    @Override
    @Transactional
    public AttendanceResponse checkIn(
        String username,
        AttendanceGpsRequest request
    ) {
        ShiftAssignment assignment = findAssignmentForUpdate(
            request.shiftAssignmentId()
        );
        validateAssignmentOwner(assignment, username);
        validateCheckInAssignment(assignment);

        Instant checkedInAt = clock.instant();
        validateCheckInWindow(assignment.getWorkShift(), checkedInAt);
        if (attendanceRepository.findByShiftAssignmentId(
            assignment.getId()
        ).isPresent()) {
            throw new DuplicateResourceException(
                "Ca làm này đã được check-in"
            );
        }

        GpsCheck gpsCheck = validateGps(
            assignment.getWorkShift().getSchedulePeriod().getLocation(),
            request
        );
        Attendance attendance = new Attendance();
        attendance.setShiftAssignment(assignment);
        attendance.setCheckInAt(checkedInAt);
        attendance.setCheckInLatitude(request.latitude());
        attendance.setCheckInLongitude(request.longitude());
        attendance.setCheckInAccuracyMeters(
            request.accuracyMeters().setScale(2, RoundingMode.HALF_UP)
        );
        attendance.setCheckInDistanceMeters(gpsCheck.distanceMeters());
        attendance.setBreakMinutes(assignment.getWorkShift().getBreakMinutes());
        recalculateAttendance(attendance);

        return attendanceMapper.toResponse(
            attendanceRepository.save(attendance)
        );
    }

    @Override
    @Transactional
    public AttendanceResponse checkOut(
        String username,
        AttendanceGpsRequest request
    ) {
        ShiftAssignment assignment = findAssignmentForUpdate(
            request.shiftAssignmentId()
        );
        validateAssignmentOwner(assignment, username);
        validateCheckoutAssignment(assignment);

        Attendance attendance = attendanceRepository
            .findDetailedByShiftAssignmentIdForUpdate(assignment.getId())
            .orElseThrow(() -> new BusinessRuleException(
                "Bạn chưa check-in ca làm này"
            ));
        if (attendance.getCheckInAt() == null) {
            throw new BusinessRuleException(
                "Ca làm này chưa có thời gian check-in"
            );
        }
        if (attendance.getCheckOutAt() != null) {
            throw new DuplicateResourceException(
                "Ca làm này đã được check-out"
            );
        }

        Instant checkedOutAt = clock.instant();
        validateCheckOutWindow(
            assignment.getWorkShift(),
            attendance.getCheckInAt(),
            checkedOutAt
        );
        GpsCheck gpsCheck = validateGps(
            assignment.getWorkShift().getSchedulePeriod().getLocation(),
            request
        );

        attendance.setCheckOutAt(checkedOutAt);
        attendance.setCheckOutLatitude(request.latitude());
        attendance.setCheckOutLongitude(request.longitude());
        attendance.setCheckOutAccuracyMeters(
            request.accuracyMeters().setScale(2, RoundingMode.HALF_UP)
        );
        attendance.setCheckOutDistanceMeters(gpsCheck.distanceMeters());
        attendance.setApprovedBy(null);
        attendance.setApprovedAt(null);
        recalculateAttendance(attendance);

        return attendanceMapper.toResponse(
            attendanceRepository.save(attendance)
        );
    }

    @Override
    public List<AttendanceResponse> getMyAttendances(
        String username,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);
        TimeRange range = createBroadTimeRange(startDate, endDate);
        return attendanceRepository.findMyAttendancesInRange(
            username,
            range.start(),
            range.end()
        ).stream()
            .filter(attendance -> isWithinLocalDateRange(
                attendance,
                startDate,
                endDate
            ))
            .map(attendanceMapper::toResponse)
            .toList();
    }

    @Override
    public List<AttendanceResponse> getAttendances(
        String username,
        LocalDate startDate,
        LocalDate endDate,
        Long locationId,
        AttendanceStatus status
    ) {
        validateDateRange(startDate, endDate);
        Long effectiveLocationId = schedulingAccessService
            .resolveLocationFilter(username, locationId);
        TimeRange range = createBroadTimeRange(startDate, endDate);
        return attendanceRepository.search(
            effectiveLocationId,
            status,
            range.start(),
            range.end()
        ).stream()
            .filter(attendance -> isWithinLocalDateRange(
                attendance,
                startDate,
                endDate
            ))
            .map(attendanceMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public AttendanceResponse approveAttendance(
        String username,
        Long attendanceId,
        AttendanceApprovalRequest request
    ) {
        Attendance attendance = attendanceRepository
            .findDetailedByIdForUpdate(attendanceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy bản chấm công với id " + attendanceId
            ));
        schedulingAccessService.requireAssignment(
            username,
            attendance.getShiftAssignment().getId()
        );
        User reviewer = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));

        applyApproval(request, attendance);
        attendance.setApprovedBy(reviewer);
        attendance.setApprovedAt(clock.instant());
        attendance.setNote(normalizeNullableText(request.note()));

        return attendanceMapper.toResponse(
            attendanceRepository.save(attendance)
        );
    }

    private void applyApproval(
        AttendanceApprovalRequest request,
        Attendance attendance
    ) {
        boolean hasCheckIn = request.checkInAt() != null;
        boolean hasCheckOut = request.checkOutAt() != null;
        if (hasCheckIn != hasCheckOut) {
            throw new BusinessRuleException(
                "Phải khai báo đồng thời thời gian check-in và check-out"
            );
        }

        attendance.setBreakMinutes(request.breakMinutes());
        if (!hasCheckIn) {
            clearAttendanceTimesAndGps(attendance);
            attendance.setLateMinutes(0);
            attendance.setEarlyLeaveMinutes(0);
            attendance.setOvertimeMinutes(0);
            attendance.setStatus(AttendanceStatus.ABSENT);
            return;
        }

        if (!request.checkOutAt().isAfter(request.checkInAt())) {
            throw new BusinessRuleException(
                "Thời gian check-out phải sau thời gian check-in"
            );
        }
        long elapsedMinutes = Duration.between(
            request.checkInAt(),
            request.checkOutAt()
        ).toMinutes();
        if (request.breakMinutes() >= elapsedMinutes) {
            throw new BusinessRuleException(
                "Thời gian nghỉ phải nhỏ hơn thời gian làm việc thực tế"
            );
        }

        attendance.setCheckInAt(request.checkInAt());
        attendance.setCheckOutAt(request.checkOutAt());
        recalculateAttendance(attendance);
    }

    private void clearAttendanceTimesAndGps(Attendance attendance) {
        attendance.setCheckInAt(null);
        attendance.setCheckOutAt(null);
        attendance.setCheckInLatitude(null);
        attendance.setCheckInLongitude(null);
        attendance.setCheckInAccuracyMeters(null);
        attendance.setCheckInDistanceMeters(null);
        attendance.setCheckOutLatitude(null);
        attendance.setCheckOutLongitude(null);
        attendance.setCheckOutAccuracyMeters(null);
        attendance.setCheckOutDistanceMeters(null);
    }

    private ShiftAssignment findAssignmentForUpdate(Long assignmentId) {
        return shiftAssignmentRepository.findByIdForUpdate(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy phân công ca với id " + assignmentId
            ));
    }

    private void validateAssignmentOwner(
        ShiftAssignment assignment,
        String username
    ) {
        if (!assignment.getUser().getUsername().equals(username)) {
            throw new ResourceNotFoundException(
                "Không tìm thấy phân công ca với id " + assignment.getId()
            );
        }
    }

    private void validateCheckInAssignment(ShiftAssignment assignment) {
        if (!isActiveAssignment(assignment.getStatus())) {
            throw new BusinessRuleException(
                "Chỉ có thể check-in ca đang được phân công"
            );
        }

        WorkShift workShift = assignment.getWorkShift();
        if (
            workShift.getStatus() == WorkShiftStatus.CANCELLED
                || workShift.getStatus() == WorkShiftStatus.COMPLETED
        ) {
            throw new BusinessRuleException(
                "Không thể check-in ca đã hủy hoặc hoàn thành"
            );
        }
        SchedulePeriodStatus periodStatus = workShift
            .getSchedulePeriod()
            .getStatus();
        if (
            periodStatus != SchedulePeriodStatus.PUBLISHED
                && periodStatus != SchedulePeriodStatus.LOCKED
        ) {
            throw new BusinessRuleException(
                "Chỉ có thể chấm công cho lịch đã công bố"
            );
        }
    }

    private void validateCheckoutAssignment(ShiftAssignment assignment) {
        if (!isActiveAssignment(assignment.getStatus())) {
            throw new BusinessRuleException(
                "Không thể check-out phân công đã hủy hoặc hoàn thành"
            );
        }
        if (assignment.getWorkShift().getStatus() == WorkShiftStatus.CANCELLED) {
            throw new BusinessRuleException(
                "Không thể check-out ca đã bị hủy"
            );
        }
    }

    private boolean isActiveAssignment(AssignmentStatus status) {
        return status == AssignmentStatus.ASSIGNED
            || status == AssignmentStatus.CONFIRMED;
    }

    private void validateCheckInWindow(WorkShift workShift, Instant now) {
        Instant opensAt = workShift.getStartAt().minus(CHECK_IN_EARLY_WINDOW);
        if (now.isBefore(opensAt)) {
            throw new BusinessRuleException(
                "Chỉ được check-in trong vòng 2 giờ trước khi ca bắt đầu"
            );
        }
        if (!now.isBefore(workShift.getEndAt())) {
            throw new BusinessRuleException(
                "Không thể check-in sau khi ca đã kết thúc"
            );
        }
    }

    private void validateCheckOutWindow(
        WorkShift workShift,
        Instant checkedInAt,
        Instant now
    ) {
        if (!now.isAfter(checkedInAt)) {
            throw new BusinessRuleException(
                "Thời gian check-out phải sau thời gian check-in"
            );
        }
        if (now.isAfter(workShift.getEndAt().plus(CHECK_OUT_LATE_WINDOW))) {
            throw new BusinessRuleException(
                "Đã quá thời hạn check-out; vui lòng liên hệ quản lý"
            );
        }
    }

    private GpsCheck validateGps(
        Location location,
        AttendanceGpsRequest request
    ) {
        if (location.getLatitude() == null || location.getLongitude() == null) {
            throw new BusinessRuleException(
                "Chi nhánh chưa được cấu hình tọa độ chấm công"
            );
        }
        if (request.accuracyMeters().compareTo(
            MAX_GPS_ACCURACY_METERS
        ) > 0) {
            throw new BusinessRuleException(
                "Tín hiệu GPS chưa đủ chính xác; vui lòng thử lại"
            );
        }

        BigDecimal distanceMeters = calculateDistanceMeters(
            location.getLatitude(),
            location.getLongitude(),
            request.latitude(),
            request.longitude()
        );
        if (distanceMeters.compareTo(BigDecimal.valueOf(
            location.getAttendanceRadiusMeters()
        )) > 0) {
            throw new BusinessRuleException(
                "Bạn đang ở ngoài bán kính chấm công của chi nhánh"
            );
        }
        return new GpsCheck(distanceMeters);
    }

    private BigDecimal calculateDistanceMeters(
        BigDecimal originLatitude,
        BigDecimal originLongitude,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
        double originLatRadians = Math.toRadians(originLatitude.doubleValue());
        double latitudeRadians = Math.toRadians(latitude.doubleValue());
        double latitudeDelta = Math.toRadians(
            latitude.doubleValue() - originLatitude.doubleValue()
        );
        double longitudeDelta = Math.toRadians(
            longitude.doubleValue() - originLongitude.doubleValue()
        );
        double haversine = Math.sin(latitudeDelta / 2)
            * Math.sin(latitudeDelta / 2)
            + Math.cos(originLatRadians)
            * Math.cos(latitudeRadians)
            * Math.sin(longitudeDelta / 2)
            * Math.sin(longitudeDelta / 2);
        haversine = Math.max(0, Math.min(1, haversine));
        double angularDistance = 2 * Math.atan2(
            Math.sqrt(haversine),
            Math.sqrt(1 - haversine)
        );
        return BigDecimal.valueOf(EARTH_RADIUS_METERS * angularDistance)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private void recalculateAttendance(Attendance attendance) {
        WorkShift workShift = attendance.getShiftAssignment().getWorkShift();
        Instant checkInAt = attendance.getCheckInAt();
        Instant checkOutAt = attendance.getCheckOutAt();

        int lateMinutes = checkInAt != null
            && checkInAt.isAfter(workShift.getStartAt().plus(LATE_TOLERANCE))
            ? Math.toIntExact(Duration.between(
                workShift.getStartAt(),
                checkInAt
            ).toMinutes())
            : 0;
        int earlyLeaveMinutes = checkOutAt != null
            && checkOutAt.isBefore(
                workShift.getEndAt().minus(EARLY_LEAVE_TOLERANCE)
            )
            ? Math.toIntExact(Duration.between(
                checkOutAt,
                workShift.getEndAt()
            ).toMinutes())
            : 0;
        int overtimeMinutes = checkOutAt != null
            && checkOutAt.isAfter(workShift.getEndAt())
            ? Math.toIntExact(Duration.between(
                workShift.getEndAt(),
                checkOutAt
            ).toMinutes())
            : 0;

        attendance.setLateMinutes(lateMinutes);
        attendance.setEarlyLeaveMinutes(earlyLeaveMinutes);
        attendance.setOvertimeMinutes(overtimeMinutes);
        if (lateMinutes > 0) {
            attendance.setStatus(AttendanceStatus.LATE);
        } else if (earlyLeaveMinutes > 0) {
            attendance.setStatus(AttendanceStatus.EARLY_LEAVE);
        } else {
            attendance.setStatus(AttendanceStatus.PRESENT);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_HISTORY_DAYS) {
            throw new BusinessRuleException(
                "Chỉ được tra cứu tối đa 93 ngày"
            );
        }
    }

    private TimeRange createBroadTimeRange(
        LocalDate startDate,
        LocalDate endDate
    ) {
        Instant rangeStart = startDate
            .atStartOfDay(ZoneOffset.ofHours(14))
            .toInstant();
        Instant rangeEnd = endDate.plusDays(1)
            .atStartOfDay(ZoneOffset.ofHours(-12))
            .toInstant();
        return new TimeRange(rangeStart, rangeEnd);
    }

    private boolean isWithinLocalDateRange(
        Attendance attendance,
        LocalDate startDate,
        LocalDate endDate
    ) {
        WorkShift workShift = attendance.getShiftAssignment().getWorkShift();
        ZoneId zoneId = ZoneId.of(
            workShift.getSchedulePeriod().getLocation().getTimezone()
        );
        LocalDate workDate = workShift.getStartAt()
            .atZone(zoneId)
            .toLocalDate();
        return !workDate.isBefore(startDate) && !workDate.isAfter(endDate);
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record GpsCheck(BigDecimal distanceMeters) {
    }

    private record TimeRange(Instant start, Instant end) {
    }
}
