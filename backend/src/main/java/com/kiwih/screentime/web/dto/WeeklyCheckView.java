package com.kiwih.screentime.web.dto;

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
        Instant checkedAt,
        List<ReportedDeviceView> reported) {
}
