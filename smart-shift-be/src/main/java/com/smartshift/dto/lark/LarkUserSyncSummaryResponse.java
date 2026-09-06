package com.smartshift.dto.lark;

public record LarkUserSyncSummaryResponse(
    int totalUsers,
    int linkedUsers,
    int failedUsers
) {
}
