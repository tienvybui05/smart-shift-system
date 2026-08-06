package com.smartshift.service;

import com.smartshift.dto.location.LocationRequest;
import com.smartshift.dto.location.LocationResponse;

import java.util.List;

public interface LocationService {

    List<LocationResponse> getAllLocations();

    LocationResponse getLocationById(Long id);

    LocationResponse createLocation(LocationRequest request);

    LocationResponse updateLocation(Long id, LocationRequest request);
}
