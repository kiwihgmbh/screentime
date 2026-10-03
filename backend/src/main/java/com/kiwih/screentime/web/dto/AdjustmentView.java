package com.kiwih.screentime.web.dto;

import java.time.Instant;
import java.time.LocalDate;

public record AdjustmentView(
        Long id,
        LocalDate weekStart,
        int minutes,
        String reason,
        String createdBy,
        Instant createdAt,
        /** written by a weekly check rather than by hand */
        boolean fromWeeklyCheck) {
}
