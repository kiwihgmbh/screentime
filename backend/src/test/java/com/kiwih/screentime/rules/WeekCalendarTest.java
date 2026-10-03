package com.kiwih.screentime.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar is the foundation everything else stands on. If a day or a week
 * is wrong here, every balance in the app is wrong and nobody will be able to
 * tell why.
 *
 * The dates below are real: in 2026 the clocks in Europe/Zurich go forward on
 * Sunday 29 March and back on Sunday 25 October. Those two days are 23 and 25
 * hours long. The assertions name the exact UTC instants on purpose, so that
 * anyone who later "fixes" a day length by adding or subtracting hours breaks
 * this test immediately.
 */
class WeekCalendarTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private final WeekCalendar calendar = new WeekCalendar(ZURICH);

    @Nested
    @DisplayName("the week runs Monday to Sunday and is named by its Monday")
    class Weeks {

        @Test
        void mondayIsItsOwnWeekStart() {
            LocalDate monday = LocalDate.of(2026, 9, 28);
            assertThat(monday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(calendar.weekStartOf(monday)).isEqualTo(monday);
        }

        @Test
        void everyDayOfTheWeekReportsTheSameMonday() {
            LocalDate monday = LocalDate.of(2026, 9, 28);
            for (int i = 0; i < 7; i++) {
                assertThat(calendar.weekStartOf(monday.plusDays(i)))
                        .as("day %d of the week", i)
                        .isEqualTo(monday);
            }
            assertThat(calendar.weekStartOf(monday.plusDays(7)))
                    .as("the next Monday starts the next week")
                    .isEqualTo(monday.plusDays(7));
        }

        @Test
        void aWeekHasSevenDaysInOrder() {
            assertThat(calendar.daysOfWeek(LocalDate.of(2026, 9, 28)))
                    .containsExactly(
                            LocalDate.of(2026, 9, 28),
                            LocalDate.of(2026, 9, 29),
                            LocalDate.of(2026, 9, 30),
                            LocalDate.of(2026, 10, 1),
                            LocalDate.of(2026, 10, 2),
                            LocalDate.of(2026, 10, 3),
                            LocalDate.of(2026, 10, 4));
        }

        @Test
        void sundayAtOneMinuteToMidnightStillBelongsToTheWeekThatIsEnding() {
            Instant lastMinute = zurich(2026, 10, 4, 23, 59);
            assertThat(calendar.dayOf(lastMinute)).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(calendar.weekStartOf(lastMinute)).isEqualTo(LocalDate.of(2026, 9, 28));
        }

        @Test
        void mondayAtMidnightBelongsToTheWeekThatIsStarting() {
            Instant firstMinute = zurich(2026, 10, 5, 0, 0);
            assertThat(calendar.dayOf(firstMinute)).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(calendar.weekStartOf(firstMinute)).isEqualTo(LocalDate.of(2026, 10, 5));
        }

        @Test
        void theWeekRangeIsHalfOpenSoNoInstantFallsInTwoWeeks() {
            LocalDate monday = LocalDate.of(2026, 9, 28);
            Instant end = calendar.endOfWeekExclusive(monday);
            assertThat(end).isEqualTo(calendar.startOfWeek(monday.plusDays(7)));
            assertThat(calendar.weekStartOf(end)).isEqualTo(monday.plusDays(7));
        }
    }

    @Nested
    @DisplayName("daylight saving time changes a day's length, never its budget")
    class DaylightSaving {

        private static final LocalDate SPRING_FORWARD = LocalDate.of(2026, 3, 29);
        private static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25);

        @Test
        void theTestDatesAreTheRealTransitionDays() {
            assertThat(SPRING_FORWARD.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(FALL_BACK.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(SPRING_FORWARD.plusDays(7).getMonth()).isEqualTo(Month.APRIL);
            assertThat(FALL_BACK.plusDays(7).getMonth()).isEqualTo(Month.NOVEMBER);
        }

        @Test
        void theSpringForwardDayIsTwentyThreeHoursLong() {
            assertThat(calendar.startOfDay(SPRING_FORWARD))
                    .isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
            assertThat(calendar.endOfDayExclusive(SPRING_FORWARD))
                    .isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
            assertThat(Duration.between(
                    calendar.startOfDay(SPRING_FORWARD),
                    calendar.endOfDayExclusive(SPRING_FORWARD)).toHours())
                    .isEqualTo(23);
        }

        @Test
        void theFallBackDayIsTwentyFiveHoursLong() {
            assertThat(calendar.startOfDay(FALL_BACK))
                    .isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
            assertThat(calendar.endOfDayExclusive(FALL_BACK))
                    .isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
            assertThat(Duration.between(
                    calendar.startOfDay(FALL_BACK),
                    calendar.endOfDayExclusive(FALL_BACK)).toHours())
                    .isEqualTo(25);
        }

        @Test
        void theWeekAroundASwitchIsStillSevenCalendarDays() {
            LocalDate marchMonday = calendar.weekStartOf(SPRING_FORWARD);
            LocalDate octoberMonday = calendar.weekStartOf(FALL_BACK);
            assertThat(marchMonday).isEqualTo(LocalDate.of(2026, 3, 23));
            assertThat(octoberMonday).isEqualTo(LocalDate.of(2026, 10, 19));

            assertThat(calendar.daysOfWeek(marchMonday)).hasSize(7);
            assertThat(Duration.between(
                    calendar.startOfWeek(marchMonday),
                    calendar.endOfWeekExclusive(marchMonday)).toHours())
                    .as("the short week is 167 hours but still seven days")
                    .isEqualTo(167);
            assertThat(Duration.between(
                    calendar.startOfWeek(octoberMonday),
                    calendar.endOfWeekExclusive(octoberMonday)).toHours())
                    .as("the long week is 169 hours but still seven days")
                    .isEqualTo(169);
        }

        @Test
        void bothPassesOfTheRepeatedLocalHourBelongToTheSameDay() {
            // on the fall back day 02:30 local happens twice: once in summer
            // time and once in winter time. An instant is never ambiguous,
            // which is exactly why instants are what we store.
            Instant firstPass = Instant.parse("2026-10-25T00:30:00Z");
            Instant secondPass = Instant.parse("2026-10-25T01:30:00Z");

            assertThat(firstPass.atZone(ZURICH).toLocalTime()).isEqualTo(LocalTime.of(2, 30));
            assertThat(secondPass.atZone(ZURICH).toLocalTime()).isEqualTo(LocalTime.of(2, 30));
            assertThat(calendar.dayOf(firstPass)).isEqualTo(FALL_BACK);
            assertThat(calendar.dayOf(secondPass)).isEqualTo(FALL_BACK);
        }

        @Test
        void theHourThatDoesNotExistIsNotNeededToNameADay() {
            // 02:30 local does not exist on the spring forward day. The instant
            // that follows the gap is 03:30 local and belongs to that day.
            Instant afterTheGap = Instant.parse("2026-03-29T01:30:00Z");
            assertThat(afterTheGap.atZone(ZURICH).toLocalTime()).isEqualTo(LocalTime.of(3, 30));
            assertThat(calendar.dayOf(afterTheGap)).isEqualTo(SPRING_FORWARD);
        }
    }

    @Nested
    @DisplayName("a session belongs to the local day it started on")
    class SessionDays {

        @Test
        void aSessionThatRunsPastTheCutOffStillCountsToTheDayItStarted() {
            Instant start = zurich(2026, 10, 1, 19, 50);
            Instant end = zurich(2026, 10, 1, 20, 30);
            assertThat(calendar.dayOf(start)).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(calendar.minutesBetween(start, end)).isEqualTo(40);
        }

        @Test
        void aSessionThatRunsPastMidnightCountsToTheDayItStarted() {
            Instant start = zurich(2026, 10, 4, 23, 50);
            Instant end = zurich(2026, 10, 5, 0, 30);
            assertThat(calendar.dayOf(start))
                    .as("started on Sunday, so it is Sunday's time and that week's time")
                    .isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(calendar.weekStartOf(start)).isEqualTo(LocalDate.of(2026, 9, 28));
            assertThat(calendar.minutesBetween(start, end)).isEqualTo(40);
        }

        @Test
        void partMinutesAreRoundedDown() {
            Instant start = Instant.parse("2026-10-01T10:00:00Z");
            assertThat(calendar.minutesBetween(start, start.plusSeconds(59))).isZero();
            assertThat(calendar.minutesBetween(start, start.plusSeconds(60))).isEqualTo(1);
            assertThat(calendar.minutesBetween(start, start.plusSeconds(119))).isEqualTo(1);
        }

        @Test
        void aNegativeRangeIsNeverNegativeMinutes() {
            Instant start = Instant.parse("2026-10-01T10:00:00Z");
            assertThat(calendar.minutesBetween(start, start.minusSeconds(600))).isZero();
        }

        @Test
        void aSessionAcrossTheFallBackNightIsMeasuredInRealMinutes() {
            // 01:30 local summer time to 02:30 local winter time is two hours
            // of real time even though the clock shows one
            Instant start = Instant.parse("2026-10-24T23:30:00Z");
            Instant end = Instant.parse("2026-10-25T01:30:00Z");
            assertThat(start.atZone(ZURICH).toLocalTime()).isEqualTo(LocalTime.of(1, 30));
            assertThat(end.atZone(ZURICH).toLocalTime()).isEqualTo(LocalTime.of(2, 30));
            assertThat(calendar.minutesBetween(start, end))
                    .as("minutes of use are real minutes, not clock readings")
                    .isEqualTo(120);
        }
    }

    @Test
    void saturdayAndSundayAreTheWeekend() {
        assertThat(calendar.isWeekend(LocalDate.of(2026, 10, 2))).isFalse(); // Friday
        assertThat(calendar.isWeekend(LocalDate.of(2026, 10, 3))).isTrue();  // Saturday
        assertThat(calendar.isWeekend(LocalDate.of(2026, 10, 4))).isTrue();  // Sunday
        assertThat(calendar.isWeekend(LocalDate.of(2026, 10, 5))).isFalse(); // Monday
    }

    private static Instant zurich(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZURICH).toInstant();
    }
}
