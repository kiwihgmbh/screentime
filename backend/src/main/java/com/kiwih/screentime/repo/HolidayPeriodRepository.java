package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.HolidayPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HolidayPeriodRepository extends JpaRepository<HolidayPeriod, Long> {

    /** Every period that shares at least one day with the range, both ends inclusive. */
    @Query("""
            select h from HolidayPeriod h
            where h.startDate <= :to and h.endDate >= :from
            order by h.startDate
            """)
    List<HolidayPeriod> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<HolidayPeriod> findAllByOrderByStartDateAsc();
}
