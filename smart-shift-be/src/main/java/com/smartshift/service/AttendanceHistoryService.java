package com.smartshift.service;

import com.smartshift.dto.attendance.AttendanceHistoryResponse;
import com.smartshift.entity.*;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.mapper.AttendanceMapper;
import com.smartshift.repository.AttendanceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;

@Service @RequiredArgsConstructor
public class AttendanceHistoryService {
    private final AttendanceHistoryRepository history;
    private final AttendanceMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;

    public String snapshot(Attendance attendance) {
        try { return json.writeValueAsString(attendance == null ? null : mapper.toResponse(attendance)); }
        catch (RuntimeException e) { throw new BusinessRuleException("Không lưu được lịch sử công"); }
    }

    public void record(Attendance attendance, String before, User actor, String action, String reason) {
        AttendanceHistory row=new AttendanceHistory();
        row.setShiftAssignment(attendance.getShiftAssignment());
        row.setActor(actor); row.setAction(action); row.setReason(reason);
        row.setBeforeData(before); row.setAfterData(snapshot(attendance)); row.setCreatedAt(clock.instant());
        history.save(row);
    }

    public List<AttendanceHistoryResponse> list(Long assignmentId) {
        return history.findByShiftAssignmentIdOrderByIdDesc(assignmentId).stream()
            .map(h -> new AttendanceHistoryResponse(h.getId(),h.getActor().getFullName(),h.getAction(),
                h.getReason(),h.getBeforeData(),h.getAfterData(),h.getCreatedAt())).toList();
    }
}

