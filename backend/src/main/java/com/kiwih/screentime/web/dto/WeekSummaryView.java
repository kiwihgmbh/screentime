package com.kiwih.screentime.web.dto;

import java.time.LocalDate;

/** One row of the history. */
public record WeekSummaryView(
        LocalDate weekStart,
        int budgetSeconds,
        int adjustmentSeconds,
        int usedSeconds,
        int remainingSeconds,
        boolean holidayWeek,
        boolean bonusActive,
        WeeklyCheckView check) {
}
