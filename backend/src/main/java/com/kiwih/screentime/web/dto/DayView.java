package com.kiwih.screentime.web.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** One box in the seven day strip. */
public record DayView(
        LocalDate date,
        DayOfWeek dayOfWeek,
        int capMinutes,
        int usedMinutes,
        int remainingMinutes,
        int quickUsedMinutes,
        boolean today,
        boolean future) {
}
