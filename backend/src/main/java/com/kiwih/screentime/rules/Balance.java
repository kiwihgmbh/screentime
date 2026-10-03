package com.kiwih.screentime.rules;

/**
 * Everything the dashboard needs to show a number and explain it. Each total is
 * derived from the stored sessions and adjustments on every read; none of this
 * is kept in a column, because a derived total that drifts out of sync is a bug
 * nobody finds.
 *
 * The budget and the adjustment are reported separately from what is left, so
 * the child can always see why a number is what it is.
 *
 * Every field is in seconds. The budgets are set in whole minutes, but time of
 * use is counted to the second, and mixing the two units in one record is how
 * a factor of sixty goes missing.
 */
public record Balance(
        int weeklyBudgetSeconds,
        int adjustmentSeconds,
        int weekUsedSeconds,
        int remainingWeekSeconds,
        int dailyCapSeconds,
        int dayUsedSeconds,
        int remainingTodaySeconds,
        int quickBudgetSeconds,
        int quickUsedSeconds,
        int remainingQuickSeconds,
        int availableNowSeconds) {
}
