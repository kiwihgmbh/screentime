package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.rules.SettingsScope;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WeeklyCheckView(
        Long id,
        LocalDate weekStart,
        int loggedMinutes,
        int reportedMinutes,
        int differenceMinutes,
        int penaltyMinutes,
        boolean clean,
        boolean deliberate,
        /** what the week was checked against, kept with the check */
        int budgetMinutes,
        int toleranceMinutes,
        SettingsScope settingsScope,
        Instant checkedAt,
        List<ReportedDeviceView> reported) {
}
