package com.smartshift.repository;

import com.smartshift.entity.SchedulePeriod;
import com.smartshift.enums.SchedulePeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {

    List<SchedulePeriod> findAllByLocationIdOrderByStartDateDesc(Long locationId);

    List<SchedulePeriod> findAllByLocationIdAndStatusOrderByStartDateDesc(
        Long locationId,
        SchedulePeriodStatus status
    );
}

