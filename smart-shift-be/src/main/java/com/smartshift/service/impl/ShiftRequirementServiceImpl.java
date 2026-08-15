package com.smartshift.service.impl;

import com.smartshift.dto.requirement.SaveShiftRequirementsRequest;
import com.smartshift.dto.requirement.ShiftRequirementItemRequest;
import com.smartshift.dto.requirement.ShiftRequirementResponse;
import com.smartshift.dto.requirement.ShiftRequirementSummaryResponse;
import com.smartshift.entity.Position;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.ShiftRequirementMapper;
import com.smartshift.repository.PositionRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.ShiftRequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftRequirementServiceImpl implements ShiftRequirementService {

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

    private final ShiftRequirementRepository shiftRequirementRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WorkShiftRepository workShiftRepository;
    private final PositionRepository positionRepository;
    private final ShiftRequirementMapper shiftRequirementMapper;

    @Override
    public ShiftRequirementSummaryResponse getRequirements(Long workShiftId) {
        findWorkShiftById(workShiftId);
        List<ShiftRequirementResponse> requirements = shiftRequirementRepository
            .findAllByWorkShiftId(workShiftId)
            .stream()
            .map(shiftRequirementMapper::toResponse)
            .toList();
        return buildSummary(workShiftId, 1, requirements);
    }

    @Override
    @Transactional
    public ShiftRequirementSummaryResponse saveRequirements(
        Long workShiftId,
        SaveShiftRequirementsRequest request
    ) {
        WorkShift sourceWorkShift = findWorkShiftById(workShiftId);
        validateEditable(sourceWorkShift);
        validateRequest(request.requirements());

        Map<Long, Position> positions = loadPositions(request.requirements());
        List<WorkShift> targetWorkShifts = findTargetWorkShifts(
            sourceWorkShift,
            request.applyToSameTemplate()
        );
        for (WorkShift targetWorkShift : targetWorkShifts) {
            replaceRequirements(
                targetWorkShift,
                request.requirements(),
                positions
            );
        }

        List<ShiftRequirementResponse> savedRequirements =
            shiftRequirementRepository.findAllByWorkShiftId(workShiftId)
                .stream()
                .map(shiftRequirementMapper::toResponse)
                .toList();
        return buildSummary(
            workShiftId,
            targetWorkShifts.size(),
            savedRequirements
        );
    }

    private void replaceRequirements(
        WorkShift workShift,
        List<ShiftRequirementItemRequest> requests,
        Map<Long, Position> positions
    ) {
        List<ShiftRequirement> existingRequirements =
            shiftRequirementRepository.findAllByWorkShiftId(workShift.getId());
        Map<Long, ShiftRequirement> existingByPosition = new HashMap<>();
        for (ShiftRequirement requirement : existingRequirements) {
            existingByPosition.put(
                requirement.getPosition().getId(),
                requirement
            );
        }

        List<ShiftRequirement> requirementsToSave = new ArrayList<>();
        Set<Long> requestedPositionIds = new HashSet<>();
        for (ShiftRequirementItemRequest request : requests) {
            requestedPositionIds.add(request.positionId());
            ShiftRequirement requirement = existingByPosition.get(
                request.positionId()
            );
            if (requirement == null) {
                requirement = shiftRequirementMapper.toEntity(
                    request,
                    workShift,
                    positions.get(request.positionId())
                );
            } else {
                shiftRequirementMapper.updateEntity(
                    request,
                    requirement,
                    positions.get(request.positionId())
                );
            }
            requirementsToSave.add(requirement);
        }

        List<ShiftRequirement> requirementsToDelete = existingRequirements
            .stream()
            .filter(requirement -> !requestedPositionIds.contains(
                requirement.getPosition().getId()
            ))
            .toList();
        validateAgainstCurrentAssignments(
            workShift,
            requests,
            requirementsToDelete
        );
        shiftRequirementRepository.deleteAll(requirementsToDelete);
        shiftRequirementRepository.saveAll(requirementsToSave);
        shiftRequirementRepository.flush();
        refreshWorkShiftStatus(workShift, requests);
    }

    private void validateAgainstCurrentAssignments(
        WorkShift workShift,
        List<ShiftRequirementItemRequest> requests,
        List<ShiftRequirement> requirementsToDelete
    ) {
        for (ShiftRequirement requirement : requirementsToDelete) {
            long assigned = countActiveAssignments(
                workShift.getId(),
                requirement.getPosition().getId()
            );
            if (assigned > 0) {
                throw new BusinessRuleException(
                    "Không thể xóa nhu cầu vị trí '"
                        + requirement.getPosition().getName()
                        + "' vì đã có " + assigned + " nhân viên được phân công"
                );
            }
        }

        for (ShiftRequirementItemRequest request : requests) {
            long assigned = countActiveAssignments(
                workShift.getId(),
                request.positionId()
            );
            if (assigned > request.maxEmployees()) {
                Position position = positionRepository
                    .findById(request.positionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy vị trí có id " + request.positionId()
                    ));
                throw new BusinessRuleException(
                    "Số tối đa của vị trí '" + position.getName()
                        + "' không được nhỏ hơn " + assigned
                        + " nhân viên đã phân công"
                );
            }
        }
    }

    private void refreshWorkShiftStatus(
        WorkShift workShift,
        List<ShiftRequirementItemRequest> requests
    ) {
        int totalMinimum = requests.stream()
            .mapToInt(ShiftRequirementItemRequest::minEmployees)
            .sum();
        boolean minimumStaffed = totalMinimum > 0
            && requests.stream().allMatch(request ->
                countActiveAssignments(
                    workShift.getId(),
                    request.positionId()
                ) >= request.minEmployees()
            );
        workShift.setStatus(
            minimumStaffed ? WorkShiftStatus.FILLED : WorkShiftStatus.OPEN
        );
        workShiftRepository.save(workShift);
    }

    private long countActiveAssignments(
        Long workShiftId,
        Long positionId
    ) {
        return shiftAssignmentRepository
            .countByWorkShiftIdAndPositionIdAndStatusIn(
                workShiftId,
                positionId,
                ACTIVE_ASSIGNMENT_STATUSES
            );
    }

    private List<WorkShift> findTargetWorkShifts(
        WorkShift sourceWorkShift,
        boolean applyToSameTemplate
    ) {
        if (!applyToSameTemplate) {
            return List.of(sourceWorkShift);
        }
        if (sourceWorkShift.getShiftTemplate() == null) {
            throw new BusinessRuleException(
                "Ca tùy chỉnh không thể áp dụng nhu cầu theo mẫu ca"
            );
        }
        return workShiftRepository
            .findAllBySchedulePeriodIdAndShiftTemplateIdAndStatus(
                sourceWorkShift.getSchedulePeriod().getId(),
                sourceWorkShift.getShiftTemplate().getId(),
                WorkShiftStatus.OPEN
            );
    }

    private void validateRequest(
        List<ShiftRequirementItemRequest> requirements
    ) {
        Set<Long> positionIds = new HashSet<>();
        for (ShiftRequirementItemRequest requirement : requirements) {
            if (!positionIds.add(requirement.positionId())) {
                throw new BusinessRuleException(
                    "Mỗi vị trí chỉ được xuất hiện một lần trong nhu cầu của ca"
                );
            }
            if (requirement.maxEmployees() < requirement.minEmployees()) {
                throw new BusinessRuleException(
                    "Số nhân viên tối đa không được nhỏ hơn số tối thiểu"
                );
            }
        }
    }

    private Map<Long, Position> loadPositions(
        List<ShiftRequirementItemRequest> requirements
    ) {
        Map<Long, Position> positions = new HashMap<>();
        for (ShiftRequirementItemRequest requirement : requirements) {
            Position position = positionRepository
                .findById(requirement.positionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Không tìm thấy vị trí có id " + requirement.positionId()
                ));
            if (!position.isActive()) {
                throw new BusinessRuleException(
                    "Không thể sử dụng vị trí '" + position.getName()
                        + "' đang ngừng hoạt động"
                );
            }
            positions.put(position.getId(), position);
        }
        return positions;
    }

    private void validateEditable(WorkShift workShift) {
        SchedulePeriod schedulePeriod = workShift.getSchedulePeriod();
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                "Chỉ có thể cấu hình nhu cầu khi kỳ xếp lịch đang ở trạng thái nháp"
            );
        }
        if (workShift.getStatus() != WorkShiftStatus.OPEN) {
            throw new BusinessRuleException(
                "Chỉ có thể cấu hình nhu cầu cho ca làm đang mở"
            );
        }
    }

    private WorkShift findWorkShiftById(Long id) {
        return workShiftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + id
            ));
    }

    private ShiftRequirementSummaryResponse buildSummary(
        Long workShiftId,
        int affectedShiftCount,
        List<ShiftRequirementResponse> requirements
    ) {
        int totalMinEmployees = requirements.stream()
            .mapToInt(ShiftRequirementResponse::minEmployees)
            .sum();
        int totalMaxEmployees = requirements.stream()
            .mapToInt(ShiftRequirementResponse::maxEmployees)
            .sum();
        return new ShiftRequirementSummaryResponse(
            workShiftId,
            totalMinEmployees,
            totalMaxEmployees,
            affectedShiftCount,
            requirements
        );
    }
}
