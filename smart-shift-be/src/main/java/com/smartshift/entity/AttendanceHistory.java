package com.smartshift.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Getter @Setter @NoArgsConstructor
@Entity @Table(name="attendance_history")
public class AttendanceHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="shift_assignment_id") private ShiftAssignment shiftAssignment;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="actor_id") private User actor;
    @Column(nullable=false, length=40) private String action;
    @Column(nullable=false, length=1000) private String reason;
    @Column(name="before_data", nullable=false, columnDefinition="TEXT") private String beforeData;
    @Column(name="after_data", nullable=false, columnDefinition="TEXT") private String afterData;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
}

