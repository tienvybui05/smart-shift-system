package com.smartshift.service.impl;

import com.smartshift.dto.timeoff.TimeOffRequest;
import com.smartshift.dto.timeoff.TimeOffResponse;
import com.smartshift.dto.timeoff.TimeOffReviewRequest;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.TimeOffMapper;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.TimeOffRequestRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.TimeOffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeOffServiceImpl implements TimeOffService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String MANAGER_ROLE = "ROLE_MANAGER";

    private static final List<TimeOffStatus> BLOCKING_TIME_OFF_STATUSES =
        List.of(TimeOffStatus.PENDING, TimeOffStatus.APPROVED);

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

    private final TimeOffRequestRepository timeOffRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final UserRepository userRepository;
    private final TimeOffMapper timeOffMapper;

    @Override
    public List<TimeOffResponse> getMyRequests(String username) {
        findUserByUsername(username);
        return timeOffRequestRepository
            .findAllByUserUsernameOrderByCreatedAtDesc(username)
            .stream()
            .map(timeOffMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public TimeOffResponse createMyRequest(
        String username,
        TimeOffRequest request
    ) {
        User user = findUserByUsernameForUpdate(username);
        validatePeriod(request);
        validateNoOverlap(user.getId(), request.startAt(), request.endAt());

        com.smartshift.entity.TimeOffRequest savedRequest =
            timeOffRequestRepository.save(
                timeOffMapper.toEntity(request, user)
            );
        return timeOffMapper.toResponse(savedRequest);
    }

    @Override
    @Transactional
    public TimeOffResponse cancelMyRequest(Long id, String username) {
        com.smartshift.entity.TimeOffRequest request =
            findRequestByIdForUpdate(id);
        if (!request.getUser().getUsername().equals(username)) {
            throw requestNotFound(id);
        }
        if (request.getStatus() != TimeOffStatus.PENDING) {
            throw new BusinessRuleException(
                "Chỉ có thể hủy đơn nghỉ đang chờ duyệt"
            );
        }
        if (!request.getStartAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(
                "Không thể hủy đơn nghỉ đã bắt đầu"
            );
        }

        request.setStatus(TimeOffStatus.CANCELLED);
        request.setApprovedBy(null);
        request.setApprovedAt(null);
        return timeOffMapper.toResponse(
            timeOffRequestRepository.save(request)
        );
    }

    @Override
    public List<TimeOffResponse> getRequests(
        TimeOffStatus status,
        Long locationId,
        String currentUsername
    ) {
        User reviewer = findUserByUsername(currentUsername);
        Long effectiveLocationId = resolveReviewLocation(
            reviewer,
            locationId
        );
        return timeOffRequestRepository.search(status, effectiveLocationId)
            .stream()
            .map(timeOffMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public TimeOffResponse reviewRequest(
        Long id,
        TimeOffReviewRequest reviewRequest,
        String currentUsername
    ) {
        validateReviewStatus(reviewRequest.status());
        User reviewer = findUserByUsername(currentUsername);
        validateReviewerRole(reviewer);

        com.smartshift.entity.TimeOffRequest request =
            findRequestByIdForUpdate(id);
        validateReviewerLocation(reviewer, request);
        if (reviewer.getId().equals(request.getUser().getId())) {
            throw new BusinessRuleException(
                "Không thể tự xét duyệt đơn nghỉ của chính mình"
            );
        }
        if (request.getStatus() != TimeOffStatus.PENDING) {
            throw new BusinessRuleException(
                "Chỉ có thể xét duyệt đơn nghỉ đang chờ duyệt"
            );
        }
        if (reviewRequest.status() == TimeOffStatus.APPROVED) {
            lockUserById(request.getUser().getId());
            validateNoActiveAssignmentOverlap(request);
        }

        request.setStatus(reviewRequest.status());
        request.setApprovedBy(reviewer);
        request.setApprovedAt(Instant.now());
        return timeOffMapper.toResponse(
            timeOffRequestRepository.save(request)
        );
    }

    private void validatePeriod(TimeOffRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessRuleException(
                "Thời gian kết thúc nghỉ phải sau thời gian bắt đầu"
            );
        }
        if (!request.startAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(
                "Không thể tạo đơn nghỉ bắt đầu trong quá khứ"
            );
        }
    }

    private void validateNoOverlap(
        Long userId,
        Instant startAt,
        Instant endAt
    ) {
        if (timeOffRequestRepository.existsOverlappingRequest(
            userId,
            BLOCKING_TIME_OFF_STATUSES,
            startAt,
            endAt
        )) {
            throw new DuplicateResourceException(
                "Khoảng thời gian này bị chồng với một đơn nghỉ đang chờ duyệt hoặc đã được duyệt"
            );
        }
    }

    private Long resolveReviewLocation(User reviewer, Long requestedLocationId) {
        validateReviewerRole(reviewer);
        if (isAdmin(reviewer)) {
            return requestedLocationId;
        }

        Long managerLocationId = reviewer.getLocation().getId();
        if (requestedLocationId != null
            && !requestedLocationId.equals(managerLocationId)) {
            throw new BusinessRuleException(
                "Quản lý chỉ có thể xem đơn nghỉ tại chi nhánh của mình"
            );
        }
        return managerLocationId;
    }

    private void validateReviewerRole(User reviewer) {
        String roleName = reviewer.getRole().getName();
        if (!ADMIN_ROLE.equals(roleName) && !MANAGER_ROLE.equals(roleName)) {
            throw new BusinessRuleException(
                "Tài khoản không có quyền xét duyệt đơn nghỉ"
            );
        }
    }

    private void validateReviewerLocation(
        User reviewer,
        com.smartshift.entity.TimeOffRequest request
    ) {
        if (!isAdmin(reviewer)
            && !reviewer.getLocation().getId().equals(
                request.getUser().getLocation().getId()
            )) {
            throw requestNotFound(request.getId());
        }
    }

    private void validateReviewStatus(TimeOffStatus status) {
        if (status != TimeOffStatus.APPROVED
            && status != TimeOffStatus.REJECTED) {
            throw new BusinessRuleException(
                "Trạng thái xét duyệt chỉ có thể là APPROVED hoặc REJECTED"
            );
        }
    }

    private void validateNoActiveAssignmentOverlap(
        com.smartshift.entity.TimeOffRequest request
    ) {
        List<ShiftAssignment> overlappingAssignments =
            shiftAssignmentRepository.findActiveAssignmentsInRange(
                request.getUser().getId(),
                ACTIVE_ASSIGNMENT_STATUSES,
                request.getStartAt(),
                request.getEndAt()
            );
        if (!overlappingAssignments.isEmpty()) {
            throw new BusinessRuleException(
                "Không thể duyệt đơn nghỉ vì nhân viên đã được phân công ca trong khoảng thời gian này"
            );
        }
    }

    private boolean isAdmin(User user) {
        return ADMIN_ROLE.equals(user.getRole().getName());
    }

    private com.smartshift.entity.TimeOffRequest findRequestByIdForUpdate(
        Long id
    ) {
        return timeOffRequestRepository.findByIdForUpdate(id)
            .orElseThrow(() -> requestNotFound(id));
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
    }

    private User findUserByUsernameForUpdate(String username) {
        return userRepository.findByUsernameForUpdate(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
    }

    private void lockUserById(Long id) {
        userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy nhân viên có id " + id
            ));
    }

    private ResourceNotFoundException requestNotFound(Long id) {
        return new ResourceNotFoundException(
            "Không tìm thấy đơn nghỉ có id " + id
        );
    }
}
