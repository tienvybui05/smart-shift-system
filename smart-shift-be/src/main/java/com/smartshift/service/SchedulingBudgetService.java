package com.smartshift.service;

import com.smartshift.dto.schedule.*;
import com.smartshift.entity.Location;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class SchedulingBudgetService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final SchedulingAccessService access;
    private final LocationRepository locations;
    private final SchedulePeriodRepository periods;
    private final ShiftAssignmentRepository assignments;

    public WeeklyBudgetSettings settings(String username, Long locationId) {
        access.requireLocation(username,locationId);
        return settings(location(locationId));
    }

    @Transactional
    public WeeklyBudgetSettings update(String username, Long locationId, WeeklyBudgetRequest request) {
        access.requireLocation(username,locationId);
        Location location=locations.findByIdForUpdate(locationId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh"));
        location.setWeeklyBudget(request.weeklyBudget()==null ? null : request.weeklyBudget().setScale(2,RoundingMode.UNNECESSARY));
        return settings(locations.save(location));
    }

    public ScheduleBudgetResponse report(String username, Long periodId) {
        access.requireSchedulePeriod(username,periodId);
        var period=periods.findById(periodId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch"));
        Location location=period.getLocation();
        ZoneId zone=ZoneId.of(location.getTimezone());
        LocalDate first=monday(period.getStartDate()), last=monday(period.getEndDate());
        Map<LocalDate,Totals> weeks=new LinkedHashMap<>();
        for(LocalDate day=first; !day.isAfter(last); day=day.plusWeeks(1)) weeks.put(day,new Totals());
        var rows=assignments.findBudgetAssignments(location.getId(),first.atStartOfDay(zone).toInstant(),
            last.plusWeeks(1).atStartOfDay(zone).toInstant());
        for(var row:rows) {
            long minutes=Duration.between(row.getStartAt(),row.getEndAt()).toMinutes()-row.getBreakMinutes();
            if(minutes<=0) throw new BusinessRuleException("Ca có thời lượng công không hợp lệ, hãy kiểm tra trước khi dự toán");
            BigDecimal cost=row.getBasePayAmount().multiply(row.getSalaryCoefficient())
                .multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);
            Totals total=weeks.get(monday(row.getStartAt().atZone(zone).toLocalDate()));
            total.count++;
            if(periodId.equals(row.getPeriodId())) total.own=total.own.add(cost);
            else total.other=total.other.add(cost);
        }
        BigDecimal budget=location.getWeeklyBudget();
        List<ScheduleBudgetResponse.Week> result=new ArrayList<>();
        BigDecimal periodCost=ZERO;
        boolean over=false;
        for(var entry:weeks.entrySet()) {
            Totals total=entry.getValue();
            BigDecimal cost=total.own.add(total.other);
            BigDecimal excess=budget==null ? ZERO : cost.subtract(budget).max(ZERO);
            BigDecimal remaining=budget==null ? null : budget.subtract(cost).max(ZERO);
            BigDecimal utilization=budget==null || budget.signum()==0 ? null
                : cost.multiply(BigDecimal.valueOf(100)).divide(budget,2,RoundingMode.HALF_UP);
            over |= excess.signum()>0;
            periodCost=periodCost.add(total.own);
            result.add(new ScheduleBudgetResponse.Week(entry.getKey(),entry.getKey().plusDays(6),
                total.own,total.other,cost,excess,remaining,utilization,total.count));
        }
        return new ScheduleBudgetResponse(periodId,location.getId(),location.getName(),location.getTimezone(),
            budget,periodCost,over,result);
    }

    private Location location(Long id) {
        return locations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh"));
    }
    private WeeklyBudgetSettings settings(Location location) {
        return new WeeklyBudgetSettings(location.getId(),location.getName(),location.getTimezone(),location.getWeeklyBudget());
    }
    private static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    private static final class Totals {
        BigDecimal own=ZERO,other=ZERO;
        int count;
    }
}
