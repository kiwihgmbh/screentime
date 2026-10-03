package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.WeekFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeekFlagRepository extends JpaRepository<WeekFlag, LocalDate> {

    List<WeekFlag> findByWeekStartBetween(LocalDate from, LocalDate to);
}
