package com.kiwih.screentime.web.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** One day of the week view, with its entries. */
public record DayDetailView(
        LocalDate date,
        DayOfWeek dayOfWeek,
        int capSeconds,
        int usedSeconds,
        int remainingSeconds,
        int quickUsedSeconds,
        boolean today,
        boolean future,
        List<SessionView> entries) {
}
