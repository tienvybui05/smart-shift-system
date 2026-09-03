package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.shiftswap.ShiftSwapCandidateResponse;
import com.smartshift.dto.shiftswap.ShiftSwapCreateRequest;
import com.smartshift.dto.shiftswap.ShiftSwapRequestResponse;
import com.smartshift.dto.shiftswap.ShiftSwapRespondRequest;
import com.smartshift.dto.shiftswap.ShiftSwapReviewRequest;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftSwapRequest;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentSource;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.enums.ScheduleAuditAction;
import com.smartshift.enums.ScheduleAuditTargetType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.ShiftSwapStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.ShiftSwapMapper;
import com.smartshift.repository.AttendanceRepository;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftSwapRequestRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.AssignmentConstraintService;
import com.smartshift.service.NotificationService;
import com.smartshift.service.ScheduleAuditService;
import com.smartshift.service.SchedulingAccessService;
import com.smartshift.service.ShiftSwapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.smartshift.service.ScheduleAuditSnapshots.assignment;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftSwapServiceImpl implements ShiftSwapService {

    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

    private static final List<ShiftSwapStatus> ACTIVE_REQUEST_STATUSES =
        List.of(ShiftSwapStatus.PENDING, ShiftSwapStatus.ACCEPTED);

    private final ShiftSwapRequestRepository shiftSwapRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final UserRepository userRepository;
    private final AssignmentConstraintService assignmentConstraintService;
    private final SchedulingAccessService schedulingAccessService;
    private final NotificationService notificationService;
    private final ScheduleAuditService scheduleAuditService;
    private final ShiftSwapMapper shiftSwapMapper;

    @Override
    public List<ShiftSwapRequestResponse> getMyRequests(String username) {
        User actor = findUser(username);
        return shiftSwapRequestRepository.findMyDetailed(username).stream()
            .map(request -> shiftSwapMapper.toResponse(request, actor))
            .toList();
    }

    @Override
    public List<ShiftSwapRequestResponse> getAvailableGiveaways(
        String username
    ) {
        User actor = findEmployee(username);
        if (actor.getLocation() == null || actor.getPosition() == null) {
            return List.of();
        }

        return shiftSwapRequestRepository.findAvailableGiveawaysDetailed(
                actor.getId(),
                actor.getLocation().getId(),
                actor.getPosition().getId(),
                Instant.now()
            ).stream()
            .filter(request -> isGiveawayAvailableTo(request, actor))
            .map(request -> shiftSwapMapper.toResponse(request, actor))
            .toList();
    }

    @Override
    public List<ShiftSwapCandidateResponse> getSwapCandidates(
        String username,
        Long requesterAssignmentId
    ) {
        User requester = findEmployee(username);
        ShiftAssignment requesterAssignment = shiftAssignmentRepository
            .findDetailedById(requesterAssignmentId)
            .orElseThrow(() -> assignmentNotFound(requesterAssignmentId));
        validateAssignmentOwner(requesterAssignment, requester);
        validateRequestableAssignment(requesterAssignment);
        ensureAssignmentHasNoActiveRequest(requesterAssignment.getId());

        return shiftAssignmentRepository.findSwapCandidates(
                requesterAssignment.getWorkShift().getSchedulePeriod().getId(),
                requesterAssignment.getPosition().getId(),
                requester.getId(),
                ACTIVE_ASSIGNMENT_STATUSES,
                Instant.now()
            ).stream()
            .filter(candidate -> !candidate.getWorkShift().getId().equals(
                requesterAssignment.getWorkShift().getId()
            ))
            .map(candidate -> toCandidate(requesterAssignment, candidate))
            .toList();
    }

    @Override
    @Transactional
    public ShiftSwapRequestResponse createRequest(
        String username,
        ShiftSwapCreateRequest request
    ) {
        lockSchedulePeriodForAssignment(request.requesterAssignmentId());
        User requester = findEmployeeForUpdate(username);
        ShiftAssignment requesterAssignment = findAssignmentForUpdate(
            request.requesterAssignmentId()
        );
        validateAssignmentOwner(requesterAssignment, requester);
        validateRequestableAssignment(requesterAssignment);
        ensureAssignmentHasNoActiveRequest(requesterAssignment.getId());

        ShiftAssignment targetAssignment = null;
        User targetUser = null;
        if (request.targetAssignmentId() != null) {
            if (request.targetAssignmentId().equals(
                request.requesterAssignmentId()
            )) {
                throw new BusinessRuleException(
                    "Ca đối ứng phải khác ca bạn muốn đổi"
                );
            }
            targetAssignment = findAssignmentForUpdate(
                request.targetAssignmentId()
            );
            validateSwapPair(requesterAssignment, targetAssignment);
            ensureAssignmentHasNoActiveRequest(targetAssignment.getId());
            targetUser = targetAssignment.getUser();
            validateTransferEligibility(
                requester,
                requesterAssignment,
                targetUser,
                targetAssignment
            );
        }

        ShiftSwapRequest swapRequest = new ShiftSwapRequest();
        swapRequest.setRequesterUser(requester);
        swapRequest.setRequesterAssignment(requesterAssignment);
        swapRequest.setTargetUser(targetUser);
        swapRequest.setTargetAssignment(targetAssignment);
        swapRequest.setStatus(ShiftSwapStatus.PENDING);
        swapRequest.setReason(normalizeText(request.reason()));
        ShiftSwapRequest saved = shiftSwapRequestRepository.saveAndFlush(
            swapRequest
        );

        if (targetUser == null) {
            notifyEligibleEmployeesOfGiveaway(saved);
        } else {
            notifyTargetOfDirectSwap(saved);
        }
        return shiftSwapMapper.toResponse(saved, requester);
    }

    @Override
    @Transactional
    public ShiftSwapRequestResponse respondToRequest(
        String username,
        Long id,
        ShiftSwapRespondRequest response
    ) {
        validateResponseStatus(response.status());
        lockSchedulePeriodForRequest(id);
        ShiftSwapRequest request = findRequestForUpdate(id);
        User actor = findEmployeeForUpdate(username);

        if (request.getStatus() != ShiftSwapStatus.PENDING) {
            throw new BusinessRuleException(
                "Chỉ có thể phản hồi yêu cầu đang chờ người nhận"
            );
        }
        if (request.getRequesterUser().getId().equals(actor.getId())) {
            throw new BusinessRuleException(
                "Bạn không thể tự nhận hoặc phản hồi yêu cầu của chính mình"
            );
        }

        boolean publicGiveaway = request.getTargetAssignment() == null
            && request.getTargetUser() == null;
        if (!publicGiveaway && !request.getTargetUser().getId().equals(
            actor.getId()
        )) {
            throw new BusinessRuleException(
                "Yêu cầu đổi ca này được gửi cho một nhân viên khác"
            );
        }
        if (publicGiveaway && response.status() == ShiftSwapStatus.DECLINED) {
            throw new BusinessRuleException(
                "Ca nhường công khai không cần thao tác từ chối"
            );
        }

        if (response.status() == ShiftSwapStatus.ACCEPTED) {
            request.setTargetUser(actor);
            validateRequestStateForTransfer(request);
        }

        request.setStatus(response.status());
        request.setResponseNote(normalizeText(response.responseNote()));
        request.setRespondedAt(Instant.now());
        ShiftSwapRequest saved = shiftSwapRequestRepository.saveAndFlush(
            request
        );

        notifyRequesterOfResponse(saved);
        if (saved.getStatus() == ShiftSwapStatus.ACCEPTED) {
            notifyReviewersOfAcceptedRequest(saved);
        }
        return shiftSwapMapper.toResponse(saved, actor);
    }

    @Override
    @Transactional
    public ShiftSwapRequestResponse cancelRequest(
        String username,
        Long id
    ) {
        lockSchedulePeriodForRequest(id);
        ShiftSwapRequest request = findRequestForUpdate(id);
        User actor = findEmployeeForUpdate(username);
        if (!request.getRequesterUser().getId().equals(actor.getId())) {
            throw new BusinessRuleException(
                "Bạn chỉ có thể hủy yêu cầu do chính mình tạo"
            );
        }
        if (!ACTIVE_REQUEST_STATUSES.contains(request.getStatus())) {
            throw new BusinessRuleException(
                "Yêu cầu đã kết thúc nên không thể hủy"
            );
        }
        if (!request.getRequesterAssignment().getWorkShift().getStartAt()
            .isAfter(Instant.now())) {
            throw new BusinessRuleException(
                "Không thể hủy yêu cầu khi ca đã bắt đầu"
            );
        }

        request.setStatus(ShiftSwapStatus.CANCELLED);
        ShiftSwapRequest saved = shiftSwapRequestRepository.saveAndFlush(
            request
        );
        notifyTargetOfCancellation(saved);
        return shiftSwapMapper.toResponse(saved, actor);
    }

    @Override
    public List<ShiftSwapRequestResponse> getRequestsForReview(
        String username,
        ShiftSwapStatus status,
        Long locationId
    ) {
        Long effectiveLocationId = schedulingAccessService
            .resolveLocationFilter(username, locationId);
        User reviewer = findUser(username);
        return shiftSwapRequestRepository.findReviewDetailed(
                status,
                effectiveLocationId
            ).stream()
            .map(request -> shiftSwapMapper.toResponse(request, reviewer))
            .toList();
    }

    @Override
    @Transactional
    public ShiftSwapRequestResponse reviewRequest(
        String username,
        Long id,
        ShiftSwapReviewRequest review
    ) {
        validateReviewStatus(review.status());
        lockSchedulePeriodForRequest(id);
        ShiftSwapRequest request = findRequestForUpdate(id);
        Long locationId = request.getRequesterAssignment()
            .getWorkShift()
            .getSchedulePeriod()
            .getLocation()
            .getId();
        schedulingAccessService.requireLocation(username, locationId);
        User reviewer = findUser(username);

        if (request.getStatus() != ShiftSwapStatus.ACCEPTED) {
            throw new BusinessRuleException(
                "Chỉ có thể xét duyệt yêu cầu đã được người nhận đồng ý"
            );
        }
        if (request.getRequesterUser().getId().equals(reviewer.getId())
            || request.getTargetUser().getId().equals(reviewer.getId())) {
            throw new BusinessRuleException(
                "Người tham gia yêu cầu không thể tự xét duyệt"
            );
        }

        if (review.status() == ShiftSwapStatus.APPROVED) {
            approveTransfer(request, reviewer, review.reviewerNote());
        }
        request.setStatus(review.status());
        request.setApprovedBy(reviewer);
        request.setApprovedAt(Instant.now());
        request.setReviewerNote(normalizeText(review.reviewerNote()));
        ShiftSwapRequest saved = shiftSwapRequestRepository.saveAndFlush(
            request
        );
        notifyParticipantsOfReview(saved);
        return shiftSwapMapper.toResponse(saved, reviewer);
    }

    private ShiftSwapCandidateResponse toCandidate(
        ShiftAssignment requesterAssignment,
        ShiftAssignment candidate
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (attendanceRepository.existsByShiftAssignmentId(candidate.getId())) {
            reasons.add("Ca đối ứng đã có dữ liệu chấm công");
        }
        if (shiftSwapRequestRepository.countActiveByAssignmentId(
            candidate.getId(),
            ACTIVE_REQUEST_STATUSES
        ) > 0) {
            reasons.add("Ca đối ứng đang thuộc một yêu cầu khác");
        }
        try {
            validateRequestableAssignment(candidate);
        } catch (BusinessRuleException exception) {
            reasons.add(exception.getMessage());
        }

        User requester = requesterAssignment.getUser();
        User target = candidate.getUser();
        addViolations(
            reasons,
            assignmentConstraintService.evaluate(
                target,
                requesterAssignment.getWorkShift(),
                requesterAssignment.getPosition(),
                Set.of(candidate.getId())
            ),
            "Người nhận: "
        );
        addViolations(
            reasons,
            assignmentConstraintService.evaluate(
                requester,
                candidate.getWorkShift(),
                candidate.getPosition(),
                Set.of(requesterAssignment.getId())
            ),
            "Bạn: "
        );

        return new ShiftSwapCandidateResponse(
            candidate.getId(),
            target.getId(),
            target.getEmployeeCode(),
            target.getFullName(),
            shiftSwapMapper.toAssignmentResponse(candidate),
            reasons.isEmpty(),
            List.copyOf(reasons)
        );
    }

    private void approveTransfer(
        ShiftSwapRequest request,
        User reviewer,
        String reviewerNote
    ) {
        ShiftAssignment requesterAssignment = findAssignmentForUpdate(
            request.getRequesterAssignment().getId()
        );
        ShiftAssignment targetAssignment = request.getTargetAssignment() == null
            ? null
            : findAssignmentForUpdate(request.getTargetAssignment().getId());
        request.setRequesterAssignment(requesterAssignment);
        request.setTargetAssignment(targetAssignment);

        User requester = userRepository.findByIdForUpdate(
            request.getRequesterUser().getId()
        ).orElseThrow(() -> userNotFound(request.getRequesterUser().getId()));
        User target = userRepository.findByIdForUpdate(
            request.getTargetUser().getId()
        ).orElseThrow(() -> userNotFound(request.getTargetUser().getId()));
        request.setRequesterUser(requester);
        request.setTargetUser(target);

        validateRequestStateForTransfer(request);
        Map<String, Object> requesterBefore = assignment(requesterAssignment);
        Map<String, Object> targetBefore = targetAssignment == null
            ? null
            : assignment(targetAssignment);

        applySwapMetadata(
            requesterAssignment,
            target,
            reviewer,
            request.getId()
        );
        if (targetAssignment != null) {
            applySwapMetadata(
                targetAssignment,
                requester,
                reviewer,
                request.getId()
            );
            shiftAssignmentRepository.saveAllAndFlush(List.of(
                requesterAssignment,
                targetAssignment
            ));
        } else {
            shiftAssignmentRepository.saveAndFlush(requesterAssignment);
        }

        String auditReason = normalizeText(reviewerNote);
        if (auditReason == null) {
            auditReason = "Duyệt yêu cầu đổi/nhường ca #" + request.getId();
        }
        scheduleAuditService.record(
            reviewer.getUsername(),
            requesterAssignment.getWorkShift().getSchedulePeriod(),
            requesterAssignment.getWorkShift(),
            ScheduleAuditAction.UPDATED,
            ScheduleAuditTargetType.SHIFT_ASSIGNMENT,
            requesterAssignment.getId(),
            auditReason,
            requesterBefore,
            assignment(requesterAssignment)
        );
        if (targetAssignment != null) {
            scheduleAuditService.record(
                reviewer.getUsername(),
                targetAssignment.getWorkShift().getSchedulePeriod(),
                targetAssignment.getWorkShift(),
                ScheduleAuditAction.UPDATED,
                ScheduleAuditTargetType.SHIFT_ASSIGNMENT,
                targetAssignment.getId(),
                auditReason,
                targetBefore,
                assignment(targetAssignment)
            );
        }
    }

    private void applySwapMetadata(
        ShiftAssignment assignment,
        User employee,
        User reviewer,
        Long requestId
    ) {
        assignment.setUser(employee);
        assignment.setPosition(employee.getPosition());
        assignment.setAssignmentSource(AssignmentSource.SWAP);
        assignment.setAssignedBy(reviewer);
        assignment.setAssignedAt(Instant.now());
        assignment.setScore(null);
        String swapNote = "Chuyển ca theo yêu cầu #" + requestId;
        assignment.setNote(appendNote(assignment.getNote(), swapNote));
    }

    private void validateRequestStateForTransfer(ShiftSwapRequest request) {
        ShiftAssignment requesterAssignment = request.getRequesterAssignment();
        ShiftAssignment targetAssignment = request.getTargetAssignment();
        User requester = request.getRequesterUser();
        User target = request.getTargetUser();

        if (target == null) {
            throw new BusinessRuleException(
                "Yêu cầu chưa có nhân viên nhận ca"
            );
        }
        validateAssignmentOwner(requesterAssignment, requester);
        validateRequestableAssignment(requesterAssignment);
        if (targetAssignment != null) {
            validateAssignmentOwner(targetAssignment, target);
            validateSwapPair(requesterAssignment, targetAssignment);
        }
        validateTransferEligibility(
            requester,
            requesterAssignment,
            target,
            targetAssignment
        );
    }

    private void validateTransferEligibility(
        User requester,
        ShiftAssignment requesterAssignment,
        User target,
        ShiftAssignment targetAssignment
    ) {
        validateEmployee(target);
        if (target.getId().equals(requester.getId())) {
            throw new BusinessRuleException(
                "Người nhận ca phải khác người tạo yêu cầu"
            );
        }
        if (shiftAssignmentRepository.findByWorkShiftIdAndUserId(
            requesterAssignment.getWorkShift().getId(),
            target.getId()
        ).isPresent()) {
            throw new BusinessRuleException(
                "Người nhận đã có phân công trong ca bạn muốn chuyển"
            );
        }

        Set<Long> targetExclusions = targetAssignment == null
            ? Set.of()
            : Set.of(targetAssignment.getId());
        requireEligible(
            assignmentConstraintService.evaluate(
                target,
                requesterAssignment.getWorkShift(),
                requesterAssignment.getPosition(),
                targetExclusions
            ),
            "Người nhận không phù hợp: "
        );

        if (targetAssignment == null) {
            return;
        }
        if (shiftAssignmentRepository.findByWorkShiftIdAndUserId(
            targetAssignment.getWorkShift().getId(),
            requester.getId()
        ).isPresent()) {
            throw new BusinessRuleException(
                "Bạn đã có phân công trong ca đối ứng"
            );
        }
        requireEligible(
            assignmentConstraintService.evaluate(
                requester,
                targetAssignment.getWorkShift(),
                targetAssignment.getPosition(),
                Set.of(requesterAssignment.getId())
            ),
            "Bạn không phù hợp với ca đối ứng: "
        );
    }

    private void validateSwapPair(
        ShiftAssignment requesterAssignment,
        ShiftAssignment targetAssignment
    ) {
        validateRequestableAssignment(targetAssignment);
        if (requesterAssignment.getWorkShift().getId().equals(
            targetAssignment.getWorkShift().getId()
        )) {
            throw new BusinessRuleException(
                "Không thể đổi giữa hai phân công trong cùng một ca"
            );
        }
        if (!requesterAssignment.getWorkShift().getSchedulePeriod().getId()
            .equals(targetAssignment.getWorkShift().getSchedulePeriod().getId())) {
            throw new BusinessRuleException(
                "Hai ca đổi phải thuộc cùng một kỳ xếp lịch"
            );
        }
        if (!requesterAssignment.getPosition().getId().equals(
            targetAssignment.getPosition().getId()
        )) {
            throw new BusinessRuleException(
                "Chỉ có thể đổi ca giữa hai nhân viên cùng vị trí"
            );
        }
    }

    private void validateRequestableAssignment(ShiftAssignment assignment) {
        if (!ACTIVE_ASSIGNMENT_STATUSES.contains(assignment.getStatus())) {
            throw new BusinessRuleException(
                "Phân công không còn ở trạng thái có thể đổi"
            );
        }
        WorkShift workShift = assignment.getWorkShift();
        if (workShift.getSchedulePeriod().getStatus()
            != SchedulePeriodStatus.PUBLISHED) {
            throw new BusinessRuleException(
                "Chỉ có thể đổi ca thuộc lịch đã công bố và chưa khóa"
            );
        }
        if (workShift.getStatus() != WorkShiftStatus.OPEN
            && workShift.getStatus() != WorkShiftStatus.FILLED) {
            throw new BusinessRuleException(
                "Ca đã hủy hoặc hoàn thành nên không thể đổi"
            );
        }
        if (!workShift.getStartAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(
                "Ca đã bắt đầu nên không thể đổi hoặc nhường"
            );
        }
        if (attendanceRepository.existsByShiftAssignmentId(
            assignment.getId()
        )) {
            throw new BusinessRuleException(
                "Phân công đã có dữ liệu chấm công nên không thể đổi"
            );
        }
    }

    private boolean isGiveawayAvailableTo(
        ShiftSwapRequest request,
        User employee
    ) {
        try {
            validateRequestableAssignment(request.getRequesterAssignment());
            if (shiftAssignmentRepository.findByWorkShiftIdAndUserId(
                request.getRequesterAssignment().getWorkShift().getId(),
                employee.getId()
            ).isPresent()) {
                return false;
            }
            return assignmentConstraintService.evaluate(
                employee,
                request.getRequesterAssignment().getWorkShift(),
                request.getRequesterAssignment().getPosition()
            ).eligible();
        } catch (BusinessRuleException exception) {
            return false;
        }
    }

    private void ensureAssignmentHasNoActiveRequest(Long assignmentId) {
        if (shiftSwapRequestRepository.countActiveByAssignmentId(
            assignmentId,
            ACTIVE_REQUEST_STATUSES
        ) > 0) {
            throw new DuplicateResourceException(
                "Phân công đang thuộc một yêu cầu đổi hoặc nhường ca khác"
            );
        }
    }

    private void validateAssignmentOwner(
        ShiftAssignment assignment,
        User employee
    ) {
        if (!assignment.getUser().getId().equals(employee.getId())) {
            throw new BusinessRuleException(
                "Phân công không thuộc về nhân viên được yêu cầu"
            );
        }
    }

    private void validateEmployee(User user) {
        if (!user.isActive() || user.getRole() == null
            || !EMPLOYEE_ROLE.equals(user.getRole().getName())) {
            throw new BusinessRuleException(
                "Tài khoản không phải nhân viên đang hoạt động"
            );
        }
    }

    private void requireEligible(
        AssignmentConstraintResult evaluation,
        String prefix
    ) {
        if (!evaluation.eligible()) {
            throw new BusinessRuleException(
                prefix + String.join("; ", evaluation.violations())
            );
        }
    }

    private void addViolations(
        LinkedHashSet<String> reasons,
        AssignmentConstraintResult evaluation,
        String prefix
    ) {
        evaluation.violations().forEach(
            violation -> reasons.add(prefix + violation)
        );
    }

    private void validateResponseStatus(ShiftSwapStatus status) {
        if (status != ShiftSwapStatus.ACCEPTED
            && status != ShiftSwapStatus.DECLINED) {
            throw new BusinessRuleException(
                "Phản hồi chỉ được là đồng ý hoặc từ chối"
            );
        }
    }

    private void validateReviewStatus(ShiftSwapStatus status) {
        if (status != ShiftSwapStatus.APPROVED
            && status != ShiftSwapStatus.REJECTED) {
            throw new BusinessRuleException(
                "Kết quả xét duyệt chỉ được là duyệt hoặc từ chối"
            );
        }
    }

    private void notifyEligibleEmployeesOfGiveaway(
        ShiftSwapRequest request
    ) {
        ShiftAssignment assignment = request.getRequesterAssignment();
        Long locationId = assignment.getWorkShift().getSchedulePeriod()
            .getLocation().getId();
        List<User> recipients = userRepository
            .findSchedulableByLocationAndPosition(
                locationId,
                assignment.getPosition().getId()
            ).stream()
            .filter(user -> !user.getId().equals(
                request.getRequesterUser().getId()
            ))
            .filter(user -> isGiveawayAvailableTo(request, user))
            .toList();
        notificationService.createNotifications(
            recipients,
            NotificationType.SHIFT_SWAP_REQUEST_CREATED,
            "Có ca đang được nhường",
            request.getRequesterUser().getFullName()
                + " đang nhường " + assignmentLabel(assignment) + ".",
            NotificationReferenceType.SHIFT_SWAP_REQUEST,
            request.getId()
        );
    }

    private void notifyTargetOfDirectSwap(ShiftSwapRequest request) {
        notificationService.createNotification(
            request.getTargetUser(),
            NotificationType.SHIFT_SWAP_REQUEST_CREATED,
            "Bạn có yêu cầu đổi ca mới",
            request.getRequesterUser().getFullName()
                + " muốn đổi ca với bạn. Hãy xem và phản hồi yêu cầu #"
                + request.getId() + ".",
            NotificationReferenceType.SHIFT_SWAP_REQUEST,
            request.getId()
        );
    }

    private void notifyRequesterOfResponse(ShiftSwapRequest request) {
        boolean accepted = request.getStatus() == ShiftSwapStatus.ACCEPTED;
        notificationService.createNotification(
            request.getRequesterUser(),
            accepted
                ? NotificationType.SHIFT_SWAP_REQUEST_ACCEPTED
                : NotificationType.SHIFT_SWAP_REQUEST_DECLINED,
            accepted
                ? "Yêu cầu đã có người đồng ý"
                : "Yêu cầu đổi ca đã bị từ chối",
            request.getTargetUser().getFullName()
                + (accepted
                    ? " đã đồng ý yêu cầu #"
                    : " đã từ chối yêu cầu #")
                + request.getId() + ".",
            NotificationReferenceType.SHIFT_SWAP_REQUEST,
            request.getId()
        );
    }

    private void notifyReviewersOfAcceptedRequest(ShiftSwapRequest request) {
        Long locationId = request.getRequesterAssignment().getWorkShift()
            .getSchedulePeriod().getLocation().getId();
        notificationService.createNotifications(
            userRepository.findActiveNotificationReviewersForLocation(
                locationId
            ),
            NotificationType.SHIFT_SWAP_REQUEST_ACCEPTED,
            "Có yêu cầu đổi/nhường ca cần duyệt",
            "Yêu cầu #" + request.getId()
                + " đã được người nhận đồng ý và đang chờ xét duyệt.",
            NotificationReferenceType.SHIFT_SWAP_REQUEST,
            request.getId()
        );
    }

    private void notifyTargetOfCancellation(ShiftSwapRequest request) {
        if (request.getTargetUser() == null) {
            return;
        }
        notificationService.createNotification(
            request.getTargetUser(),
            NotificationType.SHIFT_SWAP_REQUEST_CANCELLED,
            "Yêu cầu đổi/nhường ca đã được hủy",
            request.getRequesterUser().getFullName()
                + " đã hủy yêu cầu #" + request.getId() + ".",
            NotificationReferenceType.SHIFT_SWAP_REQUEST,
            request.getId()
        );
    }

    private void notifyParticipantsOfReview(ShiftSwapRequest request) {
        boolean approved = request.getStatus() == ShiftSwapStatus.APPROVED;
        NotificationType type = approved
            ? NotificationType.SHIFT_SWAP_REQUEST_APPROVED
            : NotificationType.SHIFT_SWAP_REQUEST_REJECTED;
        String title = approved
            ? "Yêu cầu đổi/nhường ca đã được duyệt"
            : "Yêu cầu đổi/nhường ca bị từ chối";
        String content = "Yêu cầu #" + request.getId()
            + (approved
                ? " đã được duyệt và lịch làm đã cập nhật."
                : " đã bị người quản lý từ chối.");
        notificationService.createNotifications(
            List.of(request.getRequesterUser(), request.getTargetUser()),
            type,
            title,
            content,
            NotificationReferenceType.SHIFT_SWAP_REQUEST,
            request.getId()
        );
    }

    private String assignmentLabel(ShiftAssignment assignment) {
        WorkShift shift = assignment.getWorkShift();
        return shift.getShiftTemplate() == null
            ? "ca làm #" + shift.getId()
            : "ca " + shift.getShiftTemplate().getName();
    }

    private String appendNote(String current, String addition) {
        String normalizedCurrent = normalizeText(current);
        return normalizedCurrent == null
            ? addition
            : normalizedCurrent + " | " + addition;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));
    }

    private User findEmployee(String username) {
        User employee = findUser(username);
        validateEmployee(employee);
        return employee;
    }

    private User findEmployeeForUpdate(String username) {
        User employee = userRepository.findByUsernameForUpdate(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));
        validateEmployee(employee);
        return employee;
    }

    private ShiftAssignment findAssignmentForUpdate(Long id) {
        return shiftAssignmentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> assignmentNotFound(id));
    }

    private ShiftSwapRequest findRequestForUpdate(Long id) {
        return shiftSwapRequestRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy yêu cầu đổi/nhường ca có id " + id
            ));
    }

    private void lockSchedulePeriodForAssignment(Long assignmentId) {
        Long workShiftId = shiftAssignmentRepository
            .findWorkShiftIdByAssignmentId(assignmentId)
            .orElseThrow(() -> assignmentNotFound(assignmentId));
        Long schedulePeriodId = shiftAssignmentRepository.findDetailedById(
            assignmentId
        ).map(assignment -> assignment.getWorkShift()
            .getSchedulePeriod().getId())
            .orElseThrow(() -> assignmentNotFound(assignmentId));
        schedulePeriodRepository.findByIdForUpdate(schedulePeriodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch của ca " + workShiftId
            ));
    }

    private void lockSchedulePeriodForRequest(Long requestId) {
        Long schedulePeriodId = shiftSwapRequestRepository
            .findSchedulePeriodIdById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy yêu cầu đổi/nhường ca có id " + requestId
            ));
        schedulePeriodRepository.findByIdForUpdate(schedulePeriodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch của yêu cầu"
            ));
    }

    private ResourceNotFoundException assignmentNotFound(Long id) {
        return new ResourceNotFoundException(
            "Không tìm thấy phân công có id " + id
        );
    }

    private ResourceNotFoundException userNotFound(Long id) {
        return new ResourceNotFoundException(
            "Không tìm thấy nhân viên có id " + id
        );
    }
}
