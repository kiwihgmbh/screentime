package com.kiwih.screentime.rules;

import java.time.LocalDate;

/**
 * The switch the weekly check sets on a week.
 *
 * {@code bonusActive} is set by the check of the previous week and is never
 * cumulative: it is on or off for this week and nothing more. Holidays are not
 * a switch any more; they come from the holiday periods, through
 * {@link WeekSettings}.
 */
public record WeekState(LocalDate weekStart, boolean bonusActive) {

    public static WeekState plain(LocalDate weekStart) {
        return new WeekState(weekStart, false);
    }
}
