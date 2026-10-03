package com.kiwih.screentime.domain;

/**
 * FUN counts against the week and the day, QUICK only against the separate
 * daily quick budget, FILM against nothing and may be started by a parent only.
 */
public enum SessionType {
    FUN,
    QUICK,
    FILM;

    public boolean countsAgainstWeek() {
        return this == FUN;
    }

    public boolean countsAgainstDailyCap() {
        return this == FUN;
    }

    public boolean parentOnly() {
        return this == FILM;
    }

    /** FILM is the family film night and is the one type allowed after the cut off. */
    public boolean boundByCutoff() {
        return this != FILM;
    }
}
