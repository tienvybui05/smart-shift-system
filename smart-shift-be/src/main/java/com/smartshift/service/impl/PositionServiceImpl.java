package com.smartshift.service.impl;

import com.smartshift.dto.position.PositionRequest;
import com.smartshift.dto.position.PositionResponse;
import com.smartshift.entity.Position;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.PositionMapper;
import com.smartshift.repository.PositionRepository;
import com.smartshift.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;
    private final PositionMapper positionMapper;

    @Override
    public List<PositionResponse> getAllPositions() {
        return positionRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
            .stream()
            .map(positionMapper::toResponse)
            .toList();
    }

    @Override
    public PositionResponse getPositionById(Long id) {
        return positionMapper.toResponse(findPositionById(id));
    }

    @Override
    @Transactional
    public PositionResponse createPosition(PositionRequest request) {
        String normalizedCode = request.code().trim();
        String normalizedName = request.name().trim();
        validateUniqueCodeAndName(normalizedCode, normalizedName, null);

        Position position = positionMapper.toEntity(request);
        Position savedPosition = positionRepository.save(position);
        return positionMapper.toResponse(savedPosition);
    }

    @Override
    @Transactional
    public PositionResponse updatePosition(Long id, PositionRequest request) {
        Position position = findPositionById(id);
        String normalizedCode = request.code().trim();
        String normalizedName = request.name().trim();
        validateUniqueCodeAndName(normalizedCode, normalizedName, position);

        positionMapper.updateEntity(request, position);
        Position updatedPosition = positionRepository.save(position);
        return positionMapper.toResponse(updatedPosition);
    }

    private void validateUniqueCodeAndName(
        String code,
        String name,
        Position currentPosition
    ) {
        boolean codeChanged = currentPosition == null
            || !currentPosition.getCode().equals(code);
        if (codeChanged && positionRepository.existsByCode(code)) {
            throw new DuplicateResourceException(
                "Mã vị trí '" + code + "' đã tồn tại"
            );
        }

        boolean nameChanged = currentPosition == null
            || !currentPosition.getName().equals(name);
        if (nameChanged && positionRepository.existsByName(name)) {
            throw new DuplicateResourceException(
                "Tên vị trí '" + name + "' đã tồn tại"
            );
        }
    }

    private Position findPositionById(Long id) {
        return positionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy vị trí có id " + id
            ));
    }
}

