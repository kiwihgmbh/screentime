package com.kiwih.screentime.web.dto;

import java.time.LocalDate;

/** One row of the history. */
public record WeekSummaryView(
        LocalDate weekStart,
        int budgetMinutes,
        int adjustmentMinutes,
        int usedMinutes,
        int remainingMinutes,
        boolean holidayWeek,
        boolean bonusActive,
        WeeklyCheckView check) {
}
