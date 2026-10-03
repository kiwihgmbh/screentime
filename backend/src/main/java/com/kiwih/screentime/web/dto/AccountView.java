package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.rules.Balance;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the dashboard needs, in one call.
 *
 * The balance is nested whole rather than flattened, so the budget, the
 * adjustment and what has been used travel with what is left. The child can
 * then be shown the arithmetic and not just the answer.
 */
public record AccountView(
        String displayName,
        LocalDate today,
        LocalDate weekStart,
        Balance balance,
        boolean holidayWeek,
        boolean bonusActive,
        int cutoffHour,
        /** the values in force this week and why, so the child sees "holiday rules" and not just a bigger number */
        EffectiveSettingsView rules,
        /** the cut off has passed: screens are off for the evening */
        boolean screensOff,
        OpenSessionView openSession,
        /** what the child ticks before screen time today; empty when nothing is due */
        List<ChecklistDueView> checklist,
        List<DayView> week,
        List<SessionView> todayEntries,
        List<AdjustmentView> weekAdjustments,
        List<DeviceView> devices) {
}
