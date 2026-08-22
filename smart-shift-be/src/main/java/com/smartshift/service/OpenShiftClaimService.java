package com.smartshift.service;

import com.smartshift.dto.openshift.AvailableOpenShiftResponse;
import com.smartshift.dto.openshift.OpenShiftClaimRequest;
import com.smartshift.dto.openshift.OpenShiftClaimResponse;
import com.smartshift.dto.openshift.OpenShiftClaimReviewRequest;
import com.smartshift.enums.OpenShiftClaimStatus;

import java.util.List;

public interface OpenShiftClaimService {

    List<AvailableOpenShiftResponse> getAvailableShifts(String username);

    List<OpenShiftClaimResponse> getMyClaims(String username);

    OpenShiftClaimResponse createClaim(
        String username,
        OpenShiftClaimRequest request
    );

    OpenShiftClaimResponse cancelClaim(Long id, String username);

    List<OpenShiftClaimResponse> getClaims(
        OpenShiftClaimStatus status,
        Long locationId,
        String reviewerUsername
    );

    OpenShiftClaimResponse reviewClaim(
        Long id,
        OpenShiftClaimReviewRequest request,
        String reviewerUsername
    );
}
