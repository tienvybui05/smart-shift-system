package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentCandidateResponse;
import com.smartshift.dto.assignment.AssignmentRequirementProgressResponse;
import com.smartshift.dto.assignment.MyWorkScheduleResponse;
import com.smartshift.dto.assignment.ShiftAssignmentRequest;
import com.smartshift.dto.assignment.ShiftAssignmentResponse;
import com.smartshift.dto.assignment.ShiftAssignmentSummaryResponse;
import com.smartshift.entity.EmployeeAvailability;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.ShiftAssignmentMapper;
import com.smartshift.repository.EmployeeAvailabilityRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.TimeOffRequestRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.ShiftAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftAssignmentServiceImpl implements ShiftAssignmentService {

    private static final long MAX_SCHEDULE_QUERY_DAYS = 93;

    private static final List<AssignmentStatus> ACTIVE_STATUSES = List.of(
        AssignmentStatus.ASSIGNED,
        AssignmentStatus.CONFIRMED
    );

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final EmployeeAvailabilityRepository availabilityRepository;
    private final TimeOffRequestRepository timeOffRequestRepository;
    private final WorkShiftRepository workShiftRepository;
    private final UserRepository userRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;

    @Override
    public ShiftAssignmentSummaryResponse getSummary(Long workShiftId) {
        WorkShift workShift = findWorkShiftById(workShiftId);
        return buildSummary(workShift);
    }

    @Override
    public List<AssignmentCandidateResponse> getCandidates(
        Long workShiftId,
        Long positionId
    ) {
        WorkShift workShift = findWorkShiftById(workShiftId);
        findRequirement(workShiftId, positionId);

        Set<Long> assignedUserIds = new HashSet<>(
            findActiveAssignments(workShiftId).stream()
                .map(assignment -> assignment.getUser().getId())
                .toList()
        );
        Long locationId = workShift.getSchedulePeriod().getLocation().getId();

        return userRepository
            .findAllByLocationIdAndPositionIdAndActiveTrueOrderByFullNameAsc(
                locationId,
                positionId
            )
            .stream()
            .map(user -> evaluateCandidate(
                user,
                workShift,
                assignedUserIds.contains(user.getId())
            ))
            .sorted(candidateComparator())
            .toList();
    }

    @Override
    public List<MyWorkScheduleResponse> getMySchedule(
        String username,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateScheduleQueryRange(startDate, endDate);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
        ZoneId zoneId = ZoneId.of(user.getLocation().getTimezone());
        Instant rangeStart = startDate.atStartOfDay(zoneId).toInstant();
        Instant rangeEnd = endDate.plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant();
        return shiftAssignmentRepository.findMyScheduleInRange(
            username,
            ACTIVE_STATUSES,
            rangeStart,
            rangeEnd
        ).stream()
            .map(shiftAssignmentMapper::toMyScheduleResponse)
            .toList();
    }

    @Override
    @Transactional
    public ShiftAssignmentSummaryResponse assignEmployee(
        Long workShiftId,
        ShiftAssignmentRequest request,
        String assignedByUsername
    ) {
        WorkShift workShift = findWorkShiftByIdForUpdate(workShiftId);
        validateEditable(workShift);

        User employee = userRepository.findByIdForUpdate(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy nhân viên có id " + request.userId()
            ));
        validateEmployeeMatchesShift(employee, workShift);

        ShiftRequirement requirement = findRequirement(
            workShiftId,
            employee.getPosition().getId()
        );
        if (shiftAssignmentRepository.findByWorkShiftIdAndUserId(
            workShiftId,
            employee.getId()
        ).isPresent()) {
            throw new DuplicateResourceException(
                "Nhân viên đã được phân công vào ca này"
            );
        }

        long assignedForPosition = shiftAssignmentRepository
            .countByWorkShiftIdAndPositionIdAndStatusIn(
                workShiftId,
                requirement.getPosition().getId(),
                ACTIVE_STATUSES
            );
        if (assignedForPosition >= requirement.getMaxEmployees()) {
            throw new BusinessRuleException(
                "Vị trí '" + requirement.getPosition().getName()
                    + "' đã đạt số nhân viên tối đa"
            );
        }

        AssignmentCandidateResponse evaluation = evaluateCandidate(
            employee,
            workShift,
            false
        );
        if (!evaluation.eligible()) {
            throw new BusinessRuleException(
                String.join("; ", evaluation.ineligibilityReasons())
            );
        }

        User assignedBy = userRepository.findByUsername(assignedByUsername)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản đang thực hiện phân công"
            ));
        ShiftAssignment assignment = shiftAssignmentMapper.toEntity(
            workShift,
            employee,
            assignedBy,
            request.note()
        );
        shiftAssignmentRepository.saveAndFlush(assignment);
        refreshWorkShiftStatus(workShift);
        return buildSummary(workShift);
    }

    @Override
    @Transactional
    public ShiftAssignmentSummaryResponse removeAssignment(Long assignmentId) {
        ShiftAssignment assignment = shiftAssignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy phân công có id " + assignmentId
            ));
        WorkShift workShift = findWorkShiftByIdForUpdate(
            assignment.getWorkShift().getId()
        );
        validateEditable(workShift);

        shiftAssignmentRepository.delete(assignment);
        shiftAssignmentRepository.flush();
        refreshWorkShiftStatus(workShift);
        return buildSummary(workShift);
    }

    private AssignmentCandidateResponse evaluateCandidate(
        User user,
        WorkShift workShift,
        boolean alreadyAssigned
    ) {
        List<String> reasons = new ArrayList<>();
        if (alreadyAssigned) {
            reasons.add("Nhân viên đã được phân công vào ca này");
        }

        if (timeOffRequestRepository.existsOverlappingRequest(
            user.getId(),
            List.of(TimeOffStatus.APPROVED),
            workShift.getStartAt(),
            workShift.getEndAt()
        )) {
            reasons.add(
                "Nhân viên có đơn nghỉ đã được duyệt trong thời gian của ca"
            );
        }

        AvailabilityType availabilityType = findAvailabilityCoverage(
            user,
            workShift
        );
        if (availabilityType == null) {
            reasons.add("Chưa đăng ký rảnh cho toàn bộ thời gian của ca");
        }

        List<ShiftAssignment> existingAssignments = loadAssignmentsForRules(
            user,
            workShift
        ).stream()
            .filter(assignment -> !assignment.getWorkShift().getId().equals(
                workShift.getId()
            ))
            .toList();

        validateTimeConflict(existingAssignments, workShift, reasons);
        validateMinimumRest(user, existingAssignments, workShift, reasons);

        BigDecimal targetHours = calculateNetHours(workShift);
        BigDecimal currentDailyHours = calculateAssignedHours(
            existingAssignments,
            workShift,
            true
        );
        BigDecimal currentWeeklyHours = calculateAssignedHours(
            existingAssignments,
            workShift,
            false
        );
        BigDecimal projectedDailyHours = currentDailyHours.add(targetHours);
        BigDecimal projectedWeeklyHours = currentWeeklyHours.add(targetHours);

        if (projectedDailyHours.compareTo(user.getMaxHoursPerDay()) > 0) {
            reasons.add(
                "Vượt giới hạn " + user.getMaxHoursPerDay()
                    + " giờ làm trong ngày"
            );
        }
        if (projectedWeeklyHours.compareTo(user.getMaxHoursPerWeek()) > 0) {
            reasons.add(
                "Vượt giới hạn " + user.getMaxHoursPerWeek()
                    + " giờ làm trong tuần"
            );
        }

        return new AssignmentCandidateResponse(
            user.getId(),
            user.getEmployeeCode(),
            user.getFullName(),
            user.getEmploymentType(),
            availabilityType,
            alreadyAssigned,
            reasons.isEmpty(),
            List.copyOf(reasons),
            projectedDailyHours,
            user.getMaxHoursPerDay(),
            projectedWeeklyHours,
            user.getMaxHoursPerWeek()
        );
    }

    private void validateTimeConflict(
        List<ShiftAssignment> existingAssignments,
        WorkShift targetShift,
        List<String> reasons
    ) {
        boolean conflicting = existingAssignments.stream().anyMatch(
            assignment -> assignment.getWorkShift().getStartAt().isBefore(
                targetShift.getEndAt()
            ) && assignment.getWorkShift().getEndAt().isAfter(
                targetShift.getStartAt()
            )
        );
        if (conflicting) {
            reasons.add("Bị trùng thời gian với một ca đã được phân công");
        }
    }

    private void validateMinimumRest(
        User user,
        List<ShiftAssignment> existingAssignments,
        WorkShift targetShift,
        List<String> reasons
    ) {
        BigDecimal minimumRest = user.getMinRestHours();
        boolean insufficientRest = existingAssignments.stream().anyMatch(
            assignment -> {
                WorkShift existingShift = assignment.getWorkShift();
                if (!existingShift.getEndAt().isAfter(targetShift.getStartAt())) {
                    return durationHours(
                        existingShift.getEndAt(),
                        targetShift.getStartAt()
                    ).compareTo(minimumRest) < 0;
                }
                if (!targetShift.getEndAt().isAfter(existingShift.getStartAt())) {
                    return durationHours(
                        targetShift.getEndAt(),
                        existingShift.getStartAt()
                    ).compareTo(minimumRest) < 0;
                }
                return false;
            }
        );
        if (insufficientRest) {
            reasons.add(
                "Không đủ tối thiểu " + minimumRest
                    + " giờ nghỉ giữa hai ca"
            );
        }
    }

    private AvailabilityType findAvailabilityCoverage(
        User user,
        WorkShift workShift
    ) {
        ZoneId zoneId = ZoneId.of(
            workShift.getSchedulePeriod().getLocation().getTimezone()
        );
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);
        List<EmployeeAvailability> availabilities = availabilityRepository
            .findAllByUserIdAndAvailableDateBetweenOrderByAvailableDateAscStartTimeAsc(
                user.getId(),
                localStart.toLocalDate(),
                localEnd.toLocalDate()
            );

        List<AvailabilitySegment> segments = buildAvailabilitySegments(
            localStart,
            localEnd
        );
        boolean allPreferred = true;
        for (AvailabilitySegment segment : segments) {
            AvailabilityType segmentType = findSegmentCoverage(
                availabilities,
                segment
            );
            if (segmentType == null) {
                return null;
            }
            allPreferred &= segmentType == AvailabilityType.PREFERRED;
        }
        return allPreferred
            ? AvailabilityType.PREFERRED
            : AvailabilityType.AVAILABLE;
    }

    private List<AvailabilitySegment> buildAvailabilitySegments(
        ZonedDateTime localStart,
        ZonedDateTime localEnd
    ) {
        if (localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            return List.of(new AvailabilitySegment(
                localStart.toLocalDate(),
                localStart.toLocalTime(),
                localEnd.toLocalTime()
            ));
        }

        List<AvailabilitySegment> segments = new ArrayList<>();
        if (localStart.toLocalTime().isBefore(LocalTime.of(23, 59))) {
            segments.add(new AvailabilitySegment(
                localStart.toLocalDate(),
                localStart.toLocalTime(),
                LocalTime.of(23, 59)
            ));
        }
        if (localEnd.toLocalTime().isAfter(LocalTime.MIDNIGHT)) {
            segments.add(new AvailabilitySegment(
                localEnd.toLocalDate(),
                LocalTime.MIDNIGHT,
                localEnd.toLocalTime()
            ));
        }
        return segments;
    }

    private AvailabilityType findSegmentCoverage(
        List<EmployeeAvailability> availabilities,
        AvailabilitySegment segment
    ) {
        boolean hasUnavailableOverlap = availabilities.stream().anyMatch(
            availability -> availability.getAvailableDate().equals(segment.date())
                && availability.getAvailabilityType()
                    == AvailabilityType.UNAVAILABLE
                && availability.getStartTime().isBefore(segment.endTime())
                && availability.getEndTime().isAfter(segment.startTime())
        );
        if (hasUnavailableOverlap) {
            return null;
        }

        return availabilities.stream()
            .filter(availability -> availability.getAvailableDate().equals(
                segment.date()
            ))
            .filter(availability -> availability.getAvailabilityType()
                != AvailabilityType.UNAVAILABLE)
            .filter(availability -> !availability.getStartTime().isAfter(
                segment.startTime()
            ))
            .filter(availability -> !availability.getEndTime().isBefore(
                segment.endTime()
            ))
            .map(EmployeeAvailability::getAvailabilityType)
            .findFirst()
            .orElse(null);
    }

    private List<ShiftAssignment> loadAssignmentsForRules(
        User user,
        WorkShift targetShift
    ) {
        ZoneId zoneId = ZoneId.of(
            targetShift.getSchedulePeriod().getLocation().getTimezone()
        );
        LocalDate targetDate = targetShift.getStartAt()
            .atZone(zoneId)
            .toLocalDate();
        LocalDate weekStart = targetDate.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        long restMinutes = user.getMinRestHours()
            .multiply(BigDecimal.valueOf(60))
            .longValue();
        Instant rangeStart = weekStart.atStartOfDay(zoneId)
            .minusMinutes(restMinutes)
            .toInstant();
        Instant rangeEnd = weekStart.plusWeeks(1)
            .atStartOfDay(zoneId)
            .plusMinutes(restMinutes)
            .toInstant();
        return shiftAssignmentRepository.findActiveAssignmentsInRange(
            user.getId(),
            ACTIVE_STATUSES,
            rangeStart,
            rangeEnd
        );
    }

    private BigDecimal calculateAssignedHours(
        List<ShiftAssignment> assignments,
        WorkShift targetShift,
        boolean sameDayOnly
    ) {
        ZoneId zoneId = ZoneId.of(
            targetShift.getSchedulePeriod().getLocation().getTimezone()
        );
        LocalDate targetDate = targetShift.getStartAt()
            .atZone(zoneId)
            .toLocalDate();
        LocalDate weekStart = targetDate.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate weekEnd = weekStart.plusWeeks(1);

        return assignments.stream()
            .filter(assignment -> {
                LocalDate assignmentDate = assignment.getWorkShift()
                    .getStartAt()
                    .atZone(zoneId)
                    .toLocalDate();
                if (sameDayOnly) {
                    return assignmentDate.equals(targetDate);
                }
                return !assignmentDate.isBefore(weekStart)
                    && assignmentDate.isBefore(weekEnd);
            })
            .map(assignment -> calculateNetHours(assignment.getWorkShift()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateNetHours(WorkShift workShift) {
        long durationMinutes = Duration.between(
            workShift.getStartAt(),
            workShift.getEndAt()
        ).toMinutes();
        long netMinutes = durationMinutes - workShift.getBreakMinutes();
        return BigDecimal.valueOf(netMinutes)
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal durationHours(Instant start, Instant end) {
        return BigDecimal.valueOf(Duration.between(start, end).toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private void validateEmployeeMatchesShift(
        User employee,
        WorkShift workShift
    ) {
        if (!employee.isActive()) {
            throw new BusinessRuleException(
                "Không thể phân công nhân viên đang ngừng hoạt động"
            );
        }
        Long shiftLocationId = workShift.getSchedulePeriod()
            .getLocation()
            .getId();
        if (!employee.getLocation().getId().equals(shiftLocationId)) {
            throw new BusinessRuleException(
                "Nhân viên không thuộc chi nhánh của ca làm"
            );
        }
    }

    private void validateEditable(WorkShift workShift) {
        SchedulePeriod schedulePeriod = workShift.getSchedulePeriod();
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                "Chỉ có thể phân công khi kỳ xếp lịch đang ở trạng thái nháp"
            );
        }
        if (workShift.getStatus() != WorkShiftStatus.OPEN
            && workShift.getStatus() != WorkShiftStatus.FILLED) {
            throw new BusinessRuleException(
                "Không thể thay đổi nhân sự của ca đã hủy hoặc hoàn thành"
            );
        }
        if (!workShift.getStartAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(
                "Không thể thay đổi nhân sự của ca đã bắt đầu"
            );
        }
    }

    private void validateScheduleQueryRange(
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
        if (ChronoUnit.DAYS.between(startDate, endDate)
            > MAX_SCHEDULE_QUERY_DAYS) {
            throw new BusinessRuleException(
                "Chỉ có thể xem lịch làm trong tối đa 93 ngày"
            );
        }
    }

    private void refreshWorkShiftStatus(WorkShift workShift) {
        List<ShiftRequirement> requirements = shiftRequirementRepository
            .findAllByWorkShiftId(workShift.getId());
        int totalMinimum = requirements.stream()
            .mapToInt(ShiftRequirement::getMinEmployees)
            .sum();
        boolean minimumStaffed = totalMinimum > 0
            && requirements.stream().allMatch(requirement ->
                shiftAssignmentRepository
                    .countByWorkShiftIdAndPositionIdAndStatusIn(
                        workShift.getId(),
                        requirement.getPosition().getId(),
                        ACTIVE_STATUSES
                    ) >= requirement.getMinEmployees()
            );
        workShift.setStatus(
            minimumStaffed ? WorkShiftStatus.FILLED : WorkShiftStatus.OPEN
        );
        workShiftRepository.saveAndFlush(workShift);
    }

    private ShiftAssignmentSummaryResponse buildSummary(WorkShift workShift) {
        List<ShiftRequirement> requirements = shiftRequirementRepository
            .findAllByWorkShiftId(workShift.getId());
        List<ShiftAssignment> assignments = findActiveAssignments(
            workShift.getId()
        );
        Map<Long, Integer> assignedByPosition = new HashMap<>();
        for (ShiftAssignment assignment : assignments) {
            assignedByPosition.merge(
                assignment.getPosition().getId(),
                1,
                Integer::sum
            );
        }

        List<AssignmentRequirementProgressResponse> progress = requirements
            .stream()
            .map(requirement -> {
                int assigned = assignedByPosition.getOrDefault(
                    requirement.getPosition().getId(),
                    0
                );
                return new AssignmentRequirementProgressResponse(
                    requirement.getId(),
                    requirement.getPosition().getId(),
                    requirement.getPosition().getCode(),
                    requirement.getPosition().getName(),
                    requirement.getMinEmployees(),
                    requirement.getMaxEmployees(),
                    assigned,
                    assigned >= requirement.getMinEmployees(),
                    assigned >= requirement.getMaxEmployees()
                );
            })
            .toList();

        int totalMinimum = progress.stream()
            .mapToInt(AssignmentRequirementProgressResponse::minEmployees)
            .sum();
        int totalMaximum = progress.stream()
            .mapToInt(AssignmentRequirementProgressResponse::maxEmployees)
            .sum();
        boolean minimumStaffed = totalMinimum > 0
            && progress.stream().allMatch(
                AssignmentRequirementProgressResponse::minimumMet
            );
        List<ShiftAssignmentResponse> assignmentResponses = assignments
            .stream()
            .map(shiftAssignmentMapper::toResponse)
            .toList();

        return new ShiftAssignmentSummaryResponse(
            workShift.getId(),
            workShift.getStatus(),
            assignments.size(),
            totalMinimum,
            totalMaximum,
            minimumStaffed,
            isEditable(workShift),
            progress,
            assignmentResponses
        );
    }

    private boolean isEditable(WorkShift workShift) {
        return workShift.getSchedulePeriod().getStatus()
            == SchedulePeriodStatus.DRAFT
            && (workShift.getStatus() == WorkShiftStatus.OPEN
                || workShift.getStatus() == WorkShiftStatus.FILLED)
            && workShift.getStartAt().isAfter(Instant.now());
    }

    private List<ShiftAssignment> findActiveAssignments(Long workShiftId) {
        return shiftAssignmentRepository
            .findAllDetailedByWorkShiftIdAndStatusIn(
                workShiftId,
                ACTIVE_STATUSES
            );
    }

    private ShiftRequirement findRequirement(
        Long workShiftId,
        Long positionId
    ) {
        return shiftRequirementRepository.findAllByWorkShiftId(workShiftId)
            .stream()
            .filter(requirement -> requirement.getPosition().getId().equals(
                positionId
            ))
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException(
                "Vị trí của nhân viên không nằm trong nhu cầu của ca"
            ));
    }

    private WorkShift findWorkShiftById(Long id) {
        return workShiftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + id
            ));
    }

    private WorkShift findWorkShiftByIdForUpdate(Long id) {
        return workShiftRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + id
            ));
    }

    private Comparator<AssignmentCandidateResponse> candidateComparator() {
        return Comparator
            .comparing(AssignmentCandidateResponse::assigned)
            .thenComparing(
                AssignmentCandidateResponse::eligible,
                Comparator.reverseOrder()
            )
            .thenComparing(
                candidate -> availabilityRank(candidate.availabilityType()),
                Comparator.reverseOrder()
            )
            .thenComparing(AssignmentCandidateResponse::fullName);
    }

    private int availabilityRank(AvailabilityType availabilityType) {
        if (availabilityType == AvailabilityType.PREFERRED) {
            return 2;
        }
        if (availabilityType == AvailabilityType.AVAILABLE) {
            return 1;
        }
        return 0;
    }

    private record AvailabilitySegment(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
    ) {
    }
}
