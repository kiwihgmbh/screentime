package com.kiwih.screentime.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides which values apply to a week.
 *
 * Count the holiday days in the week. If at least
 * {@code holidayWeekThresholdDays} of the seven are holiday days, the holiday
 * set applies to the whole week. Otherwise the term set applies, and only the
 * daily ceiling of each individual holiday day comes from the holiday set.
 *
 * A weekly budget cannot be split across two value sets without becoming
 * impossible to explain to a child. Daily ceilings can: "Saturday is a holiday,
 * so two hours" is a sentence; "this week's budget is five sevenths of term
 * time and two sevenths of the holidays" is not.
 *
 * Every value, the threshold included, is the one in force on the week's
 * Monday, so a week has one set of numbers from start to end.
 */
public final class SettingsResolver {

    private final WeekCalendar calendar;

    public SettingsResolver(WeekCalendar calendar) {
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public WeekSettings resolve(LocalDate anyDayOfTheWeek, SettingsHistory history, List<Holiday> holidays) {
        LocalDate monday = calendar.weekStartOf(anyDayOfTheWeek);

        List<LocalDate> holidayDays = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (LocalDate day : calendar.daysOfWeek(monday)) {
            for (Holiday holiday : holidays) {
                if (holiday.contains(day)) {
                    holidayDays.add(day);
                    if (!names.contains(holiday.name())) {
                        names.add(holiday.name());
                    }
                    // periods never overlap, so one match is the only match
                    break;
                }
            }
        }

        int threshold = history.holidayWeekThresholdDays(monday);
        SettingsScope scope = holidayDays.size() >= threshold ? SettingsScope.HOLIDAY : SettingsScope.TERM;
        ScreentimeSettings holidayValues = history.values(SettingsScope.HOLIDAY, monday);
        ScreentimeSettings winning = scope == SettingsScope.HOLIDAY
                ? holidayValues
                : history.values(SettingsScope.TERM, monday);

        return new WeekSettings(monday, scope, winning, holidayValues, holidayDays, names, threshold);
    }
}
