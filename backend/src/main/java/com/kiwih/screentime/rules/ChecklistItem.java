package com.kiwih.screentime.rules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * A reminder a parent sets: "homework first", "laundry". Due always, or only
 * on some weekdays, or only from one date to another, both ends inclusive, or
 * both. A single day is a range of one day. No weekdays means every day.
 *
 * {@code active} is false once a parent removes it. The row stays, so what the
 * child ticked in the past still points at something.
 */
public record ChecklistItem(Long id, String text, Set<DayOfWeek> weekdays,
                            LocalDate validFrom, LocalDate validUntil, boolean active) {

    public static final int MAX_TEXT = 200;

    public ChecklistItem {
        if (text == null || text.isBlank()) {
            throw new RuleViolation(RuleViolation.Kind.INVALID, "A checklist item needs a text.");
        }
        text = text.trim();
        if (text.length() > MAX_TEXT) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "A checklist item can be at most " + MAX_TEXT + " characters, this one has " + text.length() + ".");
        }
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "A checklist item cannot end on " + validUntil + " before it starts on " + validFrom + ".");
        }
        weekdays = weekdays == null || weekdays.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(weekdays));
    }

    /** Whether the child has to tick this on the given local day. */
    public boolean dueOn(LocalDate day) {
        return active
                && (validFrom == null || !day.isBefore(validFrom))
                && (validUntil == null || !day.isAfter(validUntil))
                && (weekdays.isEmpty() || weekdays.contains(day.getDayOfWeek()));
    }
}
