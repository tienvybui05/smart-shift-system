package com.smartshift.service;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.entity.Position;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;

import java.util.Collection;

public interface AssignmentConstraintService {

    AssignmentConstraintResult evaluate(
        User employee,
        WorkShift workShift,
        Position requiredPosition
    );

    AssignmentConstraintResult evaluate(
        User employee,
        WorkShift workShift,
        Position requiredPosition,
        Collection<Long> excludedAssignmentIds
    );
}
