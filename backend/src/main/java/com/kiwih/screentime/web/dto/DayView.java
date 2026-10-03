package com.kiwih.screentime.web.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** One box in the seven day strip. */
public record DayView(
        LocalDate date,
        DayOfWeek dayOfWeek,
        int capSeconds,
        int usedSeconds,
        int remainingSeconds,
        int quickUsedSeconds,
        boolean today,
        boolean future) {
}
