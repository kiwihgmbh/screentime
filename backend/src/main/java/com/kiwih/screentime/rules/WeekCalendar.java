package com.kiwih.screentime.rules;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Turns instants into the local days and weeks the rules are written in, and
 * back again. This is the only place in the application that knows a time zone.
 *
 * Everything is derived with java.time. Nothing here adds or subtracts hours to
 * reach a local time, because two days a year that answer is wrong: the last
 * Sunday in March is 23 hours long and the last Sunday in October is 25.
 * Budgets are minutes of use and never change on those days.
 */
public final class WeekCalendar {

    private final ZoneId zone;

    public WeekCalendar(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public ZoneId zone() {
        return zone;
    }

    /** The local day a session started on. A session belongs to this day whatever time it ends. */
    public LocalDate dayOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    public LocalTime timeOf(Instant instant) {
        return instant.atZone(zone).toLocalTime();
    }

    /** The Monday that names the week a day belongs to. */
    public LocalDate weekStartOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate weekStartOf(Instant instant) {
        return weekStartOf(dayOf(instant));
    }

    public LocalDate nextWeek(LocalDate weekStart) {
        return weekStart.plusWeeks(1);
    }

    public LocalDate previousWeek(LocalDate weekStart) {
        return weekStart.minusWeeks(1);
    }

    /**
     * Midnight local time, as an instant. {@code atStartOfDay} picks the right
     * side of a clock change by itself, which is why it is used instead of any
     * arithmetic on hours.
     */
    public Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(zone).toInstant();
    }

    /** The start of the next day. Ranges are half open so no instant lands in two days. */
    public Instant endOfDayExclusive(LocalDate day) {
        return startOfDay(day.plusDays(1));
    }

    public Instant startOfWeek(LocalDate weekStart) {
        return startOfDay(weekStart);
    }

    public Instant endOfWeekExclusive(LocalDate weekStart) {
        return startOfDay(weekStart.plusDays(7));
    }

    /** Monday to Sunday, in order. Always seven entries, however long the week is in hours. */
    public List<LocalDate> daysOfWeek(LocalDate weekStart) {
        return IntStream.range(0, 7).mapToObj(weekStart::plusDays).toList();
    }

    public boolean isWeekend(LocalDate day) {
        DayOfWeek d = day.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    /**
     * Whole seconds of real elapsed time, never negative. A part second is
     * dropped, so a stop never charges a second that has not fully passed.
     */
    public int secondsBetween(Instant from, Instant to) {
        long seconds = Duration.between(from, to).getSeconds();
        return seconds <= 0 ? 0 : (int) Math.min(seconds, Integer.MAX_VALUE);
    }

    /**
     * The moment the scheduler closes whatever is still running, 23:59 local
     * time on the given day.
     */
    public Instant autoCloseMoment(LocalDate day) {
        return day.atTime(23, 59).atZone(zone).toInstant();
    }
}
