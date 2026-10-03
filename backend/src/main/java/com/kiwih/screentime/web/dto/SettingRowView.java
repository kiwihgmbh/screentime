package com.kiwih.screentime.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/** One stored value. {@code createdBy} is absent for the seeded defaults. */
public record SettingRowView(
        Long id,
        String key,
        String value,
        LocalDate validFrom,
        String createdBy,
        Instant createdAt) {
}
