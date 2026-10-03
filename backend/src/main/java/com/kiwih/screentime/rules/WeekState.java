package com.kiwih.screentime.rules;

import java.time.LocalDate;

/**
 * The two switches that change what a week is worth.
 *
 * {@code holiday} makes the weekend ceiling apply on every day of the week.
 * {@code bonusActive} is set by the check of the previous week and is never
 * cumulative: it is on or off for this week and nothing more.
 */
public record WeekState(LocalDate weekStart, boolean holiday, boolean bonusActive) {

    public static WeekState plain(LocalDate weekStart) {
        return new WeekState(weekStart, false, false);
    }
}
