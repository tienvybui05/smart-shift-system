package com.smartshift.mapper;

import com.smartshift.dto.payroll.PayrollRecordResponse;
import com.smartshift.entity.PayrollRecord;
import com.smartshift.entity.User;
import org.springframework.stereotype.Component;

@Component
public class PayrollMapper {

    public PayrollRecordResponse toResponse(PayrollRecord record) {
        User employee = record.getUser();
        User calculatedBy = record.getCalculatedBy();
        User confirmedBy = record.getConfirmedBy();

        return new PayrollRecordResponse(
            record.getId(),
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            employee.getRole().getName(),
            employee.getPosition() == null
                ? null
                : employee.getPosition().getName(),
            record.getLocation().getId(),
            record.getLocation().getName(),
            record.getPeriodStart(),
            record.getPeriodEnd(),
            record.getWorkedMinutes(),
            record.getBasePayAmount(),
            "ROLE_MANAGER".equals(employee.getRole().getName())
                ? "MONTHLY"
                : "HOURLY",
            record.getSalaryCoefficient(),
            record.getBaseAmount(),
            record.getBonusAmount(),
            record.getBonusNote(),
            record.getTotalAmount(),
            record.getStatus(),
            calculatedBy.getId(),
            calculatedBy.getFullName(),
            confirmedBy == null ? null : confirmedBy.getId(),
            confirmedBy == null ? null : confirmedBy.getFullName(),
            record.getConfirmedAt(),
            record.getCreatedAt(),
            record.getUpdatedAt()
        );
    }
}
