package com.smartshift.dto.lark;

import java.time.Instant;

public record LarkIntegrationStatusResponse(
    boolean groupEnabled,
    boolean groupConfigured,
    boolean groupReady,
    boolean signatureEnabled,
    boolean personalEnabled,
    boolean personalConfigured,
    boolean personalReady,
    int maxAttempts,
    long eligibleUsers,
    long linkedUsers,
    long userSyncErrors,
    long pendingDeliveries,
    long sentDeliveries,
    long failedDeliveries,
    Instant lastSentAt,
    String latestError,
    Instant checkedAt
) {
}
