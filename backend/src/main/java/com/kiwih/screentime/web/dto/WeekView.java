package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.rules.Balance;

import java.time.LocalDate;
import java.util.List;

public record WeekView(
        LocalDate weekStart,
        Balance balance,
        boolean holidayWeek,
        boolean bonusActive,
        List<DayDetailView> days,
        List<AdjustmentView> adjustments,
        WeeklyCheckView check) {
}
