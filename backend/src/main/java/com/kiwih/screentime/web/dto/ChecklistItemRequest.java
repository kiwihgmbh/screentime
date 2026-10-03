package com.kiwih.screentime.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * A reminder as a parent enters it. No weekdays means every day; the dates are
 * both inclusive and either may be left out. The length and the order of the
 * dates are checked by the rules, so the message names the numbers.
 */
public record ChecklistItemRequest(
        @NotNull String text,
        Set<DayOfWeek> weekdays,
        LocalDate validFrom,
        LocalDate validUntil) {
}
