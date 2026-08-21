package com.smartshift.service.impl;

import com.smartshift.dto.workshift.GenerateWorkShiftsRequest;
import com.smartshift.dto.workshift.GenerateWorkShiftsResponse;
import com.smartshift.dto.workshift.WorkShiftRequest;
import com.smartshift.dto.workshift.WorkShiftResponse;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftTemplate;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.WorkShiftMapper;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftTemplateRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.WorkShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkShiftServiceImpl implements WorkShiftService {

    private final WorkShiftRepository workShiftRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final WorkShiftMapper workShiftMapper;

    @Override
    public List<WorkShiftResponse> getWorkShifts(
        Long schedulePeriodId,
        WorkShiftStatus status
    ) {
        findSchedulePeriodById(schedulePeriodId);
        return workShiftRepository.search(schedulePeriodId, status)
            .stream()
            .map(workShiftMapper::toResponse)
            .toList();
    }

    @Override
    public WorkShiftResponse getWorkShiftById(Long id) {
        return workShiftMapper.toResponse(findWorkShiftById(id));
    }

    @Override
    @Transactional
    public WorkShiftResponse createWorkShift(WorkShiftRequest request) {
        SchedulePeriod schedulePeriod = findSchedulePeriodByIdForUpdate(
            request.schedulePeriodId()
        );
        validateEditablePeriod(schedulePeriod);
        ShiftTemplate shiftTemplate = findValidShiftTemplate(
            request.shiftTemplateId(),
            schedulePeriod
        );
        validateWorkShiftRequest(request, schedulePeriod);

        WorkShift workShift = workShiftMapper.toEntity(
            request,
            schedulePeriod,
            shiftTemplate
        );
        validateUniqueStart(schedulePeriod.getId(), workShift.getStartAt(), null);
        return workShiftMapper.toResponse(workShiftRepository.save(workShift));
    }

    @Override
    @Transactional
    public WorkShiftResponse updateWorkShift(
        Long id,
        WorkShiftRequest request
    ) {
        SchedulePeriod lockedPeriod = lockSchedulePeriodForShift(id);
        WorkShift workShift = findWorkShiftByIdForUpdate(id);
        if (!workShift.getSchedulePeriod().getId().equals(request.schedulePeriodId())) {
            throw new BusinessRuleException(
                "Không thể chuyển ca làm sang một kỳ xếp lịch khác"
            );
        }
        validateEditablePeriod(lockedPeriod);
        if (workShift.getStatus() != WorkShiftStatus.OPEN) {
            throw new BusinessRuleException(
                "Chỉ có thể cập nhật ca làm đang mở"
            );
        }

        ShiftTemplate shiftTemplate = findValidShiftTemplate(
            request.shiftTemplateId(),
            workShift.getSchedulePeriod()
        );
        validateWorkShiftRequest(request, workShift.getSchedulePeriod());
        workShiftMapper.updateEntity(
            request,
            workShift,
            workShift.getSchedulePeriod(),
            shiftTemplate
        );
        validateUniqueStart(
            workShift.getSchedulePeriod().getId(),
            workShift.getStartAt(),
            id
        );
        return workShiftMapper.toResponse(workShiftRepository.save(workShift));
    }

    @Override
    @Transactional
    public GenerateWorkShiftsResponse generateWorkShifts(
        GenerateWorkShiftsRequest request
    ) {
        SchedulePeriod schedulePeriod = findSchedulePeriodByIdForUpdate(
            request.schedulePeriodId()
        );
        validateEditablePeriod(schedulePeriod);
        validateGenerationRange(request, schedulePeriod);

        List<ShiftTemplate> shiftTemplates = new LinkedHashSet<>(
            request.shiftTemplateIds()
        ).stream()
            .map(id -> findValidShiftTemplate(id, schedulePeriod))
            .toList();

        List<WorkShift> workShiftsToCreate = new ArrayList<>();
        int skippedCount = 0;
        LocalDate currentDate = request.startDate();
        while (!currentDate.isAfter(request.endDate())) {
            for (ShiftTemplate shiftTemplate : shiftTemplates) {
                WorkShift workShift = workShiftMapper.fromTemplate(
                    schedulePeriod,
                    shiftTemplate,
                    currentDate
                );
                boolean duplicated = workShiftRepository
                    .existsBySchedulePeriodIdAndStartAt(
                        schedulePeriod.getId(),
                        workShift.getStartAt()
                    ) || hasSameStart(workShiftsToCreate, workShift.getStartAt());
                if (duplicated) {
                    skippedCount++;
                } else {
                    workShiftsToCreate.add(workShift);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        List<WorkShiftResponse> responses = workShiftRepository
            .saveAll(workShiftsToCreate)
            .stream()
            .map(workShiftMapper::toResponse)
            .toList();
        return new GenerateWorkShiftsResponse(
            responses.size(),
            skippedCount,
            responses
        );
    }

    @Override
    @Transactional
    public WorkShiftResponse updateStatus(Long id, WorkShiftStatus status) {
        SchedulePeriod lockedPeriod = lockSchedulePeriodForShift(id);
        WorkShift workShift = findWorkShiftByIdForUpdate(id);
        validateEditablePeriod(lockedPeriod);
        if (workShift.getStatus() != WorkShiftStatus.OPEN
            && workShift.getStatus() != WorkShiftStatus.CANCELLED) {
            throw new BusinessRuleException(
                "Chỉ có thể thay đổi trạng thái của ca đang mở hoặc đã hủy"
            );
        }
        if (status != WorkShiftStatus.OPEN && status != WorkShiftStatus.CANCELLED) {
            throw new BusinessRuleException(
                "Chỉ có thể mở lại hoặc hủy ca làm tại bước này"
            );
        }
        workShift.setStatus(status);
        return workShiftMapper.toResponse(workShiftRepository.save(workShift));
    }

    private void validateWorkShiftRequest(
        WorkShiftRequest request,
        SchedulePeriod schedulePeriod
    ) {
        validateDateInsidePeriod(request.workDate(), schedulePeriod);
        if (request.startTime().equals(request.endTime())) {
            throw new BusinessRuleException(
                "Giờ bắt đầu và giờ kết thúc không được trùng nhau"
            );
        }

        long durationMinutes = calculateDurationMinutes(
            request.startTime(),
            request.endTime()
        );
        if (request.breakMinutes() >= durationMinutes) {
            throw new BusinessRuleException(
                "Thời gian nghỉ phải nhỏ hơn tổng thời lượng ca"
            );
        }
    }

    private void validateGenerationRange(
        GenerateWorkShiftsRequest request,
        SchedulePeriod schedulePeriod
    ) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
        validateDateInsidePeriod(request.startDate(), schedulePeriod);
        validateDateInsidePeriod(request.endDate(), schedulePeriod);
    }

    private void validateDateInsidePeriod(
        LocalDate workDate,
        SchedulePeriod schedulePeriod
    ) {
        if (workDate.isBefore(schedulePeriod.getStartDate())
            || workDate.isAfter(schedulePeriod.getEndDate())) {
            throw new BusinessRuleException(
                "Ngày làm việc phải nằm trong khoảng ngày của kỳ xếp lịch"
            );
        }
    }

    private void validateUniqueStart(
        Long schedulePeriodId,
        Instant startAt,
        Long currentId
    ) {
        boolean duplicated = currentId == null
            ? workShiftRepository.existsBySchedulePeriodIdAndStartAt(
                schedulePeriodId,
                startAt
            )
            : workShiftRepository.existsBySchedulePeriodIdAndStartAtAndIdNot(
                schedulePeriodId,
                startAt,
                currentId
            );
        if (duplicated) {
            throw new DuplicateResourceException(
                "Kỳ xếp lịch đã có một ca bắt đầu tại thời điểm này"
            );
        }
    }

    private void validateEditablePeriod(SchedulePeriod schedulePeriod) {
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                "Chỉ có thể thay đổi ca làm khi kỳ xếp lịch đang ở trạng thái nháp"
            );
        }
    }

    private ShiftTemplate findValidShiftTemplate(
        Long id,
        SchedulePeriod schedulePeriod
    ) {
        ShiftTemplate shiftTemplate = shiftTemplateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy mẫu ca có id " + id
            ));
        if (!shiftTemplate.isActive()) {
            throw new BusinessRuleException(
                "Không thể sử dụng mẫu ca đang ngừng hoạt động"
            );
        }
        if (!shiftTemplate.getLocation().getId().equals(
            schedulePeriod.getLocation().getId()
        )) {
            throw new BusinessRuleException(
                "Mẫu ca không thuộc chi nhánh của kỳ xếp lịch"
            );
        }
        return shiftTemplate;
    }

    private WorkShift findWorkShiftById(Long id) {
        return workShiftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + id
            ));
    }

    private WorkShift findWorkShiftByIdForUpdate(Long id) {
        return workShiftRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + id
            ));
    }

    private SchedulePeriod findSchedulePeriodById(Long id) {
        return schedulePeriodRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + id
            ));
    }

    private SchedulePeriod findSchedulePeriodByIdForUpdate(Long id) {
        return schedulePeriodRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + id
            ));
    }

    private SchedulePeriod lockSchedulePeriodForShift(Long workShiftId) {
        Long schedulePeriodId = workShiftRepository
            .findSchedulePeriodIdById(workShiftId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm có id " + workShiftId
            ));
        return findSchedulePeriodByIdForUpdate(schedulePeriodId);
    }

    private long calculateDurationMinutes(
        LocalTime startTime,
        LocalTime endTime
    ) {
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        return durationMinutes > 0 ? durationMinutes : durationMinutes + 24 * 60;
    }

    private boolean hasSameStart(
        List<WorkShift> workShifts,
        Instant startAt
    ) {
        return workShifts.stream().anyMatch(
            workShift -> workShift.getStartAt().equals(startAt)
        );
    }
}
