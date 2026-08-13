package com.smartshift.service.impl;

import com.smartshift.dto.schedule.SchedulePeriodRequest;
import com.smartshift.dto.schedule.SchedulePeriodResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.User;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.SchedulePeriodMapper;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.SchedulePeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulePeriodServiceImpl implements SchedulePeriodService {

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final SchedulePeriodMapper schedulePeriodMapper;

    @Override
    public List<SchedulePeriodResponse> getSchedulePeriods(
        Long locationId,
        SchedulePeriodStatus status
    ) {
        return schedulePeriodRepository.search(locationId, status)
            .stream()
            .map(schedulePeriodMapper::toResponse)
            .toList();
    }

    @Override
    public SchedulePeriodResponse getSchedulePeriodById(Long id) {
        return schedulePeriodMapper.toResponse(findSchedulePeriodById(id));
    }

    @Override
    @Transactional
    public SchedulePeriodResponse createSchedulePeriod(
        SchedulePeriodRequest request,
        String currentUsername
    ) {
        validateDateRange(request);
        Location location = findActiveLocationById(request.locationId());
        validateNoOverlap(location.getId(), request, null);
        User createdBy = findUserByUsername(currentUsername);

        SchedulePeriod schedulePeriod = schedulePeriodMapper.toEntity(
            request,
            location,
            createdBy
        );
        return schedulePeriodMapper.toResponse(
            schedulePeriodRepository.save(schedulePeriod)
        );
    }

    @Override
    @Transactional
    public SchedulePeriodResponse updateSchedulePeriod(
        Long id,
        SchedulePeriodRequest request
    ) {
        validateDateRange(request);
        SchedulePeriod schedulePeriod = findSchedulePeriodById(id);
        validateEditable(schedulePeriod);
        Location location = findActiveLocationById(request.locationId());
        validateNoOverlap(location.getId(), request, id);

        schedulePeriodMapper.updateEntity(request, schedulePeriod, location);
        return schedulePeriodMapper.toResponse(
            schedulePeriodRepository.save(schedulePeriod)
        );
    }

    private void validateDateRange(SchedulePeriodRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
    }

    private void validateNoOverlap(
        Long locationId,
        SchedulePeriodRequest request,
        Long excludedId
    ) {
        boolean overlapping = schedulePeriodRepository.existsOverlappingPeriod(
            locationId,
            request.startDate(),
            request.endDate(),
            excludedId
        );
        if (overlapping) {
            throw new DuplicateResourceException(
                "Khoảng ngày này bị trùng với một kỳ xếp lịch khác của chi nhánh"
            );
        }
    }

    private void validateEditable(SchedulePeriod schedulePeriod) {
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                "Chỉ có thể cập nhật kỳ xếp lịch đang ở trạng thái nháp"
            );
        }
    }

    private SchedulePeriod findSchedulePeriodById(Long id) {
        return schedulePeriodRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + id
            ));
    }

    private Location findActiveLocationById(Long id) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy chi nhánh có id " + id
            ));
        if (!location.isActive()) {
            throw new BusinessRuleException(
                "Không thể tạo kỳ xếp lịch cho chi nhánh đang ngừng hoạt động"
            );
        }
        return location;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
    }
}
