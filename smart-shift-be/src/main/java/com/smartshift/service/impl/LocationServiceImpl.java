package com.smartshift.service.impl;

import com.smartshift.dto.location.LocationRequest;
import com.smartshift.dto.location.LocationResponse;
import com.smartshift.entity.Location;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.LocationMapper;
import com.smartshift.repository.LocationRepository;
import com.smartshift.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;

    @Override
    public List<LocationResponse> getAllLocations() {
        return locationRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
            .stream()
            .map(locationMapper::toResponse)
            .toList();
    }

    @Override
    public LocationResponse getLocationById(Long id) {
        return locationMapper.toResponse(findLocationById(id));
    }

    @Override
    @Transactional
    public LocationResponse createLocation(LocationRequest request) {
        validateAttendanceCoordinates(request);
        String normalizedCode = request.code().trim();

        if (locationRepository.existsByCode(normalizedCode)) {
            throw new DuplicateResourceException(
                "Mã chi nhánh '" + normalizedCode + "' đã tồn tại"
            );
        }

        Location location = locationMapper.toEntity(request);
        Location savedLocation = locationRepository.save(location);
        return locationMapper.toResponse(savedLocation);
    }

    @Override
    @Transactional
    public LocationResponse updateLocation(Long id, LocationRequest request) {
        validateAttendanceCoordinates(request);
        Location location = findLocationById(id);
        String normalizedCode = request.code().trim();

        boolean codeChanged = !location.getCode().equals(normalizedCode);
        if (codeChanged && locationRepository.existsByCode(normalizedCode)) {
            throw new DuplicateResourceException(
                "Mã chi nhánh '" + normalizedCode + "' đã tồn tại"
            );
        }

        locationMapper.updateEntity(request, location);
        Location updatedLocation = locationRepository.save(location);
        return locationMapper.toResponse(updatedLocation);
    }

    private Location findLocationById(Long id) {
        return locationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy chi nhánh có id " + id
            ));
    }

    private void validateAttendanceCoordinates(LocationRequest request) {
        boolean hasLatitude = request.latitude() != null;
        boolean hasLongitude = request.longitude() != null;
        if (hasLatitude != hasLongitude) {
            throw new BusinessRuleException(
                "Phải khai báo đồng thời vĩ độ và kinh độ chi nhánh"
            );
        }
    }
}
