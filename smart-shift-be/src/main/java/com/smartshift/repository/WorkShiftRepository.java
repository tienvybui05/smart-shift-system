package com.smartshift.repository;

import com.smartshift.entity.WorkShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    List<WorkShift> findAllBySchedulePeriodIdOrderByStartAtAsc(Long schedulePeriodId);
}

