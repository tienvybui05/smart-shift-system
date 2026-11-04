package com.smartshift.repository;
import com.smartshift.entity.AttendanceHistory;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface AttendanceHistoryRepository extends JpaRepository<AttendanceHistory, Long> {
    @EntityGraph(attributePaths={"actor"})
    List<AttendanceHistory> findByShiftAssignmentIdOrderByIdDesc(Long assignmentId);
}

