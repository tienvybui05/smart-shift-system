package com.smartshift.entity;

import com.smartshift.enums.AttendanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attendances")
public class Attendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_assignment_id", nullable = false, unique = true)
    private ShiftAssignment shiftAssignment;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "check_in_latitude", precision = 9, scale = 6)
    private BigDecimal checkInLatitude;

    @Column(name = "check_in_longitude", precision = 9, scale = 6)
    private BigDecimal checkInLongitude;

    @Column(name = "check_in_accuracy_meters", precision = 8, scale = 2)
    private BigDecimal checkInAccuracyMeters;

    @Column(name = "check_in_distance_meters", precision = 10, scale = 2)
    private BigDecimal checkInDistanceMeters;

    @Column(name = "check_out_latitude", precision = 9, scale = 6)
    private BigDecimal checkOutLatitude;

    @Column(name = "check_out_longitude", precision = 9, scale = 6)
    private BigDecimal checkOutLongitude;

    @Column(name = "check_out_accuracy_meters", precision = 8, scale = 2)
    private BigDecimal checkOutAccuracyMeters;

    @Column(name = "check_out_distance_meters", precision = 10, scale = 2)
    private BigDecimal checkOutDistanceMeters;

    @Column(name = "break_minutes", nullable = false)
    private Short breakMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes = 0;

    @Column(name = "early_leave_minutes", nullable = false)
    private Integer earlyLeaveMinutes = 0;

    @Column(name = "overtime_minutes", nullable = false)
    private Integer overtimeMinutes = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
