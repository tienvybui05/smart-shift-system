package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.openshift.AvailableOpenShiftResponse;
import com.smartshift.dto.openshift.OpenShiftClaimRequest;
import com.smartshift.dto.openshift.OpenShiftClaimResponse;
import com.smartshift.dto.openshift.OpenShiftClaimReviewRequest;
import com.smartshift.entity.OpenShiftClaim;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.enums.OpenShiftClaimStatus;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.OpenShiftClaimMapper;
import com.smartshift.mapper.ShiftAssignmentMapper;
import com.smartshift.repository.OpenShiftClaimRepository;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.AssignmentConstraintService;
import com.smartshift.service.OpenShiftClaimService;
import com.smartshift.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenShiftClaimServiceImpl implements OpenShiftClaimService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

    private final OpenShiftClaimRepository openShiftClaimRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final UserRepository userRepository;
    private final OpenShiftClaimMapper openShiftClaimMapper;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final AssignmentConstraintService assignmentConstraintService;
    private final NotificationService notificationService;

    @Override
    public List<AvailableOpenShiftResponse> getAvailableShifts(
        String username
    ) {
        User employee = findUserByUsername(username);
        validateEmployeeRole(employee);
        Instant now = Instant.now();
        Map<Long, OpenShiftClaim> pendingClaimsByShift =
            openShiftClaimRepository
                .findAllByUserUsernameOrderByCreatedAtDesc(username)
                .stream()
                .filter(claim ->
                    claim.getStatus() == OpenShiftClaimStatus.PENDING
                )
                .collect(Collectors.toMap(
                    claim -> claim.getWorkShift().getId(),
                    Function.identity(),
                    (first, ignored) -> first
                ));

        return workShiftRepository.findClaimableShifts(
            employee.getLocation().getId(),
            employee.getPosition().getId(),
            SchedulePeriodStatus.DRAFT,
            WorkShiftStatus.OPEN,
            now
        ).stream()
            .map(workShift -> toAvailableShift(
                workShift,
                employee,
                pendingClaimsByShift.get(workShift.getId())
            ))
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public List<OpenShiftClaimResponse> getMyClaims(String username) {
        User employee = findUserByUsername(username);
        validateEmployeeRole(employee);
        return openShiftClaimRepository
            .findAllByUserUsernameOrderByCreatedAtDesc(username)
            .stream()
            .map(openShiftClaimMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public OpenShiftClaimResponse createClaim(
        String username,
        OpenShiftClaimRequest request
    ) {
        lockPeriodForShift(request.workShiftId());
        WorkShift workShift = findWorkShiftForUpdate(request.workShiftId());
        User employee = userRepository.findByUsernameForUpdate(username)
            .orElseThrow(() -> userNotFound(username));
        validateEmployeeRole(employee);
        validateClaimableShift(workShift);

        ShiftRequirement requirement = findRequirement(
            workShift.getId(),
            employee.getPosition().getId()
        );
        validateNoExistingAssignment(workShift.getId(), employee.getId());
        validatePositionStillOpen(workShift, requirement);
        if (openShiftClaimRepository
            .existsByWorkShiftIdAndUserIdAndStatus(
                workShift.getId(),
                employee.getId(),
                OpenShiftClaimStatus.PENDING
            )) {
            throw new DuplicateResourceException(
                "Bạn đã gửi yêu cầu nhận ca này và đang chờ xét duyệt"
            );
        }

        AssignmentConstraintResult evaluation = assignmentConstraintService
            .evaluate(employee, workShift, requirement.getPosition());
        validateEligible(evaluation);
        OpenShiftClaim saved = openShiftClaimRepository.saveAndFlush(
            openShiftClaimMapper.toEntity(
                workShift,
                employee,
                request.reason()
            )
        );
        notifyReviewersOfNewClaim(saved);
        return openShiftClaimMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public OpenShiftClaimResponse cancelClaim(Long id, String username) {
        lockPeriodForClaim(id);
        OpenShiftClaim claim = findClaimForUpdate(id);
        if (!claim.getUser().getUsername().equals(username)) {
            throw claimNotFound(id);
        }
        if (claim.getStatus() != OpenShiftClaimStatus.PENDING) {
            throw new BusinessRuleException(
                "Chỉ có thể hủy yêu cầu nhận ca đang chờ duyệt"
            );
        }
        if (!claim.getWorkShift().getStartAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(
                "Không thể hủy yêu cầu của ca đã bắt đầu"
            );
        }

        claim.setStatus(OpenShiftClaimStatus.CANCELLED);
        claim.setReviewedBy(null);
        claim.setReviewedAt(null);
        claim.setReviewerNote(null);
        claim.setAssignment(null);
        return openShiftClaimMapper.toResponse(
            openShiftClaimRepository.saveAndFlush(claim)
        );
    }

    @Override
    public List<OpenShiftClaimResponse> getClaims(
        OpenShiftClaimStatus status,
        Long locationId,
        String reviewerUsername
    ) {
        User reviewer = findUserByUsername(reviewerUsername);
        Long effectiveLocationId = resolveReviewLocation(
            reviewer,
            locationId
        );
        return openShiftClaimRepository.search(status, effectiveLocationId)
            .stream()
            .map(openShiftClaimMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public OpenShiftClaimResponse reviewClaim(
        Long id,
        OpenShiftClaimReviewRequest request,
        String reviewerUsername
    ) {
        validateReviewStatus(request.status());
        lockPeriodForClaim(id);
        OpenShiftClaim claim = findClaimForUpdate(id);
        User reviewer = findUserByUsername(reviewerUsername);
        validateReviewerRole(reviewer);
        validateReviewerLocation(reviewer, claim);
        if (reviewer.getId().equals(claim.getUser().getId())) {
            throw new BusinessRuleException(
                "Không thể tự xét duyệt yêu cầu nhận ca của chính mình"
            );
        }
        if (claim.getStatus() != OpenShiftClaimStatus.PENDING) {
            throw new BusinessRuleException(
                "Chỉ có thể xét duyệt yêu cầu nhận ca đang chờ"
            );
        }

        if (request.status() == OpenShiftClaimStatus.REJECTED) {
            markReviewed(claim, reviewer, request);
            OpenShiftClaim savedClaim = openShiftClaimRepository
                .saveAndFlush(claim);
            notifyEmployeeOfClaimResult(savedClaim);
            return openShiftClaimMapper.toResponse(savedClaim);
        }

        approveClaim(claim, reviewer, request);
        notifyEmployeeOfClaimResult(claim);
        return openShiftClaimMapper.toResponse(claim);
    }

    private Optional<AvailableOpenShiftResponse> toAvailableShift(
        WorkShift workShift,
        User employee,
        OpenShiftClaim pendingClaim
    ) {
        Optional<ShiftRequirement> requirementResult =
            shiftRequirementRepository.findByWorkShiftIdAndPositionId(
                workShift.getId(),
                employee.getPosition().getId()
            );
        if (requirementResult.isEmpty()) {
            return Optional.empty();
        }
        ShiftRequirement requirement = requirementResult.get();
        long assigned = countAssigned(workShift, requirement);
        if (assigned >= requirement.getMinEmployees()) {
            return Optional.empty();
        }

        AssignmentConstraintResult evaluation = assignmentConstraintService
            .evaluate(employee, workShift, requirement.getPosition());
        if (!evaluation.eligible()) {
            return Optional.empty();
        }

        SchedulePeriod period = workShift.getSchedulePeriod();
        ZoneId zoneId = ZoneId.of(period.getLocation().getTimezone());
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);
        long workMinutes = Duration.between(
            workShift.getStartAt(),
            workShift.getEndAt()
        ).toMinutes() - workShift.getBreakMinutes();
        return Optional.of(new AvailableOpenShiftResponse(
            workShift.getId(),
            period.getId(),
            period.getName(),
            period.getLocation().getId(),
            period.getLocation().getName(),
            workShift.getShiftTemplate() == null
                ? "Ca tùy chỉnh"
                : workShift.getShiftTemplate().getName(),
            workShift.getShiftTemplate() == null
                ? null
                : workShift.getShiftTemplate().getColorCode(),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            localEnd.toLocalDate().isAfter(localStart.toLocalDate()),
            workShift.getBreakMinutes(),
            workMinutes,
            requirement.getPosition().getId(),
            requirement.getPosition().getName(),
            requirement.getMinEmployees(),
            (int) assigned,
            requirement.getMinEmployees() - (int) assigned,
            openShiftClaimRepository.countByWorkShiftIdAndStatus(
                workShift.getId(),
                OpenShiftClaimStatus.PENDING
            ),
            evaluation.availabilityType(),
            evaluation.projectedDailyHours(),
            employee.getMaxHoursPerDay(),
            evaluation.projectedWeeklyHours(),
            employee.getMaxHoursPerWeek(),
            pendingClaim == null ? null : pendingClaim.getId()
        ));
    }

    private void approveClaim(
        OpenShiftClaim claim,
        User reviewer,
        OpenShiftClaimReviewRequest request
    ) {
        WorkShift workShift = claim.getWorkShift();
        validateClaimableShift(workShift);
        User employee = userRepository.findByIdForUpdate(
            claim.getUser().getId()
        ).orElseThrow(() -> new ResourceNotFoundException(
            "Không tìm thấy nhân viên của yêu cầu nhận ca"
        ));
        validateEmployeeRole(employee);
        ShiftRequirement requirement = findRequirement(
            workShift.getId(),
            employee.getPosition().getId()
        );
        validateNoExistingAssignment(workShift.getId(), employee.getId());
        validatePositionStillOpen(workShift, requirement);

        AssignmentConstraintResult evaluation = assignmentConstraintService
            .evaluate(employee, workShift, requirement.getPosition());
        validateEligible(evaluation);
        ShiftAssignment assignment = shiftAssignmentMapper.toClaimEntity(
            workShift,
            employee,
            reviewer,
            "Nhận ca trống theo yêu cầu #" + claim.getId()
        );
        ShiftAssignment savedAssignment = shiftAssignmentRepository
            .saveAndFlush(assignment);

        claim.setUser(employee);
        claim.setAssignment(savedAssignment);
        markReviewed(claim, reviewer, request);
        openShiftClaimRepository.saveAndFlush(claim);
        refreshWorkShiftStatus(workShift);
        rejectRemainingClaimsIfPositionIsFilled(
            workShift,
            requirement,
            reviewer
        );
    }

    private void markReviewed(
        OpenShiftClaim claim,
        User reviewer,
        OpenShiftClaimReviewRequest request
    ) {
        claim.setStatus(request.status());
        claim.setReviewedBy(reviewer);
        claim.setReviewedAt(Instant.now());
        claim.setReviewerNote(normalizeNullableText(request.reviewerNote()));
    }

    private void rejectRemainingClaimsIfPositionIsFilled(
        WorkShift workShift,
        ShiftRequirement requirement,
        User reviewer
    ) {
        if (countAssigned(workShift, requirement)
            < requirement.getMinEmployees()) {
            return;
        }
        List<OpenShiftClaim> remainingClaims = openShiftClaimRepository
            .findAllByShiftPositionAndStatusForUpdate(
                workShift.getId(),
                requirement.getPosition().getId(),
                OpenShiftClaimStatus.PENDING
            );
        Instant reviewedAt = Instant.now();
        for (OpenShiftClaim remainingClaim : remainingClaims) {
            remainingClaim.setStatus(OpenShiftClaimStatus.REJECTED);
            remainingClaim.setReviewedBy(reviewer);
            remainingClaim.setReviewedAt(reviewedAt);
            remainingClaim.setReviewerNote(
                "Ca đã đủ nhân sự sau khi một yêu cầu khác được duyệt"
            );
        }
        openShiftClaimRepository.saveAll(remainingClaims);
        openShiftClaimRepository.flush();
        for (OpenShiftClaim remainingClaim : remainingClaims) {
            notifyEmployeeOfClaimResult(remainingClaim);
        }
    }

    private void notifyReviewersOfNewClaim(OpenShiftClaim claim) {
        List<User> reviewers = userRepository
            .findActiveNotificationReviewersForLocation(
                claim.getUser().getLocation().getId()
            );
        notificationService.createNotifications(
            reviewers,
            NotificationType.OPEN_SHIFT_CLAIM_CREATED,
            "Có yêu cầu nhận ca mới",
            claim.getUser().getFullName()
                + " vừa gửi yêu cầu nhận ca #" + claim.getId() + ".",
            NotificationReferenceType.OPEN_SHIFT_CLAIM,
            claim.getId()
        );
    }

    private void notifyEmployeeOfClaimResult(OpenShiftClaim claim) {
        boolean approved = claim.getStatus()
            == OpenShiftClaimStatus.APPROVED;
        notificationService.createNotification(
            claim.getUser(),
            approved
                ? NotificationType.OPEN_SHIFT_CLAIM_APPROVED
                : NotificationType.OPEN_SHIFT_CLAIM_REJECTED,
            approved
                ? "Yêu cầu nhận ca đã được duyệt"
                : "Yêu cầu nhận ca đã bị từ chối",
            "Yêu cầu nhận ca #" + claim.getId() + " của bạn "
                + (approved ? "đã được duyệt." : "đã bị từ chối."),
            NotificationReferenceType.OPEN_SHIFT_CLAIM,
            claim.getId()
        );
    }

    private void validateClaimableShift(WorkShift workShift) {
        if (workShift.getSchedulePeriod().getStatus()
            != SchedulePeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                "Chỉ có thể nhận ca thuộc kỳ xếp lịch đang ở trạng thái nháp"
            );
        }
        if (workShift.getStatus() != WorkShiftStatus.OPEN
            && workShift.getStatus() != WorkShiftStatus.FILLED) {
            throw new BusinessRuleException(
                "Ca làm không còn mở để nhận thêm nhân sự"
            );
        }
        if (!workShift.getStartAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(
                "Không thể đăng ký nhận ca đã bắt đầu"
            );
        }
    }

    private void validatePositionStillOpen(
        WorkShift workShift,
        ShiftRequirement requirement
    ) {
        if (countAssigned(workShift, requirement)
            >= requirement.getMinEmployees()) {
            throw new BusinessRuleException(
                "Vị trí '" + requirement.getPosition().getName()
                    + "' của ca này đã đủ nhân sự tối thiểu"
            );
        }
    }

    private void validateNoExistingAssignment(
        Long workShiftId,
        Long userId
    ) {
        if (shiftAssignmentRepository.findByWorkShiftIdAndUserId(
            workShiftId,
            userId
        ).isPresent()) {
            throw new DuplicateResourceException(
                "Nhân viên đã được phân công vào ca này"
            );
        }
    }

    private void validateEligible(AssignmentConstraintResult evaluation) {
        if (!evaluation.eligible()) {
            throw new BusinessRuleException(
                String.join("; ", evaluation.violations())
            );
        }
    }

    private long countAssigned(
        WorkShift workShift,
        ShiftRequirement requirement
    ) {
        return shiftAssignmentRepository
            .countByWorkShiftIdAndPositionIdAndStatusIn(
                workShift.getId(),
                requirement.getPosition().getId(),
                ACTIVE_ASSIGNMENT_STATUSES
            );
    }

    private void refreshWorkShiftStatus(WorkShift workShift) {
        List<ShiftRequirement> requirements = shiftRequirementRepository
            .findAllByWorkShiftId(workShift.getId());
        int totalMinimum = requirements.stream()
            .mapToInt(ShiftRequirement::getMinEmployees)
            .sum();
        boolean minimumStaffed = totalMinimum > 0
            && requirements.stream().allMatch(requirement ->
                countAssigned(workShift, requirement)
                    >= requirement.getMinEmployees()
            );
        workShift.setStatus(
            minimumStaffed ? WorkShiftStatus.FILLED : WorkShiftStatus.OPEN
        );
        workShiftRepository.saveAndFlush(workShift);
    }

    private ShiftRequirement findRequirement(
        Long workShiftId,
        Long positionId
    ) {
        return shiftRequirementRepository
            .findByWorkShiftIdAndPositionId(workShiftId, positionId)
            .orElseThrow(() -> new BusinessRuleException(
                "Vị trí của nhân viên không nằm trong nhu cầu của ca"
            ));
    }

    private Long resolveReviewLocation(
        User reviewer,
        Long requestedLocationId
    ) {
        validateReviewerRole(reviewer);
        if (isAdmin(reviewer)) {
            return requestedLocationId;
        }
        Long managerLocationId = reviewer.getLocation().getId();
        if (requestedLocationId != null
            && !requestedLocationId.equals(managerLocationId)) {
            throw new BusinessRuleException(
                "Quản lý chỉ có thể xem yêu cầu tại chi nhánh của mình"
            );
        }
        return managerLocationId;
    }

    private void validateReviewerRole(User reviewer) {
        String roleName = reviewer.getRole().getName();
        if (!ADMIN_ROLE.equals(roleName)
            && !MANAGER_ROLE.equals(roleName)) {
            throw new BusinessRuleException(
                "Tài khoản không có quyền xét duyệt yêu cầu nhận ca"
            );
        }
    }

    private void validateReviewerLocation(
        User reviewer,
        OpenShiftClaim claim
    ) {
        if (!isAdmin(reviewer)
            && !reviewer.getLocation().getId().equals(
                claim.getWorkShift()
                    .getSchedulePeriod()
                    .getLocation()
                    .getId()
            )) {
            throw claimNotFound(claim.getId());
        }
    }

    private void validateEmployeeRole(User employee) {
        if (employee.getRole() == null
            || !EMPLOYEE_ROLE.equals(employee.getRole().getName())) {
            throw new BusinessRuleException(
                "Chỉ tài khoản nhân viên mới có thể đăng ký nhận ca trống"
            );
        }
        if (!employee.isActive()) {
            throw new BusinessRuleException(
                "Tài khoản nhân viên đang ngừng hoạt động"
            );
        }
    }

    private void validateReviewStatus(OpenShiftClaimStatus status) {
        if (status != OpenShiftClaimStatus.APPROVED
            && status != OpenShiftClaimStatus.REJECTED) {
            throw new BusinessRuleException(
                "Trạng thái xét duyệt chỉ có thể là APPROVED hoặc REJECTED"
            );
        }
    }

    private boolean isAdmin(User user) {
        return ADMIN_ROLE.equals(user.getRole().getName());
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> userNotFound(username));
    }

    private WorkShift findWorkShiftForUpdate(Long id) {
        return workShiftRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + id
            ));
    }

    private OpenShiftClaim findClaimForUpdate(Long id) {
        return openShiftClaimRepository.findByIdForUpdate(id)
            .orElseThrow(() -> claimNotFound(id));
    }

    private void lockPeriodForShift(Long workShiftId) {
        Long periodId = workShiftRepository
            .findSchedulePeriodIdById(workShiftId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + workShiftId
            ));
        lockPeriod(periodId);
    }

    private void lockPeriodForClaim(Long claimId) {
        Long periodId = openShiftClaimRepository
            .findSchedulePeriodIdByClaimId(claimId)
            .orElseThrow(() -> claimNotFound(claimId));
        lockPeriod(periodId);
    }

    private void lockPeriod(Long periodId) {
        schedulePeriodRepository.findByIdForUpdate(periodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + periodId
            ));
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResourceNotFoundException userNotFound(String username) {
        return new ResourceNotFoundException(
            "Không tìm thấy tài khoản '" + username + "'"
        );
    }

    private ResourceNotFoundException claimNotFound(Long id) {
        return new ResourceNotFoundException(
            "Không tìm thấy yêu cầu nhận ca có id " + id
        );
    }
}
