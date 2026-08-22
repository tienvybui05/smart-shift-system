package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentCandidateResponse;
import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.assignment.AssignmentRequirementProgressResponse;
import com.smartshift.dto.assignment.MyWorkScheduleResponse;
import com.smartshift.dto.assignment.ShiftAssignmentRequest;
import com.smartshift.dto.assignment.ShiftAssignmentResponse;
import com.smartshift.dto.assignment.ShiftAssignmentSummaryResponse;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.ShiftAssignmentMapper;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.AssignmentConstraintService;
import com.smartshift.service.ShiftAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final WorkShiftRepository workShiftRepository;
    private final UserRepository userRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final AssignmentConstraintService assignmentConstraintService;

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
        ShiftRequirement requirement = findRequirement(
            workShiftId,
            positionId
        );

        Set<Long> assignedUserIds = new HashSet<>(
            findActiveAssignments(workShiftId).stream()
                .map(assignment -> assignment.getUser().getId())
                .toList()
        );
        Long locationId = workShift.getSchedulePeriod().getLocation().getId();

        return userRepository
            .findSchedulableByLocationAndPosition(
                locationId,
                positionId
            )
            .stream()
            .map(user -> evaluateCandidate(
                user,
                workShift,
                requirement,
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
        lockSchedulePeriodForShift(workShiftId);
        WorkShift workShift = findWorkShiftByIdForUpdate(workShiftId);
        validateEditable(workShift);

        User employee = userRepository.findByIdForUpdate(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy nhân viên có id " + request.userId()
            ));
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

        AssignmentConstraintResult evaluation = assignmentConstraintService
            .evaluate(employee, workShift, requirement.getPosition());
        if (!evaluation.eligible()) {
            throw new BusinessRuleException(
                String.join("; ", evaluation.violations())
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
        Long workShiftId = shiftAssignmentRepository
            .findWorkShiftIdByAssignmentId(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy phân công có id " + assignmentId
            ));
        lockSchedulePeriodForShift(workShiftId);
        WorkShift workShift = findWorkShiftByIdForUpdate(workShiftId);
        validateEditable(workShift);
        ShiftAssignment assignment = shiftAssignmentRepository
            .findByIdForUpdate(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy phân công có id " + assignmentId
            ));

        shiftAssignmentRepository.delete(assignment);
        shiftAssignmentRepository.flush();
        refreshWorkShiftStatus(workShift);
        return buildSummary(workShift);
    }

    private AssignmentCandidateResponse evaluateCandidate(
        User user,
        WorkShift workShift,
        ShiftRequirement requirement,
        boolean alreadyAssigned
    ) {
        AssignmentConstraintResult evaluation = assignmentConstraintService
            .evaluate(user, workShift, requirement.getPosition());
        List<String> reasons = new ArrayList<>(evaluation.violations());
        if (alreadyAssigned) {
            reasons.add(0, "Nhân viên đã được phân công vào ca này");
        }

        return new AssignmentCandidateResponse(
            user.getId(),
            user.getEmployeeCode(),
            user.getFullName(),
            user.getEmploymentType(),
            evaluation.availabilityType(),
            alreadyAssigned,
            !alreadyAssigned && evaluation.eligible(),
            List.copyOf(reasons),
            evaluation.projectedDailyHours(),
            user.getMaxHoursPerDay(),
            evaluation.projectedWeeklyHours(),
            user.getMaxHoursPerWeek()
        );
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

    private void lockSchedulePeriodForShift(Long workShiftId) {
        Long schedulePeriodId = workShiftRepository
            .findSchedulePeriodIdById(workShiftId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + workShiftId
            ));
        schedulePeriodRepository.findByIdForUpdate(schedulePeriodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + schedulePeriodId
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
}
