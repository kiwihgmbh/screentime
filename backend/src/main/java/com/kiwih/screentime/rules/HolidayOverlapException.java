package com.kiwih.screentime.rules;

/** A holiday period that shares a day with one already stored. Maps to 409. */
public class HolidayOverlapException extends RuleViolation {

    private final Holiday conflicting;

    public HolidayOverlapException(Holiday candidate, Holiday conflicting) {
        super(Kind.CONFLICT, candidate.start() + " to " + candidate.end() + " overlaps \""
                + conflicting.name() + "\" (" + conflicting.start() + " to " + conflicting.end()
                + "). Holiday periods cannot share a day.");
        this.conflicting = conflicting;
    }

    /** The stored period the new one clashes with. */
    public Holiday conflicting() {
        return conflicting;
    }
}
