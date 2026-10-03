package com.kiwih.screentime.web.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** One day of the week view, with its entries. */
public record DayDetailView(
        LocalDate date,
        DayOfWeek dayOfWeek,
        int capMinutes,
        int usedMinutes,
        int remainingMinutes,
        int quickUsedMinutes,
        boolean today,
        boolean future,
        List<SessionView> entries) {
}
