package com.kiwih.screentime.web.dto;

import java.time.LocalDate;

/**
 * The result of a check, spelled out. {@code moreLoggedThanReported} is the
 * case worth a parent's attention: nothing is deducted, but an entry is
 * probably wrong.
 */
public record CheckResponse(
        WeeklyCheckView check,
        boolean moreLoggedThanReported,
        boolean bonusSetForFollowingWeek,
        LocalDate followingWeek,
        AdjustmentView penalty) {
}
