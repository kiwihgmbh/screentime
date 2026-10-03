package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.rules.SettingsScope;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The values that apply to one week and why: which set won, how many holiday
 * days the week has against the threshold, and the holiday period behind it.
 * Enough for a screen to say "holiday rules, 4 of 7 days are in the autumn
 * holidays" instead of showing an unexplained bigger number.
 *
 * {@code values} is the set that won, in whole minutes, the cut off in hours.
 * {@code weeklyBudgetMinutes} includes the bonus when it is active.
 */
public record EffectiveSettingsView(
        LocalDate weekStart,
        SettingsScope scope,
        int holidayDayCount,
        int holidayWeekThresholdDays,
        List<LocalDate> holidayDays,
        String holidayPeriodName,
        List<String> holidayPeriodNames,
        boolean bonusActive,
        int weeklyBudgetMinutes,
        Map<String, Integer> values,
        List<EffectiveDayView> days) {
}
