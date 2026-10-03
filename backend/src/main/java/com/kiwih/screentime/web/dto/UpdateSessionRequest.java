package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.SessionType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** A parent's correction. Every field is optional; what is null is left alone. */
public record UpdateSessionRequest(
        @PositiveOrZero Integer minutes,
        Long deviceId,
        SessionType type,
        LocalDate date,
        @Size(max = 512) String note) {
}
