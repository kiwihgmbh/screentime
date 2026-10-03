package com.kiwih.screentime.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/** One holiday period. Both dates are inclusive; {@code days} counts both ends. */
public record HolidayView(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        String createdBy,
        Instant createdAt) {
}
