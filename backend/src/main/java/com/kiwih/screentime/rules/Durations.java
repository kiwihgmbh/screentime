package com.kiwih.screentime.rules;

/**
 * Time of use is counted in seconds. Budgets, ceilings, adjustments and what
 * the devices report are whole minutes, because that is what people enter.
 * These are the only two conversions between the two.
 */
public final class Durations {

    private Durations() {
    }

    public static int seconds(int minutes) {
        return Math.multiplyExact(minutes, 60);
    }

    /**
     * The log as the weekly check compares it with the devices. Rounded to the
     * nearest minute, half up, because the devices round their own figures and
     * rounding the log down would make it run below them every week.
     */
    public static int nearestMinute(int seconds) {
        return (int) Math.round(seconds / 60.0);
    }
}
