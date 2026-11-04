package com.smartshift.entity;

import com.smartshift.enums.AttendanceCorrectionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="attendance_corrections")
public class AttendanceCorrection extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="shift_assignment_id") private ShiftAssignment shiftAssignment;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="requester_id") private User requester;
    @Column(name="proposed_check_in_at", nullable=false) private Instant proposedCheckInAt;
    @Column(name="proposed_check_out_at", nullable=false) private Instant proposedCheckOutAt;
    @Column(name="proposed_break_minutes", nullable=false) private Short proposedBreakMinutes;
    @Column(nullable=false, length=1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private AttendanceCorrectionStatus status;
    @Column(name="baseline_attendance_id") private Long baselineAttendanceId;
    @Column(name="baseline_updated_at") private Instant baselineUpdatedAt;
    @Column(name="before_data", nullable=false, columnDefinition="TEXT") private String beforeData;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by") private User reviewedBy;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="review_note", length=1000) private String reviewNote;
}

