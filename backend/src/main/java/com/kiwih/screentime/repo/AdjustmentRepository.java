package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.Adjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AdjustmentRepository extends JpaRepository<Adjustment, Long> {

    List<Adjustment> findByWeekStartOrderByCreatedAtAsc(LocalDate weekStart);

    List<Adjustment> findByWeekStartBetweenOrderByWeekStartAscCreatedAtAsc(LocalDate from, LocalDate to);

    List<Adjustment> findByCheckId(Long checkId);
}
