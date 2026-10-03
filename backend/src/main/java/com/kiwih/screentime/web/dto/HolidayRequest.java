package com.kiwih.screentime.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A holiday period as a parent enters it. The dates are an explicit,
 * authorised field, like a parent booking a past day: they say which days are
 * holidays, not when something happened.
 */
public record HolidayRequest(
        @NotBlank @Size(max = 64) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
