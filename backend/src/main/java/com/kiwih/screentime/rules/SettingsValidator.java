package com.kiwih.screentime.rules;

import java.util.ArrayList;
import java.util.List;

import static com.kiwih.screentime.rules.ScreentimeSettings.*;

/**
 * Checks that a set of values holds together, one scope at a time. Every
 * broken rule is reported, not only the first, so the parent screen can mark
 * each field at once. Every message names the actual numbers: "the ceilings
 * are too low" helps nobody, "540 is not more than 540" does.
 *
 * The two ceiling rules exist for the same reason. If the daily ceilings add
 * up to no more than the weekly budget, the weekly budget never binds and only
 * the daily ceilings do any work; the arrangement stops being "eight hours a
 * week, you choose how to spend them". A bonus week raises the budget and the
 * weekend ceiling, so it has to pass the same test with those values.
 */
public final class SettingsValidator {

    public static final String HOLIDAY_WEEK_THRESHOLD_DAYS = "holidayWeekThresholdDays";

    public static final int EARLIEST_CUTOFF_HOUR = 12;
    public static final int LATEST_CUTOFF_HOUR = 23;
    public static final int MINUTES_IN_A_WEEK = 7 * 24 * 60;

    public enum Rule {
        DAILY_CEILINGS_EXCEED_WEEK,
        BONUS_CEILINGS_EXCEED_BONUS_WEEK,
        BONUS_WEEKEND_CAP_AT_LEAST_WEEKEND_CAP,
        CUTOFF_HOUR_RANGE,
        NOT_NEGATIVE,
        WEEKLY_MAXIMUM,
        HOLIDAY_WEEK_THRESHOLD_RANGE
    }

    public List<SettingsProblem> validate(SettingsScope scope, ScreentimeSettings s) {
        String prefix = scope.label() + ": ";
        List<SettingsProblem> problems = new ArrayList<>();

        int ceilings = 5 * s.weekdayCapMinutes() + 2 * s.weekendCapMinutes();
        if (ceilings <= s.weeklyMinutes()) {
            problems.add(new SettingsProblem(Rule.DAILY_CEILINGS_EXCEED_WEEK,
                    List.of(WEEKLY_MINUTES, WEEKDAY_CAP_MINUTES, WEEKEND_CAP_MINUTES),
                    prefix + "the daily ceilings add up to 5 × " + s.weekdayCapMinutes() + " + 2 × "
                            + s.weekendCapMinutes() + " = " + ceilings + " minutes, which is not more than "
                            + "the weekly budget of " + s.weeklyMinutes() + ". The weekly budget would "
                            + "never bind. Raise a ceiling or lower the weekly budget."));
        }

        int bonusWeek = s.weeklyMinutes() + s.bonusMinutes();
        int bonusCeilings = 5 * s.weekdayCapMinutes() + 2 * s.bonusWeekendCapMinutes();
        if (bonusWeek >= bonusCeilings) {
            problems.add(new SettingsProblem(Rule.BONUS_CEILINGS_EXCEED_BONUS_WEEK,
                    List.of(WEEKLY_MINUTES, BONUS_MINUTES, WEEKDAY_CAP_MINUTES, BONUS_WEEKEND_CAP_MINUTES),
                    prefix + "in a bonus week the budget is " + s.weeklyMinutes() + " + " + s.bonusMinutes()
                            + " = " + bonusWeek + " minutes, which is not below the ceilings of 5 × "
                            + s.weekdayCapMinutes() + " + 2 × " + s.bonusWeekendCapMinutes() + " = "
                            + bonusCeilings + ". The bonus week budget would never bind."));
        }

        if (s.bonusWeekendCapMinutes() < s.weekendCapMinutes()) {
            problems.add(new SettingsProblem(Rule.BONUS_WEEKEND_CAP_AT_LEAST_WEEKEND_CAP,
                    List.of(BONUS_WEEKEND_CAP_MINUTES, WEEKEND_CAP_MINUTES),
                    prefix + "the weekend ceiling in a bonus week is " + s.bonusWeekendCapMinutes()
                            + " minutes, lower than the ordinary weekend ceiling of " + s.weekendCapMinutes()
                            + ". A bonus cannot take time away."));
        }

        if (s.cutoffHour() < EARLIEST_CUTOFF_HOUR || s.cutoffHour() > LATEST_CUTOFF_HOUR) {
            problems.add(new SettingsProblem(Rule.CUTOFF_HOUR_RANGE, List.of(CUTOFF_HOUR),
                    prefix + CUTOFF_HOUR + " must be between " + EARLIEST_CUTOFF_HOUR + " and "
                            + LATEST_CUTOFF_HOUR + ", but was " + s.cutoffHour() + "."));
        }

        s.minuteValues().forEach((key, value) -> {
            if (value < 0) {
                problems.add(new SettingsProblem(Rule.NOT_NEGATIVE, List.of(key),
                        prefix + key + " cannot be negative, but was " + value + "."));
            }
        });

        if (s.weeklyMinutes() > MINUTES_IN_A_WEEK) {
            problems.add(new SettingsProblem(Rule.WEEKLY_MAXIMUM, List.of(WEEKLY_MINUTES),
                    prefix + "the weekly budget is " + s.weeklyMinutes() + " minutes, more than the "
                            + MINUTES_IN_A_WEEK + " minutes a week has."));
        }

        return problems;
    }

    /**
     * The number of holiday days that makes a whole week a holiday week. It
     * decides which scope applies, so it belongs to neither.
     */
    public List<SettingsProblem> validateHolidayWeekThreshold(int days) {
        if (days >= 1 && days <= 7) {
            return List.of();
        }
        return List.of(new SettingsProblem(Rule.HOLIDAY_WEEK_THRESHOLD_RANGE,
                List.of(HOLIDAY_WEEK_THRESHOLD_DAYS),
                HOLIDAY_WEEK_THRESHOLD_DAYS + " is a number of days in a week, 1 to 7, but was " + days + "."));
    }

    public void requireValid(SettingsScope scope, ScreentimeSettings settings) {
        List<SettingsProblem> problems = validate(scope, settings);
        if (!problems.isEmpty()) {
            throw new InvalidSettingsException(problems);
        }
    }
}
