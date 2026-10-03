package com.kiwih.screentime.rules;

import java.time.LocalDate;
import java.util.List;

/**
 * A holiday period as a parent enters it: a name and two dates, both inclusive.
 * A day is a holiday day if any period contains it. Periods never overlap, so
 * no day can belong to two of them and the count of holiday days in a week is
 * never ambiguous.
 *
 * {@code id} is null for a period that has not been stored yet.
 */
public record Holiday(Long id, String name, LocalDate start, LocalDate end) {

    public Holiday {
        if (name == null || name.isBlank()) {
            throw new RuleViolation(RuleViolation.Kind.INVALID, "A holiday period needs a name.");
        }
        if (start == null || end == null) {
            throw new RuleViolation(RuleViolation.Kind.INVALID, "A holiday period needs a start and an end date.");
        }
        if (end.isBefore(start)) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "A holiday period cannot end on " + end + " before it starts on " + start + ".");
        }
        name = name.trim();
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(start) && !day.isAfter(end);
    }

    /** Whether the two share at least one day. Both ends count. */
    public boolean overlaps(Holiday other) {
        return !other.end.isBefore(start) && !other.start.isAfter(end);
    }

    /**
     * Refuses a period that shares a day with one already stored, and names the
     * one it clashes with. A period being edited is not compared with itself.
     */
    public static void requireNoOverlap(Holiday candidate, List<Holiday> existing) {
        for (Holiday other : existing) {
            boolean itself = candidate.id() != null && candidate.id().equals(other.id());
            if (!itself && candidate.overlaps(other)) {
                throw new HolidayOverlapException(candidate, other);
            }
        }
    }
}
