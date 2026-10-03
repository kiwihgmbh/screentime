package com.kiwih.screentime.web.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One reminder for the parents' list, with whether it is due today. */
public record ChecklistItemView(
        Long id,
        String text,
        List<DayOfWeek> weekdays,
        LocalDate validFrom,
        LocalDate validUntil,
        int sortOrder,
        boolean dueToday,
        String createdBy,
        Instant createdAt) {
}
