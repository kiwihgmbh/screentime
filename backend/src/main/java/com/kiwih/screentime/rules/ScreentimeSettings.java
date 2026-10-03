package com.kiwih.screentime.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The configurable values, read from the {@code settings} table and validated
 * as a set. Nothing in the rules is a constant in code, so a parent can change
 * any of this without a deployment.
 *
 * A plain value: it only refuses what cannot be read as a number. Whether
 * the values hold together is {@link SettingsValidator}'s job, so that every
 * broken rule can be reported at once instead of the first one thrown.
 */
public record ScreentimeSettings(
        int weeklyMinutes,
        int weekdayCapMinutes,
        int weekendCapMinutes,
        int quickDailyMinutes,
        int cutoffHour,
        int bonusMinutes,
        int bonusWeekendCapMinutes,
        int maxPenaltyMinutes,
        int toleranceMinutes,
        int manualMaxMinutes,
        int deliberatePenaltyMinutes) {

    public static final String WEEKLY_MINUTES = "weeklyMinutes";
    public static final String WEEKDAY_CAP_MINUTES = "weekdayCapMinutes";
    public static final String WEEKEND_CAP_MINUTES = "weekendCapMinutes";
    public static final String QUICK_DAILY_MINUTES = "quickDailyMinutes";
    public static final String CUTOFF_HOUR = "cutoffHour";
    public static final String BONUS_MINUTES = "bonusMinutes";
    public static final String BONUS_WEEKEND_CAP_MINUTES = "bonusWeekendCapMinutes";
    public static final String MAX_PENALTY_MINUTES = "maxPenaltyMinutes";
    public static final String TOLERANCE_MINUTES = "toleranceMinutes";
    public static final String MANUAL_MAX_MINUTES = "manualMaxMinutes";
    public static final String DELIBERATE_PENALTY_MINUTES = "deliberatePenaltyMinutes";

    /** Every key the settings table holds, in the order the parent screen shows them. */
    public static final List<String> KEYS = List.of(
            WEEKLY_MINUTES, WEEKDAY_CAP_MINUTES, WEEKEND_CAP_MINUTES, QUICK_DAILY_MINUTES,
            CUTOFF_HOUR, BONUS_MINUTES, BONUS_WEEKEND_CAP_MINUTES, MAX_PENALTY_MINUTES,
            TOLERANCE_MINUTES, MANUAL_MAX_MINUTES, DELIBERATE_PENALTY_MINUTES);

    public static final ScreentimeSettings TERM_DEFAULTS =
            new ScreentimeSettings(480, 60, 120, 15, 20, 60, 150, 120, 10, 240, 60);

    public static final ScreentimeSettings HOLIDAY_DEFAULTS =
            new ScreentimeSettings(720, 120, 120, 15, 21, 60, 150, 120, 10, 240, 60);

    /** 5 weekdays plus 2 weekend days at their ceilings. Must exceed the weekly budget. */
    public int dailyCeilingSumMinutes() {
        return 5 * weekdayCapMinutes + 2 * weekendCapMinutes;
    }

    /** Every value that is a number of minutes, which is all of them except the cut off hour. */
    public Map<String, Integer> minuteValues() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(WEEKLY_MINUTES, weeklyMinutes);
        m.put(WEEKDAY_CAP_MINUTES, weekdayCapMinutes);
        m.put(WEEKEND_CAP_MINUTES, weekendCapMinutes);
        m.put(QUICK_DAILY_MINUTES, quickDailyMinutes);
        m.put(BONUS_MINUTES, bonusMinutes);
        m.put(BONUS_WEEKEND_CAP_MINUTES, bonusWeekendCapMinutes);
        m.put(MAX_PENALTY_MINUTES, maxPenaltyMinutes);
        m.put(TOLERANCE_MINUTES, toleranceMinutes);
        m.put(MANUAL_MAX_MINUTES, manualMaxMinutes);
        m.put(DELIBERATE_PENALTY_MINUTES, deliberatePenaltyMinutes);
        return m;
    }

    public static ScreentimeSettings fromMap(Map<String, String> values) {
        return new ScreentimeSettings(
                intValue(values, WEEKLY_MINUTES),
                intValue(values, WEEKDAY_CAP_MINUTES),
                intValue(values, WEEKEND_CAP_MINUTES),
                intValue(values, QUICK_DAILY_MINUTES),
                intValue(values, CUTOFF_HOUR),
                intValue(values, BONUS_MINUTES),
                intValue(values, BONUS_WEEKEND_CAP_MINUTES),
                intValue(values, MAX_PENALTY_MINUTES),
                intValue(values, TOLERANCE_MINUTES),
                intValue(values, MANUAL_MAX_MINUTES),
                intValue(values, DELIBERATE_PENALTY_MINUTES));
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(WEEKLY_MINUTES, String.valueOf(weeklyMinutes));
        m.put(WEEKDAY_CAP_MINUTES, String.valueOf(weekdayCapMinutes));
        m.put(WEEKEND_CAP_MINUTES, String.valueOf(weekendCapMinutes));
        m.put(QUICK_DAILY_MINUTES, String.valueOf(quickDailyMinutes));
        m.put(CUTOFF_HOUR, String.valueOf(cutoffHour));
        m.put(BONUS_MINUTES, String.valueOf(bonusMinutes));
        m.put(BONUS_WEEKEND_CAP_MINUTES, String.valueOf(bonusWeekendCapMinutes));
        m.put(MAX_PENALTY_MINUTES, String.valueOf(maxPenaltyMinutes));
        m.put(TOLERANCE_MINUTES, String.valueOf(toleranceMinutes));
        m.put(MANUAL_MAX_MINUTES, String.valueOf(manualMaxMinutes));
        m.put(DELIBERATE_PENALTY_MINUTES, String.valueOf(deliberatePenaltyMinutes));
        return m;
    }

    /** Every value as a number, in the order of {@link #KEYS}. */
    public Map<String, Integer> toIntMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        toMap().forEach((key, value) -> m.put(key, Integer.parseInt(value)));
        return m;
    }

    private static int intValue(Map<String, String> values, String key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("The setting " + key + " is missing.");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "The setting " + key + " must be a whole number of minutes but was \"" + raw + "\".");
        }
    }
}
