package com.smartshift.service;

import com.smartshift.dto.shift.ShiftTemplateRequest;
import com.smartshift.dto.shift.ShiftTemplateResponse;

import java.util.List;

public interface ShiftTemplateService {

    List<ShiftTemplateResponse> getShiftTemplates(
        Long locationId,
        Boolean active
    );

    ShiftTemplateResponse getShiftTemplateById(Long id);

    ShiftTemplateResponse createShiftTemplate(ShiftTemplateRequest request);

    ShiftTemplateResponse updateShiftTemplate(
        Long id,
        ShiftTemplateRequest request
    );

    ShiftTemplateResponse updateStatus(Long id, boolean active);
}
