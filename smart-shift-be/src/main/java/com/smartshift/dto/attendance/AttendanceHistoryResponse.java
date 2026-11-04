package com.smartshift.dto.attendance;
import java.time.Instant;
public record AttendanceHistoryResponse(Long id, String actorName, String action, String reason,
    String beforeData, String afterData, Instant createdAt) {}

