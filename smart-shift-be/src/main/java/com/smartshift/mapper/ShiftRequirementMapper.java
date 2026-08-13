package com.smartshift.mapper;

import com.smartshift.dto.requirement.ShiftRequirementItemRequest;
import com.smartshift.dto.requirement.ShiftRequirementResponse;
import com.smartshift.entity.Position;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.WorkShift;
import org.springframework.stereotype.Component;

@Component
public class ShiftRequirementMapper {

    public ShiftRequirement toEntity(
        ShiftRequirementItemRequest request,
        WorkShift workShift,
        Position position
    ) {
        ShiftRequirement requirement = new ShiftRequirement();
        requirement.setWorkShift(workShift);
        updateEntity(request, requirement, position);
        return requirement;
    }

    public void updateEntity(
        ShiftRequirementItemRequest request,
        ShiftRequirement requirement,
        Position position
    ) {
        requirement.setPosition(position);
        requirement.setMinEmployees(request.minEmployees());
        requirement.setMaxEmployees(request.maxEmployees());
        requirement.setPriority(request.priority());
    }

    public ShiftRequirementResponse toResponse(ShiftRequirement requirement) {
        return new ShiftRequirementResponse(
            requirement.getId(),
            requirement.getWorkShift().getId(),
            requirement.getPosition().getId(),
            requirement.getPosition().getCode(),
            requirement.getPosition().getName(),
            requirement.getMinEmployees(),
            requirement.getMaxEmployees(),
            requirement.getPriority()
        );
    }
}
