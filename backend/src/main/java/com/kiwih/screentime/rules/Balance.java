package com.kiwih.screentime.rules;

/**
 * Everything the dashboard needs to show a number and explain it. Each total is
 * derived from the stored sessions and adjustments on every read; none of this
 * is kept in a column, because a derived total that drifts out of sync is a bug
 * nobody finds.
 *
 * The budget and the adjustment are reported separately from what is left, so
 * the child can always see why a number is what it is.
 */
public record Balance(
        int weeklyBudgetMinutes,
        int adjustmentMinutes,
        int weekUsedMinutes,
        int remainingWeekMinutes,
        int dailyCapMinutes,
        int dayUsedMinutes,
        int remainingTodayMinutes,
        int quickBudgetMinutes,
        int quickUsedMinutes,
        int remainingQuickMinutes,
        int availableNowMinutes) {
}
