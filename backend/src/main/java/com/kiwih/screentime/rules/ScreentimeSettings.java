package com.kiwih.screentime.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The configurable values, read from the {@code settings} table and validated
 * as a set. Nothing in the rules is a constant in code, so a parent can change
 * any of this without a deployment.
 *
 * The one invariant worth stating out loud: the daily ceilings have to add up
 * to more than the weekly budget. If they do not, the weekly budget never
 * binds, only the daily ceilings do any work, and the arrangement stops being
 * "eight hours a week, you choose how to spend them".
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

    public static final ScreentimeSettings DEFAULTS =
            new ScreentimeSettings(480, 60, 120, 15, 20, 60, 150, 120, 10, 240, 60);

    public ScreentimeSettings {
        requireNotNegative(WEEKLY_MINUTES, weeklyMinutes);
        requireNotNegative(WEEKDAY_CAP_MINUTES, weekdayCapMinutes);
        requireNotNegative(WEEKEND_CAP_MINUTES, weekendCapMinutes);
        requireNotNegative(QUICK_DAILY_MINUTES, quickDailyMinutes);
        requireNotNegative(BONUS_MINUTES, bonusMinutes);
        requireNotNegative(BONUS_WEEKEND_CAP_MINUTES, bonusWeekendCapMinutes);
        requireNotNegative(MAX_PENALTY_MINUTES, maxPenaltyMinutes);
        requireNotNegative(TOLERANCE_MINUTES, toleranceMinutes);
        requireNotNegative(DELIBERATE_PENALTY_MINUTES, deliberatePenaltyMinutes);

        if (manualMaxMinutes <= 0) {
            throw new IllegalArgumentException(
                    MANUAL_MAX_MINUTES + " must be at least 1 minute but was " + manualMaxMinutes);
        }
        if (cutoffHour < 0 || cutoffHour > 23) {
            throw new IllegalArgumentException(
                    CUTOFF_HOUR + " must be an hour of the day, 0 to 23, but was " + cutoffHour);
        }

        int ceilings = 5 * weekdayCapMinutes + 2 * weekendCapMinutes;
        if (ceilings <= weeklyMinutes) {
            throw new IllegalArgumentException(
                    "The daily ceilings add up to " + ceilings + " minutes a week, which is not more "
                            + "than the weekly budget of " + weeklyMinutes + " minutes. Raise "
                            + WEEKDAY_CAP_MINUTES + " or " + WEEKEND_CAP_MINUTES + ", or lower "
                            + WEEKLY_MINUTES + ", so that the weekly budget still binds.");
        }
    }

    /** 5 weekdays plus 2 weekend days at their ceilings. Must exceed the weekly budget. */
    public int dailyCeilingSumMinutes() {
        return 5 * weekdayCapMinutes + 2 * weekendCapMinutes;
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

    private static void requireNotNegative(String key, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(key + " cannot be negative but was " + value + ".");
        }
    }
}
