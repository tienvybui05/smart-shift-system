package com.smartshift.service;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.entity.Position;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;

public interface AssignmentConstraintService {

    AssignmentConstraintResult evaluate(
        User employee,
        WorkShift workShift,
        Position requiredPosition
    );
}
