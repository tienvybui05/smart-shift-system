package com.smartshift.service;

import com.smartshift.dto.position.PositionRequest;
import com.smartshift.dto.position.PositionResponse;

import java.util.List;

public interface PositionService {

    List<PositionResponse> getAllPositions();

    PositionResponse getPositionById(Long id);

    PositionResponse createPosition(PositionRequest request);

    PositionResponse updatePosition(Long id, PositionRequest request);
}

