package com.kiwih.screentime.rules;

/**
 * The result of one weekly check, with every number the parent screen shows and
 * the child can question.
 */
public record CheckOutcome(
        int loggedMinutes,
        int reportedMinutes,
        /* reported minus logged. Positive means time is missing from the log. */
        int difference,
        int penaltyMinutes,
        boolean clean,
        /* more booked than the devices saw: no deduction, but worth a look */
        boolean moreLoggedThanReported) {

    /** What to write against the following week. Zero when there is nothing to deduct. */
    public int adjustmentMinutes() {
        return -penaltyMinutes;
    }

    /** A clean week, and only a clean week, earns the bonus for the week after. */
    public boolean bonusForFollowingWeek() {
        return clean;
    }
}
