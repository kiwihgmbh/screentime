package com.kiwih.screentime.rules;

import com.kiwih.screentime.domain.SessionType;

import java.time.Instant;

/**
 * Time already used, in seconds, as the rules see them. A closed session
 * contributes its stored duration; a session still running contributes the
 * seconds elapsed so far, so the child's countdown is honest while the timer
 * runs.
 *
 * The caller supplies the seconds. The rules never read a clock of their own,
 * which is what makes them testable without stubbing time.
 */
public record Booking(SessionType type, Instant startedAt, int seconds) {

    public Booking {
        if (seconds < 0) {
            throw new IllegalArgumentException("A booking cannot have a negative duration.");
        }
    }
}
