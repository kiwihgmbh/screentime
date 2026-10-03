package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.WeeklyCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyCheckRepository extends JpaRepository<WeeklyCheck, Long> {

    Optional<WeeklyCheck> findByWeekStart(LocalDate weekStart);

    List<WeeklyCheck> findByWeekStartBetweenOrderByWeekStartAsc(LocalDate from, LocalDate to);
}
