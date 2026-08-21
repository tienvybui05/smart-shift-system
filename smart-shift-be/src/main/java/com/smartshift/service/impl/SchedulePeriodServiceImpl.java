package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.schedule.SchedulePeriodRequest;
import com.smartshift.dto.schedule.SchedulePeriodResponse;
import com.smartshift.dto.schedule.SchedulePublicationCheckResponse;
import com.smartshift.dto.schedule.SchedulePublicationIssueResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.SchedulePeriodMapper;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.AssignmentConstraintService;
import com.smartshift.service.SchedulePeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulePeriodServiceImpl implements SchedulePeriodService {

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final SchedulePeriodMapper schedulePeriodMapper;
    private final AssignmentConstraintService assignmentConstraintService;

    @Override
    public List<SchedulePeriodResponse> getSchedulePeriods(
        Long locationId,
        SchedulePeriodStatus status
    ) {
        return schedulePeriodRepository.search(locationId, status)
            .stream()
            .map(schedulePeriodMapper::toResponse)
            .toList();
    }

    @Override
    public SchedulePeriodResponse getSchedulePeriodById(Long id) {
        return schedulePeriodMapper.toResponse(findSchedulePeriodById(id));
    }

    @Override
    @Transactional
    public SchedulePeriodResponse createSchedulePeriod(
        SchedulePeriodRequest request,
        String currentUsername
    ) {
        validateDateRange(request);
        Location location = findActiveLocationById(request.locationId());
        validateNoOverlap(location.getId(), request, null);
        User createdBy = findUserByUsername(currentUsername);

        SchedulePeriod schedulePeriod = schedulePeriodMapper.toEntity(
            request,
            location,
            createdBy
        );
        return schedulePeriodMapper.toResponse(
            schedulePeriodRepository.save(schedulePeriod)
        );
    }

    @Override
    @Transactional
    public SchedulePeriodResponse updateSchedulePeriod(
        Long id,
        SchedulePeriodRequest request
    ) {
        validateDateRange(request);
        SchedulePeriod schedulePeriod = findSchedulePeriodByIdForUpdate(id);
        validateEditable(schedulePeriod);
        Location location = findActiveLocationById(request.locationId());
        validateNoOverlap(location.getId(), request, id);

        schedulePeriodMapper.updateEntity(request, schedulePeriod, location);
        return schedulePeriodMapper.toResponse(
            schedulePeriodRepository.save(schedulePeriod)
        );
    }

    @Override
    public SchedulePublicationCheckResponse checkPublication(Long id) {
        return buildPublicationCheck(findSchedulePeriodById(id));
    }

    @Override
    @Transactional
    public SchedulePeriodResponse publishSchedulePeriod(
        Long id,
        String currentUsername
    ) {
        SchedulePeriod schedulePeriod = findSchedulePeriodByIdForUpdate(id);
        SchedulePublicationCheckResponse publicationCheck =
            buildPublicationCheck(schedulePeriod);
        if (!publicationCheck.canPublish()) {
            throw new BusinessRuleException(
                buildPublicationErrorMessage(publicationCheck)
            );
        }

        User publishedBy = findUserByUsername(currentUsername);
        schedulePeriod.setStatus(SchedulePeriodStatus.PUBLISHED);
        schedulePeriod.setPublishedBy(publishedBy);
        schedulePeriod.setPublishedAt(Instant.now());
        return schedulePeriodMapper.toResponse(
            schedulePeriodRepository.save(schedulePeriod)
        );
    }

    @Override
    @Transactional
    public SchedulePeriodResponse lockSchedulePeriod(Long id) {
        SchedulePeriod schedulePeriod = findSchedulePeriodByIdForUpdate(id);
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.PUBLISHED) {
            throw new BusinessRuleException(
                "Chỉ có thể khóa kỳ xếp lịch đã được công bố"
            );
        }
        schedulePeriod.setStatus(SchedulePeriodStatus.LOCKED);
        return schedulePeriodMapper.toResponse(
            schedulePeriodRepository.save(schedulePeriod)
        );
    }

    private SchedulePublicationCheckResponse buildPublicationCheck(
        SchedulePeriod schedulePeriod
    ) {
        List<WorkShift> workShifts = workShiftRepository.search(
            schedulePeriod.getId(),
            null
        );
        List<SchedulePublicationIssueResponse> issues = new ArrayList<>();
        Set<Long> shiftsWithoutRequirements = new HashSet<>();
        Set<Long> understaffedShifts = new HashSet<>();
        int activeShifts = 0;
        int cancelledShifts = 0;
        int totalMinimumEmployees = 0;
        int totalAssignedEmployees = 0;
        int invalidAssignments = 0;

        for (WorkShift workShift : workShifts) {
            if (workShift.getStatus() == WorkShiftStatus.CANCELLED) {
                cancelledShifts++;
                continue;
            }
            activeShifts++;

            List<ShiftRequirement> requirements = shiftRequirementRepository
                .findAllByWorkShiftId(workShift.getId());
            List<ShiftAssignment> assignments = shiftAssignmentRepository
                .findAllDetailedByWorkShiftIdAndStatusIn(
                    workShift.getId(),
                    ACTIVE_ASSIGNMENT_STATUSES
                );
            totalAssignedEmployees += assignments.size();

            if (requirements.isEmpty()) {
                invalidAssignments += assignments.size();
                shiftsWithoutRequirements.add(workShift.getId());
                issues.add(toPublicationIssue(
                    workShift,
                    null,
                    0,
                    0,
                    "NO_REQUIREMENTS",
                    "Ca chưa được khai báo nhu cầu nhân sự"
                ));
                continue;
            }

            Map<Long, ShiftRequirement> requirementByPosition = new LinkedHashMap<>();
            for (ShiftRequirement requirement : requirements) {
                requirementByPosition.put(
                    requirement.getPosition().getId(),
                    requirement
                );
            }

            List<ShiftAssignment> validAssignments = new ArrayList<>();
            Map<Long, List<String>> invalidMessagesByPosition =
                new LinkedHashMap<>();
            for (ShiftAssignment assignment : assignments) {
                Position assignedPosition = assignment.getPosition();
                ShiftRequirement assignedRequirement = requirementByPosition
                    .get(assignedPosition.getId());
                List<String> violations = new ArrayList<>();
                if (assignedRequirement == null) {
                    violations.add(
                        "Vị trí không còn nằm trong nhu cầu của ca"
                    );
                }

                AssignmentConstraintResult evaluation =
                    assignmentConstraintService.evaluate(
                        assignment.getUser(),
                        workShift,
                        assignedPosition
                    );
                violations.addAll(evaluation.violations());
                if (violations.isEmpty()) {
                    validAssignments.add(assignment);
                    continue;
                }

                invalidAssignments++;
                invalidMessagesByPosition.computeIfAbsent(
                    assignedPosition.getId(),
                    ignored -> new ArrayList<>()
                ).add(
                    assignment.getUser().getFullName() + ": "
                        + String.join("; ", violations)
                );
            }

            for (Map.Entry<Long, List<String>> entry
                : invalidMessagesByPosition.entrySet()) {
                ShiftRequirement requirement = requirementByPosition.get(
                    entry.getKey()
                );
                int validAssigned = (int) validAssignments.stream()
                    .filter(assignment -> assignment.getPosition().getId()
                        .equals(entry.getKey()))
                    .count();
                issues.add(toPublicationIssue(
                    workShift,
                    requirement,
                    requirement == null ? 0 : requirement.getMinEmployees(),
                    validAssigned,
                    "INVALID_ASSIGNMENT",
                    "Phân công không hợp lệ: "
                        + String.join(" | ", entry.getValue())
                ));
            }

            for (ShiftRequirement requirement : requirements) {
                int minimum = requirement.getMinEmployees();
                int assigned = (int) validAssignments.stream()
                    .filter(assignment -> assignment.getPosition().getId()
                        .equals(requirement.getPosition().getId()))
                    .count();
                totalMinimumEmployees += minimum;
                if (assigned < minimum) {
                    understaffedShifts.add(workShift.getId());
                    int shortage = minimum - assigned;
                    issues.add(toPublicationIssue(
                        workShift,
                        requirement,
                        minimum,
                        assigned,
                        "UNDERSTAFFED",
                        "Còn thiếu " + shortage + " nhân viên "
                            + requirement.getPosition().getName()
                    ));
                } else if (assigned > requirement.getMaxEmployees()) {
                    issues.add(toPublicationIssue(
                        workShift,
                        requirement,
                        requirement.getMaxEmployees(),
                        assigned,
                        "OVERSTAFFED",
                        "Vượt tối đa "
                            + (assigned - requirement.getMaxEmployees())
                            + " nhân viên "
                            + requirement.getPosition().getName()
                    ));
                }
            }
        }

        List<String> blockers = new ArrayList<>();
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.DRAFT) {
            blockers.add(
                "Chỉ kỳ xếp lịch ở trạng thái Nháp mới có thể được công bố"
            );
        }
        if (activeShifts == 0) {
            blockers.add(
                "Kỳ xếp lịch phải có ít nhất một ca không bị hủy"
            );
        }
        boolean canPublish = blockers.isEmpty() && issues.isEmpty();

        return new SchedulePublicationCheckResponse(
            schedulePeriod.getId(),
            schedulePeriod.getStatus(),
            workShifts.size(),
            activeShifts,
            cancelledShifts,
            totalMinimumEmployees,
            totalAssignedEmployees,
            shiftsWithoutRequirements.size(),
            understaffedShifts.size(),
            invalidAssignments,
            canPublish,
            List.copyOf(blockers),
            List.copyOf(issues)
        );
    }

    private SchedulePublicationIssueResponse toPublicationIssue(
        WorkShift workShift,
        ShiftRequirement requirement,
        int requiredEmployees,
        int assignedEmployees,
        String issueCode,
        String message
    ) {
        ZoneId zoneId = ZoneId.of(
            workShift.getSchedulePeriod().getLocation().getTimezone()
        );
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);
        return new SchedulePublicationIssueResponse(
            workShift.getId(),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            workShift.getShiftTemplate() == null
                ? "Ca tùy chỉnh"
                : workShift.getShiftTemplate().getName(),
            requirement == null ? null : requirement.getPosition().getId(),
            requirement == null ? null : requirement.getPosition().getName(),
            requiredEmployees,
            assignedEmployees,
            issueCode,
            message
        );
    }

    private String buildPublicationErrorMessage(
        SchedulePublicationCheckResponse publicationCheck
    ) {
        if (!publicationCheck.blockers().isEmpty()) {
            return "Không thể công bố lịch: "
                + publicationCheck.blockers().get(0);
        }
        return "Không thể công bố lịch: còn "
            + publicationCheck.shiftIssues().size()
            + " vấn đề cần xử lý, gồm "
            + publicationCheck.invalidAssignments()
            + " phân công vi phạm ràng buộc";
    }

    private void validateDateRange(SchedulePeriodRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
    }

    private void validateNoOverlap(
        Long locationId,
        SchedulePeriodRequest request,
        Long excludedId
    ) {
        boolean overlapping = schedulePeriodRepository.existsOverlappingPeriod(
            locationId,
            request.startDate(),
            request.endDate(),
            excludedId
        );
        if (overlapping) {
            throw new DuplicateResourceException(
                "Khoảng ngày này bị trùng với một kỳ xếp lịch khác của chi nhánh"
            );
        }
    }

    private void validateEditable(SchedulePeriod schedulePeriod) {
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                "Chỉ có thể cập nhật kỳ xếp lịch đang ở trạng thái nháp"
            );
        }
    }

    private SchedulePeriod findSchedulePeriodById(Long id) {
        return schedulePeriodRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + id
            ));
    }

    private SchedulePeriod findSchedulePeriodByIdForUpdate(Long id) {
        return schedulePeriodRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + id
            ));
    }

    private Location findActiveLocationById(Long id) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy chi nhánh có id " + id
            ));
        if (!location.isActive()) {
            throw new BusinessRuleException(
                "Không thể tạo kỳ xếp lịch cho chi nhánh đang ngừng hoạt động"
            );
        }
        return location;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
    }
}
