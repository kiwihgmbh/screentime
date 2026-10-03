package com.kiwih.screentime.rules;

import com.kiwih.screentime.domain.SessionType;

import java.time.Instant;

/**
 * Minutes already used, as the rules see them. A closed session contributes its
 * stored minutes; a session still running contributes the minutes elapsed so
 * far, so the child's countdown is honest while the timer runs.
 *
 * The caller supplies the minutes. The rules never read a clock of their own,
 * which is what makes them testable without stubbing time.
 */
public record Booking(SessionType type, Instant startedAt, int minutes) {

    public Booking {
        if (minutes < 0) {
            throw new IllegalArgumentException("A booking cannot have negative minutes.");
        }
    }
}
