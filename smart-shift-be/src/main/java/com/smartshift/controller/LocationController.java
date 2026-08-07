package com.smartshift.controller;

import com.smartshift.dto.location.LocationRequest;
import com.smartshift.dto.location.LocationResponse;
import com.smartshift.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public ResponseEntity<List<LocationResponse>> getAllLocations() {
        return ResponseEntity.ok(locationService.getAllLocations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationResponse> getLocationById(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(locationService.getLocationById(id));
    }

    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(
        @Valid @RequestBody LocationRequest request
    ) {
        LocationResponse response = locationService.createLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocationResponse> updateLocation(
        @PathVariable Long id,
        @Valid @RequestBody LocationRequest request
    ) {
        return ResponseEntity.ok(locationService.updateLocation(id, request));
    }
}

