package com.smartshift.dto.openshift;

import com.smartshift.enums.OpenShiftClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenShiftClaimReviewRequest(
    @NotNull(message = "Trạng thái xét duyệt không được để trống")
    OpenShiftClaimStatus status,

    @Size(max = 1000, message = "Ghi chú xét duyệt không được vượt quá 1000 ký tự")
    String reviewerNote
) {
}
