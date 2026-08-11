package com.smartshift.service.impl;

import com.smartshift.dto.shift.ShiftTemplateRequest;
import com.smartshift.dto.shift.ShiftTemplateResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.ShiftTemplate;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.ShiftTemplateMapper;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.ShiftTemplateRepository;
import com.smartshift.service.ShiftTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftTemplateServiceImpl implements ShiftTemplateService {

    private static final long MINUTES_PER_DAY = 24 * 60;

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final LocationRepository locationRepository;
    private final ShiftTemplateMapper shiftTemplateMapper;

    @Override
    public List<ShiftTemplateResponse> getShiftTemplates(
        Long locationId,
        Boolean active
    ) {
        return shiftTemplateRepository.search(locationId, active)
            .stream()
            .map(shiftTemplateMapper::toResponse)
            .toList();
    }

    @Override
    public ShiftTemplateResponse getShiftTemplateById(Long id) {
        return shiftTemplateMapper.toResponse(findShiftTemplateById(id));
    }

    @Override
    @Transactional
    public ShiftTemplateResponse createShiftTemplate(
        ShiftTemplateRequest request
    ) {
        validateTimeRange(request);
        Location location = findActiveLocationById(request.locationId());
        String normalizedName = request.name().trim();
        validateUniqueName(location.getId(), normalizedName, null);

        ShiftTemplate shiftTemplate = shiftTemplateMapper.toEntity(
            request,
            location
        );
        ShiftTemplate savedShiftTemplate = shiftTemplateRepository.save(
            shiftTemplate
        );
        return shiftTemplateMapper.toResponse(savedShiftTemplate);
    }

    @Override
    @Transactional
    public ShiftTemplateResponse updateShiftTemplate(
        Long id,
        ShiftTemplateRequest request
    ) {
        validateTimeRange(request);
        ShiftTemplate shiftTemplate = findShiftTemplateById(id);
        Location location = findActiveLocationById(request.locationId());
        String normalizedName = request.name().trim();
        validateUniqueName(location.getId(), normalizedName, id);

        shiftTemplateMapper.updateEntity(request, shiftTemplate, location);
        ShiftTemplate updatedShiftTemplate = shiftTemplateRepository.save(
            shiftTemplate
        );
        return shiftTemplateMapper.toResponse(updatedShiftTemplate);
    }

    @Override
    @Transactional
    public ShiftTemplateResponse updateStatus(Long id, boolean active) {
        ShiftTemplate shiftTemplate = findShiftTemplateById(id);
        shiftTemplate.setActive(active);
        return shiftTemplateMapper.toResponse(
            shiftTemplateRepository.save(shiftTemplate)
        );
    }

    private void validateTimeRange(ShiftTemplateRequest request) {
        if (request.startTime().equals(request.endTime())) {
            throw new BusinessRuleException(
                "Giờ bắt đầu và giờ kết thúc không được trùng nhau"
            );
        }

        long durationMinutes = Duration.between(
            request.startTime(),
            request.endTime()
        ).toMinutes();
        if (durationMinutes <= 0) {
            durationMinutes += MINUTES_PER_DAY;
        }

        if (request.breakMinutes() >= durationMinutes) {
            throw new BusinessRuleException(
                "Thời gian nghỉ phải nhỏ hơn tổng thời lượng ca"
            );
        }
    }

    private void validateUniqueName(
        Long locationId,
        String name,
        Long currentId
    ) {
        boolean duplicated = currentId == null
            ? shiftTemplateRepository.existsByLocationIdAndNameIgnoreCase(
                locationId,
                name
            )
            : shiftTemplateRepository
                .existsByLocationIdAndNameIgnoreCaseAndIdNot(
                    locationId,
                    name,
                    currentId
                );

        if (duplicated) {
            throw new DuplicateResourceException(
                "Tên mẫu ca '" + name + "' đã tồn tại tại chi nhánh này"
            );
        }
    }

    private ShiftTemplate findShiftTemplateById(Long id) {
        return shiftTemplateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy mẫu ca có id " + id
            ));
    }

    private Location findActiveLocationById(Long id) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy chi nhánh có id " + id
            ));

        if (!location.isActive()) {
            throw new BusinessRuleException(
                "Không thể sử dụng chi nhánh đang ngừng hoạt động"
            );
        }
        return location;
    }
}
