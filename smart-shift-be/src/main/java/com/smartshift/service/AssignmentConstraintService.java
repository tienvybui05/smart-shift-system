package com.smartshift.service;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.entity.Position;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;

import java.util.Collection;
import java.util.List;
import com.smartshift.entity.ShiftAssignment;

public interface AssignmentConstraintService {

    @FunctionalInterface
    interface BatchEvaluator {
        AssignmentConstraintResult evaluate(User employee, WorkShift shift, Position position);
    }

    /** Default preserves custom implementations; production preloads a request-local snapshot. */
    default BatchEvaluator prepare(List<User> employees, List<WorkShift> shifts,
                                   List<ShiftAssignment> assignments) {
        return this::evaluate;
    }

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
