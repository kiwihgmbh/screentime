package com.kiwih.screentime.api;

import java.time.*;

/**
 * A clock the test moves by hand. Nothing in the application reads a clock of
 * its own, so setting this is enough to put the whole app at any moment:
 * one minute before the cut off, the last Sunday in October, 23:59.
 */
public class MutableClock extends Clock {

    private Instant now;

    public MutableClock(Instant now) {
        this.now = now;
    }

    public void set(Instant now) {
        this.now = now;
    }

    /** Puts the clock at a local time in Europe/Zurich. */
    public void setLocal(LocalDate day, int hour, int minute) {
        this.now = day.atTime(hour, minute).atZone(ZoneId.of("Europe/Zurich")).toInstant();
    }

    public void advanceMinutes(long minutes) {
        this.now = now.plus(Duration.ofMinutes(minutes));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
