package com.smartshift.service.impl;

import com.smartshift.dto.payroll.PayrollBonusRequest;
import com.smartshift.dto.payroll.PayrollCalculationRequest;
import com.smartshift.dto.payroll.PayrollRecordResponse;
import com.smartshift.entity.Attendance;
import com.smartshift.entity.Location;
import com.smartshift.entity.PayrollRecord;
import com.smartshift.entity.User;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.enums.PayrollStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.PayrollMapper;
import com.smartshift.repository.AttendanceRepository;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.PayrollRecordRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.NotificationService;
import com.smartshift.service.PayrollService;
import com.smartshift.service.SchedulingAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollServiceImpl implements PayrollService {

    private static final int MAX_PERIOD_DAYS = 93;
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

    private final PayrollRecordRepository payrollRecordRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final SchedulingAccessService schedulingAccessService;
    private final NotificationService notificationService;
    private final PayrollMapper payrollMapper;

    @Override
    public List<PayrollRecordResponse> getPayrollRecords(
        String username,
        Long locationId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validatePeriod(startDate, endDate);
        Long effectiveLocationId = schedulingAccessService.resolveLocationFilter(
            username,
            locationId
        );
        User actor = findUserByUsername(username);
        return payrollRecordRepository.findDetailedByPeriod(
                effectiveLocationId,
                startDate,
                endDate
            ).stream()
            .filter(record -> isAdmin(actor) || isEmployee(record.getUser()))
            .map(payrollMapper::toResponse)
            .toList();
    }

    @Override
    public List<PayrollRecordResponse> getMyPayrollRecords(
        String username,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validatePeriod(startDate, endDate);
        return payrollRecordRepository.findMyRecordsInRange(
                username,
                startDate,
                endDate
            ).stream()
            .map(payrollMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public List<PayrollRecordResponse> calculatePayroll(
        String username,
        PayrollCalculationRequest request
    ) {
        validatePeriod(request.startDate(), request.endDate());
        Long locationId = schedulingAccessService.resolveLocationFilter(
            username,
            request.locationId()
        );
        if (locationId == null) {
            throw new BusinessRuleException("Vui lòng chọn chi nhánh cần tính lương");
        }

        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy chi nhánh có id " + locationId
            ));
        User actor = findUserByUsername(username);
        boolean fullCalendarMonth = isFullCalendarMonth(
            request.startDate(),
            request.endDate()
        );
        List<User> payrollUsers = userRepository
            .findActivePayrollUsersByLocation(locationId)
            .stream()
            .filter(user -> isEmployee(user)
                || (isAdmin(actor) && fullCalendarMonth && isManager(user)))
            .toList();

        ZoneId zoneId = ZoneId.of(location.getTimezone());
        Instant rangeStart = request.startDate().atStartOfDay(zoneId).toInstant();
        Instant rangeEnd = request.endDate().plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant();
        Map<Long, Integer> workedMinutesByUser = calculateWorkedMinutes(
            attendanceRepository.findApprovedCompletedForPayroll(
                locationId,
                rangeStart,
                rangeEnd
            )
        );

        for (User employee : payrollUsers) {
            PayrollRecord record = payrollRecordRepository
                .findByUserIdAndPeriodStartAndPeriodEnd(
                    employee.getId(),
                    request.startDate(),
                    request.endDate()
                )
                .orElseGet(PayrollRecord::new);
            if (record.getId() != null && record.getStatus() == PayrollStatus.CONFIRMED) {
                continue;
            }

            record.setUser(employee);
            record.setLocation(location);
            record.setPeriodStart(request.startDate());
            record.setPeriodEnd(request.endDate());
            record.setWorkedMinutes(isManager(employee)
                ? 0
                : workedMinutesByUser.getOrDefault(employee.getId(), 0));
            record.setBasePayAmount(employee.getBasePayAmount());
            record.setSalaryCoefficient(employee.getSalaryCoefficient());
            record.setCalculatedBy(actor);
            record.setStatus(PayrollStatus.DRAFT);
            if (record.getBonusAmount() == null) {
                record.setBonusAmount(BigDecimal.ZERO);
            }
            recalculateAmounts(record);
            payrollRecordRepository.save(record);
        }

        return payrollRecordRepository.findDetailedByPeriod(
                locationId,
                request.startDate(),
                request.endDate()
            ).stream()
            .map(payrollMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public PayrollRecordResponse updateBonus(
        String username,
        Long payrollRecordId,
        PayrollBonusRequest request
    ) {
        PayrollRecord record = findRecordForUpdate(payrollRecordId);
        schedulingAccessService.requireLocation(
            username,
            record.getLocation().getId()
        );
        requireCanManageRecord(findUserByUsername(username), record);
        requireDraft(record);

        record.setBonusAmount(money(request.bonusAmount()));
        record.setBonusNote(normalizeText(request.bonusNote()));
        recalculateAmounts(record);
        return payrollMapper.toResponse(payrollRecordRepository.save(record));
    }

    @Override
    @Transactional
    public PayrollRecordResponse confirmPayroll(
        String username,
        Long payrollRecordId
    ) {
        PayrollRecord record = findRecordForUpdate(payrollRecordId);
        schedulingAccessService.requireLocation(
            username,
            record.getLocation().getId()
        );
        User actor = findUserByUsername(username);
        requireCanManageRecord(actor, record);
        if (record.getStatus() == PayrollStatus.CONFIRMED) {
            return payrollMapper.toResponse(record);
        }

        record.setStatus(PayrollStatus.CONFIRMED);
        record.setConfirmedBy(actor);
        record.setConfirmedAt(Instant.now());
        PayrollRecord saved = payrollRecordRepository.save(record);

        notificationService.createNotification(
            saved.getUser(),
            NotificationType.PAYROLL_CONFIRMED,
            "Bảng lương dự tính đã được xác nhận",
            "Bảng lương từ " + saved.getPeriodStart() + " đến "
                + saved.getPeriodEnd() + " có tổng dự tính "
                + saved.getTotalAmount().stripTrailingZeros().toPlainString()
                + " đồng.",
            NotificationReferenceType.PAYROLL_RECORD,
            saved.getId()
        );
        return payrollMapper.toResponse(saved);
    }

    private Map<Long, Integer> calculateWorkedMinutes(
        List<Attendance> attendances
    ) {
        Map<Long, Integer> result = new HashMap<>();
        for (Attendance attendance : attendances) {
            long elapsedMinutes = Duration.between(
                attendance.getCheckInAt(),
                attendance.getCheckOutAt()
            ).toMinutes();
            int actualMinutes = Math.toIntExact(Math.max(
                0,
                elapsedMinutes - attendance.getBreakMinutes()
            ));
            Long userId = attendance.getShiftAssignment().getUser().getId();
            result.merge(userId, actualMinutes, Integer::sum);
        }
        return result;
    }

    private void recalculateAmounts(PayrollRecord record) {
        BigDecimal baseAmount;
        if (isManager(record.getUser())) {
            baseAmount = record.getBasePayAmount()
                .multiply(record.getSalaryCoefficient());
        } else {
            BigDecimal hours = BigDecimal.valueOf(record.getWorkedMinutes())
                .divide(MINUTES_PER_HOUR, 6, RoundingMode.HALF_UP);
            baseAmount = hours
                .multiply(record.getBasePayAmount())
                .multiply(record.getSalaryCoefficient());
        }
        record.setBaseAmount(money(baseAmount));
        record.setTotalAmount(money(
            record.getBaseAmount().add(record.getBonusAmount())
        ));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessRuleException("Vui lòng chọn đầy đủ khoảng ngày");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_PERIOD_DAYS) {
            throw new BusinessRuleException(
                "Chỉ được tính lương trong khoảng tối đa 93 ngày"
            );
        }
    }

    private void requireDraft(PayrollRecord record) {
        if (record.getStatus() != PayrollStatus.DRAFT) {
            throw new BusinessRuleException(
                "Bảng lương đã xác nhận nên không thể chỉnh sửa"
            );
        }
    }

    private void requireCanManageRecord(User actor, PayrollRecord record) {
        if (isManager(record.getUser()) && !isAdmin(actor)) {
            throw new BusinessRuleException(
                "Chỉ Admin được phép cập nhật và xác nhận lương của Manager"
            );
        }
    }

    private boolean isFullCalendarMonth(LocalDate startDate, LocalDate endDate) {
        return startDate.getDayOfMonth() == 1
            && endDate.equals(startDate.with(TemporalAdjusters.lastDayOfMonth()));
    }

    private boolean isAdmin(User user) {
        return ADMIN_ROLE.equals(user.getRole().getName());
    }

    private boolean isManager(User user) {
        return MANAGER_ROLE.equals(user.getRole().getName());
    }

    private boolean isEmployee(User user) {
        return EMPLOYEE_ROLE.equals(user.getRole().getName());
    }

    private PayrollRecord findRecordForUpdate(Long id) {
        return payrollRecordRepository.findDetailedByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy bảng lương có id " + id
            ));
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
