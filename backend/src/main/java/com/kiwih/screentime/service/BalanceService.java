package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.Adjustment;
import com.kiwih.screentime.domain.Session;
import com.kiwih.screentime.repo.AdjustmentRepository;
import com.kiwih.screentime.repo.SessionRepository;
import com.kiwih.screentime.rules.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Loads what a week actually contains and hands it to the rules.
 *
 * Every total here is derived on read from the stored sessions and adjustments.
 * Nothing is cached and nothing is kept in a column: the data set is a handful
 * of rows a week, and a stored total that drifts out of sync is a bug nobody
 * ever finds.
 */
@Service
public class BalanceService {

    private final SessionRepository sessions;
    private final AdjustmentRepository adjustments;

    public BalanceService(SessionRepository sessions, AdjustmentRepository adjustments) {
        this.sessions = sessions;
        this.adjustments = adjustments;
    }

    /** Every session of a week, in start order. The time zone is applied once, here. */
    @Transactional(readOnly = true)
    public List<Session> sessionsOfWeek(Long userId, LocalDate weekStart, WeekCalendar calendar) {
        return sessions.findStartedBetween(userId,
                calendar.startOfWeek(weekStart), calendar.endOfWeekExclusive(weekStart));
    }

    @Transactional(readOnly = true)
    public List<Session> sessionsOfDay(Long userId, LocalDate day, WeekCalendar calendar) {
        return sessions.findStartedBetween(userId,
                calendar.startOfDay(day), calendar.endOfDayExclusive(day));
    }

    @Transactional(readOnly = true)
    public int adjustmentMinutes(LocalDate weekStart) {
        return adjustments.findByWeekStartOrderByCreatedAtAsc(weekStart).stream()
                .mapToInt(Adjustment::getMinutes).sum();
    }

    @Transactional(readOnly = true)
    public List<Adjustment> adjustmentsOfWeek(LocalDate weekStart) {
        return adjustments.findByWeekStartOrderByCreatedAtAsc(weekStart);
    }

    /**
     * The account as it stands right now, including the minutes a running
     * session has already used, so the child's countdown is honest.
     */
    @Transactional(readOnly = true)
    public Balance balanceNow(Long userId, LocalDate day, WeekState week,
                              ScreentimeRules rules, Instant now) {
        List<Booking> bookings = toBookings(
                sessionsOfWeek(userId, week.weekStart(), rules.calendar()), rules, now);
        return rules.balance(day, week, bookings, adjustmentMinutes(week.weekStart()));
    }

    /**
     * The account counting closed sessions only. Used when the minutes of the
     * session in hand are not decided yet, which is the case while closing one.
     */
    @Transactional(readOnly = true)
    public Balance balanceOfClosedSessions(Long userId, LocalDate day, WeekState week,
                                           ScreentimeRules rules) {
        List<Booking> bookings = sessionsOfWeek(userId, week.weekStart(), rules.calendar()).stream()
                .filter(s -> !s.isOpen())
                .map(s -> new Booking(s.getType(), s.getStartedAt(), s.countedMinutes()))
                .toList();
        return rules.balance(day, week, bookings, adjustmentMinutes(week.weekStart()));
    }

    /**
     * A running session contributes the minutes it has used so far. A closed one
     * contributes what was recorded.
     */
    public List<Booking> toBookings(List<Session> sessions, ScreentimeRules rules, Instant now) {
        return sessions.stream()
                .map(s -> new Booking(s.getType(), s.getStartedAt(),
                        s.isOpen() ? rules.calendar().minutesBetween(s.getStartedAt(), now)
                                   : s.countedMinutes()))
                .toList();
    }

    /** The FUN minutes a week has logged. This is the number the weekly check compares. */
    @Transactional(readOnly = true)
    public int loggedFunMinutes(Long userId, LocalDate weekStart, WeekCalendar calendar) {
        return sessionsOfWeek(userId, weekStart, calendar).stream()
                .filter(s -> s.getType().countsAgainstWeek())
                .mapToInt(Session::countedMinutes)
                .sum();
    }
}
