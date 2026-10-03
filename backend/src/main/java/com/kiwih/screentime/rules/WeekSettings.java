package com.kiwih.screentime.rules;

import java.time.LocalDate;
import java.util.List;

/**
 * The values that apply to one week, and why.
 *
 * {@code values} is the set that won and decides the weekly budget, the cut
 * off and everything else. {@code holidayValues} is only consulted for the
 * daily ceiling of a holiday day in a term week. The rest is there so a screen
 * can say "4 of 7 days are in the autumn holidays" instead of just showing a
 * bigger number.
 */
public record WeekSettings(
        LocalDate weekStart,
        SettingsScope scope,
        ScreentimeSettings values,
        ScreentimeSettings holidayValues,
        List<LocalDate> holidayDays,
        List<String> holidayNames,
        int holidayWeekThresholdDays) {

    public WeekSettings {
        holidayDays = List.copyOf(holidayDays);
        holidayNames = List.copyOf(holidayNames);
    }

    /** A term week with no holiday day in it, for callers that have no periods to hand. */
    public static WeekSettings withoutHolidays(LocalDate weekStart, ScreentimeSettings term,
                                               ScreentimeSettings holiday) {
        return new WeekSettings(weekStart, SettingsScope.TERM, term, holiday, List.of(), List.of(), 4);
    }

    public int holidayDayCount() {
        return holidayDays.size();
    }

    public boolean isHolidayDay(LocalDate day) {
        return holidayDays.contains(day);
    }

    /**
     * The set the daily ceiling of one day comes from. In a holiday week that
     * is the holiday set on every day; in a term week, the holiday set on a
     * holiday day and the term set otherwise.
     */
    public ScreentimeSettings ceilingValuesFor(LocalDate day) {
        return isHolidayDay(day) ? holidayValues : values;
    }
}
