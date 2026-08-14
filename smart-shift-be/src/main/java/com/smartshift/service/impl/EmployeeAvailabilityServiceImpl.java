package com.smartshift.service.impl;

import com.smartshift.dto.availability.AvailabilityRequest;
import com.smartshift.dto.availability.AvailabilityResponse;
import com.smartshift.entity.EmployeeAvailability;
import com.smartshift.entity.User;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.AvailabilityMapper;
import com.smartshift.repository.EmployeeAvailabilityRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.EmployeeAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeAvailabilityServiceImpl
    implements EmployeeAvailabilityService {

    private static final long MAX_QUERY_DAYS = 93;

    private final EmployeeAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final AvailabilityMapper availabilityMapper;

    @Override
    public List<AvailabilityResponse> getMyAvailabilities(
        String username,
        LocalDate startDate,
        LocalDate endDate
    ) {
        User user = findUserByUsername(username);
        return getAvailabilities(user.getId(), startDate, endDate);
    }

    @Override
    public List<AvailabilityResponse> getUserAvailabilities(
        Long userId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        findUserById(userId);
        return getAvailabilities(userId, startDate, endDate);
    }

    @Override
    @Transactional
    public AvailabilityResponse createMyAvailability(
        String username,
        AvailabilityRequest request
    ) {
        User user = findUserByUsername(username);
        validateRequest(request, user);
        validateNoOverlap(user.getId(), request, null);
        EmployeeAvailability availability = availabilityMapper.toEntity(
            request,
            user
        );
        return availabilityMapper.toResponse(
            availabilityRepository.save(availability)
        );
    }

    @Override
    @Transactional
    public AvailabilityResponse updateMyAvailability(
        Long id,
        String username,
        AvailabilityRequest request
    ) {
        EmployeeAvailability availability = findOwnedAvailability(
            id,
            username
        );
        validateEditable(availability);
        validateRequest(request, availability.getUser());
        validateNoOverlap(availability.getUser().getId(), request, id);
        availabilityMapper.updateEntity(request, availability);
        return availabilityMapper.toResponse(
            availabilityRepository.save(availability)
        );
    }

    @Override
    @Transactional
    public void deleteMyAvailability(Long id, String username) {
        EmployeeAvailability availability = findOwnedAvailability(
            id,
            username
        );
        validateEditable(availability);
        availabilityRepository.delete(availability);
    }

    private List<AvailabilityResponse> getAvailabilities(
        Long userId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateQueryRange(startDate, endDate);
        return availabilityRepository
            .findAllByUserIdAndAvailableDateBetweenOrderByAvailableDateAscStartTimeAsc(
                userId,
                startDate,
                endDate
            )
            .stream()
            .map(availabilityMapper::toResponse)
            .toList();
    }

    private void validateRequest(
        AvailabilityRequest request,
        User user
    ) {
        LocalDate today = LocalDate.now(
            ZoneId.of(user.getLocation().getTimezone())
        );
        if (request.availableDate().isBefore(today)) {
            throw new BusinessRuleException(
                "Không thể đăng ký hoặc thay đổi lịch rảnh trong quá khứ"
            );
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessRuleException(
                "Giờ kết thúc phải sau giờ bắt đầu trong cùng một ngày"
            );
        }
    }

    private void validateQueryRange(
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_QUERY_DAYS) {
            throw new BusinessRuleException(
                "Chỉ có thể xem lịch rảnh trong tối đa 93 ngày"
            );
        }
    }

    private void validateNoOverlap(
        Long userId,
        AvailabilityRequest request,
        Long excludedId
    ) {
        if (availabilityRepository.existsOverlappingSlot(
            userId,
            request.availableDate(),
            request.startTime(),
            request.endTime(),
            excludedId
        )) {
            throw new DuplicateResourceException(
                "Khung giờ này bị chồng với một lịch rảnh đã đăng ký"
            );
        }
    }

    private void validateEditable(EmployeeAvailability availability) {
        LocalDate today = LocalDate.now(ZoneId.of(
            availability.getUser().getLocation().getTimezone()
        ));
        if (availability.getAvailableDate().isBefore(today)) {
            throw new BusinessRuleException(
                "Không thể thay đổi lịch rảnh trong quá khứ"
            );
        }
    }

    private EmployeeAvailability findOwnedAvailability(
        Long id,
        String username
    ) {
        EmployeeAvailability availability = availabilityRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy lịch rảnh có id " + id
            ));
        if (!availability.getUser().getUsername().equals(username)) {
            throw new ResourceNotFoundException(
                "Không tìm thấy lịch rảnh có id " + id
            );
        }
        return availability;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy nhân viên có id " + id
            ));
    }
}
