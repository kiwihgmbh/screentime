package com.kiwih.screentime.rules;

/**
 * A rule said no. The kind decides the status code at the edge of the
 * application; the rules themselves know nothing about HTTP.
 */
public class RuleViolation extends RuntimeException {

    public enum Kind {
        /** The caller's role, the date or the hour does not permit this. Maps to 403. */
        NOT_ALLOWED,
        /** There is already a session running for this user. Maps to 409. */
        SESSION_ALREADY_OPEN,
        /** The request clashes with something already stored, such as an overlapping holiday. Maps to 409. */
        CONFLICT,
        /** Screen time asked for before the day's checklist was ticked. Maps to 409, with the items. */
        CHECKLIST_PENDING,
        /** The request itself does not make sense. Maps to 400. */
        INVALID
    }

    private final Kind kind;
    private final Long openSessionId;

    public RuleViolation(Kind kind, String message) {
        this(kind, message, null);
    }

    public RuleViolation(Kind kind, String message, Long openSessionId) {
        super(message);
        this.kind = kind;
        this.openSessionId = openSessionId;
    }

    public Kind kind() {
        return kind;
    }

    /** The session already running, when that is why the call was refused. */
    public Long openSessionId() {
        return openSessionId;
    }
}
