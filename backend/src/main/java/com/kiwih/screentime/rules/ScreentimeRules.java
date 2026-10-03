package com.kiwih.screentime.rules;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.SessionType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Every house rule in one place, as a plain class with no framework in it. It
 * reads no clock, no database and no configuration of its own: the caller
 * passes the settings, the week, the bookings and the current instant. That is
 * what makes all of this testable without a Spring context, and it is also why
 * a date in a request body can never reach a decision made here.
 *
 * Built per request with the settings as they are at that moment, since
 * settings are rows a parent can change at any time.
 */
public final class ScreentimeRules {

    private final ScreentimeSettings settings;
    private final WeekCalendar calendar;

    public ScreentimeRules(ScreentimeSettings settings, WeekCalendar calendar) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public ScreentimeSettings settings() {
        return settings;
    }

    public WeekCalendar calendar() {
        return calendar;
    }

    // ---------------------------------------------------------------- budgets

    /**
     * The ceiling in force on one day. The weekday decides, unless the week is
     * flagged as a holiday week, in which case the weekend ceiling applies
     * every day. An active bonus raises the weekend ceiling only.
     */
    public int dailyCapMinutes(LocalDate day, WeekState week) {
        boolean weekendCeiling = week.holiday() || calendar.isWeekend(day);
        if (!weekendCeiling) {
            return settings.weekdayCapMinutes();
        }
        return week.bonusActive() ? settings.bonusWeekendCapMinutes() : settings.weekendCapMinutes();
    }

    /** The weekly budget, raised by the bonus when the previous week was clean. */
    public int weeklyBudgetMinutes(WeekState week) {
        return settings.weeklyMinutes() + (week.bonusActive() ? settings.bonusMinutes() : 0);
    }

    /**
     * The whole account for one day, derived from the week's bookings.
     *
     * Unused time expires: yesterday's leftover is not in here anywhere, and
     * neither is last week's. Available now is the smaller of what the week has
     * left and what the day has left, because both have to allow it.
     */
    public Balance balance(LocalDate day, WeekState week, List<Booking> weekBookings, int adjustmentMinutes) {
        int weeklyBudget = weeklyBudgetMinutes(week);
        int dailyCap = dailyCapMinutes(day, week);
        int quickBudget = settings.quickDailyMinutes();

        int weekUsed = 0;
        int dayUsed = 0;
        int quickUsed = 0;
        for (Booking booking : weekBookings) {
            boolean today = calendar.dayOf(booking.startedAt()).equals(day);
            if (booking.type().countsAgainstWeek()) {
                weekUsed += booking.minutes();
            }
            if (today && booking.type().countsAgainstDailyCap()) {
                dayUsed += booking.minutes();
            }
            if (today && booking.type() == SessionType.QUICK) {
                quickUsed += booking.minutes();
            }
        }

        int remainingWeek = atLeastZero(weeklyBudget + adjustmentMinutes - weekUsed);
        int remainingToday = atLeastZero(dailyCap - dayUsed);
        int remainingQuick = atLeastZero(quickBudget - quickUsed);

        return new Balance(
                weeklyBudget, adjustmentMinutes, weekUsed, remainingWeek,
                dailyCap, dayUsed, remainingToday,
                quickBudget, quickUsed, remainingQuick,
                Math.min(remainingWeek, remainingToday));
    }

    // ----------------------------------------------------------------- cutoff

    /**
     * Whether the local hour is still before the cut off. Derived in the local
     * zone, so the bedtime does not move twice a year with the clocks.
     */
    public boolean beforeCutoff(Instant now) {
        return calendar.timeOf(now).getHour() < settings.cutoffHour();
    }

    // --------------------------------------------------------------- starting

    /**
     * Whether a timer may start now. The budget is deliberately not consulted:
     * the app counts and does not enforce, and a child with nothing left must
     * still be able to log honestly instead of being pushed into using a device
     * without logging it.
     */
    public void requireStartAllowed(Role role, SessionType type, Instant now, Long openSessionId) {
        if (openSessionId != null) {
            throw new RuleViolation(RuleViolation.Kind.SESSION_ALREADY_OPEN,
                    "A session is already running. Stop it before starting another one.",
                    openSessionId);
        }
        requireTypeAllowedFor(role, type);
        if (type.boundByCutoff() && !beforeCutoff(now)) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    "Nothing starts at or after " + settings.cutoffHour() + ":00.");
        }
    }

    // ----------------------------------------------------------- manual entry

    /**
     * Whether an entry may be booked by hand.
     *
     * A child may book only the current local day, only before the cut off, and
     * at most one entry's worth of minutes. Without that, the weekly comparison
     * against what the devices report is pointless.
     *
     * A parent may book any day at any hour: correcting the account is their
     * job and it happens in the evening, after the cut off, once the child has
     * gone to bed.
     */
    public void requireManualBookingAllowed(Role role, SessionType type, LocalDate day,
                                            Instant now, int minutes) {
        if (minutes <= 0) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "An entry needs at least one minute.");
        }
        requireTypeAllowedFor(role, type);

        if (role == Role.PARENT) {
            return;
        }
        if (!day.equals(calendar.dayOf(now))) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    "You can only book time for today. Ask a parent to correct another day.");
        }
        if (!beforeCutoff(now)) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    "Time can only be booked before " + settings.cutoffHour() + ":00.");
        }
        if (minutes > settings.manualMaxMinutes()) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    "One entry can be at most " + settings.manualMaxMinutes() + " minutes.");
        }
    }

    private void requireTypeAllowedFor(Role role, SessionType type) {
        if (type.parentOnly() && role != Role.PARENT) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    "Only a parent can book " + type + " time.");
        }
    }

    // ------------------------------------------------------------- auto close

    /**
     * What a session still running at 23:59 is charged.
     *
     * The elapsed time is capped at what the day had left, so one forgotten
     * stop cannot wipe out a week. The parent sees the flag and decides whether
     * the number is fair.
     *
     * The balance passed in is the one without this session in it.
     */
    public int autoCloseMinutes(SessionType type, Instant startedAt, Instant closeAt,
                                Balance balanceWithoutThisSession) {
        int elapsed = calendar.minutesBetween(startedAt, closeAt);
        return switch (type) {
            case FUN -> Math.min(elapsed, balanceWithoutThisSession.remainingTodayMinutes());
            case QUICK -> Math.min(elapsed, balanceWithoutThisSession.remainingQuickMinutes());
            // a film costs nothing, so there is nothing to protect
            case FILM -> elapsed;
        };
    }

    // ----------------------------------------------------------- weekly check

    /**
     * Compares the log with what the devices report.
     *
     * Within tolerance in either direction the week is clean and earns the
     * bonus. Time missing from the log is deducted from the following week, up
     * to the cap, because a week with nothing left to lose has nothing left to
     * protect. More booked than the devices saw costs nothing but is flagged,
     * since it usually means an entry is wrong rather than that a rule was
     * broken.
     *
     * A week marked deliberate is never clean, even when the numbers match: the
     * parent is saying the child used a second account or changed a clock, and
     * handing that week a bonus hour would make the whole check worthless.
     */
    public CheckOutcome weeklyCheck(int loggedMinutes, int reportedMinutes, boolean deliberate) {
        int difference = reportedMinutes - loggedMinutes;
        int tolerance = settings.toleranceMinutes();

        boolean withinTolerance = Math.abs(difference) <= tolerance;
        boolean moreLoggedThanReported = difference < -tolerance;

        int penalty = 0;
        if (difference > tolerance) {
            penalty = difference;
        }
        if (deliberate) {
            penalty += settings.deliberatePenaltyMinutes();
        }
        penalty = Math.min(penalty, settings.maxPenaltyMinutes());

        boolean clean = withinTolerance && !deliberate;

        return new CheckOutcome(loggedMinutes, reportedMinutes, difference,
                penalty, clean, moreLoggedThanReported);
    }

    /** The week a check's deduction and bonus land on: the one after the week checked. */
    public LocalDate followingWeek(LocalDate checkedWeekStart) {
        return calendar.nextWeek(checkedWeekStart);
    }

    private static int atLeastZero(int value) {
        return Math.max(0, value);
    }
}
