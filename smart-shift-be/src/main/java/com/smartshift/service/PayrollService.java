package com.smartshift.service;

import com.smartshift.dto.payroll.PayrollBonusRequest;
import com.smartshift.dto.payroll.PayrollCalculationRequest;
import com.smartshift.dto.payroll.PayrollRecordResponse;

import java.time.LocalDate;
import java.util.List;

public interface PayrollService {

    List<PayrollRecordResponse> getPayrollRecords(
        String username,
        Long locationId,
        LocalDate startDate,
        LocalDate endDate
    );

    List<PayrollRecordResponse> getMyPayrollRecords(
        String username,
        LocalDate startDate,
        LocalDate endDate
    );

    List<PayrollRecordResponse> calculatePayroll(
        String username,
        PayrollCalculationRequest request
    );

    PayrollRecordResponse updateBonus(
        String username,
        Long payrollRecordId,
        PayrollBonusRequest request
    );

    PayrollRecordResponse confirmPayroll(
        String username,
        Long payrollRecordId
    );
}
