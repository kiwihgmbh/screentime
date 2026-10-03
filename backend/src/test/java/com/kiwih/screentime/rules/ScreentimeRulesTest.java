package com.kiwih.screentime.rules;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreentimeRulesTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final WeekCalendar CALENDAR = new WeekCalendar(ZURICH);

    // Monday 28 September 2026 to Sunday 4 October 2026
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 2);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);

    private final ScreentimeRules rules = new ScreentimeRules(
            WeekSettings.withoutHolidays(MONDAY, ScreentimeSettings.TERM_DEFAULTS, ScreentimeSettings.HOLIDAY_DEFAULTS),
            CALENDAR);

    @Nested
    @DisplayName("the daily ceiling follows the weekday and the bonus")
    class DailyCeiling {

        @Test
        void schoolDaysGetTheWeekdayCeiling() {
            for (LocalDate day : List.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2),
                    MONDAY.plusDays(3), FRIDAY)) {
                assertThat(rules.dailyCapMinutes(day, plainWeek()))
                        .as("%s", day.getDayOfWeek())
                        .isEqualTo(60);
            }
        }

        @Test
        void saturdayAndSundayGetTheWeekendCeiling() {
            assertThat(rules.dailyCapMinutes(SATURDAY, plainWeek())).isEqualTo(120);
            assertThat(rules.dailyCapMinutes(SUNDAY, plainWeek())).isEqualTo(120);
        }

        // holiday days and holiday weeks: SettingsResolverTest

        @Test
        void anActiveBonusRaisesOnlyTheWeekendCeiling() {
            WeekState bonus = new WeekState(MONDAY, true);
            assertThat(rules.dailyCapMinutes(FRIDAY, bonus))
                    .as("a school day is not affected by the bonus")
                    .isEqualTo(60);
            assertThat(rules.dailyCapMinutes(SATURDAY, bonus)).isEqualTo(150);
            assertThat(rules.dailyCapMinutes(SUNDAY, bonus)).isEqualTo(150);
        }

        @Test
        void theCeilingDoesNotChangeOnTheDaysTheClockChanges() {
            // the spring forward Sunday is 23 hours long and the fall back
            // Sunday is 25, and neither is a reason to move a budget
            assertThat(rules.dailyCapMinutes(LocalDate.of(2026, 3, 29), plainWeek())).isEqualTo(120);
            assertThat(rules.dailyCapMinutes(LocalDate.of(2026, 10, 25), plainWeek())).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("the weekly budget rises with the bonus and is never cumulative")
    class WeeklyBudget {

        @Test
        void aPlainWeekGetsTheWeeklyBudget() {
            assertThat(rules.weeklyBudgetMinutes(plainWeek())).isEqualTo(480);
        }

        @Test
        void aBonusWeekGetsTheWeeklyBudgetPlusTheBonus() {
            assertThat(rules.weeklyBudgetMinutes(new WeekState(MONDAY, true))).isEqualTo(540);
        }

        @Test
        void theBonusIsSetOrNotSetAndNeverAddsUpAcrossWeeks() {
            // two clean weeks in a row still mean one bonus, not two
            WeekState first = new WeekState(MONDAY, true);
            WeekState second = new WeekState(MONDAY.plusDays(7), true);
            assertThat(rules.weeklyBudgetMinutes(first)).isEqualTo(540);
            assertThat(rules.weeklyBudgetMinutes(second))
                    .as("a second clean week does not stack to 600")
                    .isEqualTo(540);
        }
    }

    @Nested
    @DisplayName("nothing starts at or after the cut off, except the film")
    class CutOff {

        @Test
        void oneMinuteBeforeTheCutOffIsAllowed() {
            assertThat(rules.beforeCutoff(at(FRIDAY, 19, 59))).isTrue();
        }

        @Test
        void theCutOffHourItselfIsAlreadyTooLate() {
            assertThat(rules.beforeCutoff(at(FRIDAY, 20, 0))).isFalse();
            assertThat(rules.beforeCutoff(at(FRIDAY, 20, 1))).isFalse();
        }

        @Test
        void theCutOffIsLocalInSummerAndInWinter() {
            // 20:00 in Zurich is 18:00Z in summer and 19:00Z in winter. An hour
            // fixed in UTC would silently move the bedtime twice a year.
            assertThat(rules.beforeCutoff(Instant.parse("2026-07-01T17:59:00Z"))).isTrue();
            assertThat(rules.beforeCutoff(Instant.parse("2026-07-01T18:00:00Z"))).isFalse();
            assertThat(rules.beforeCutoff(Instant.parse("2026-12-01T18:59:00Z"))).isTrue();
            assertThat(rules.beforeCutoff(Instant.parse("2026-12-01T19:00:00Z"))).isFalse();
        }

        @Test
        void aTimerForFunOrQuickCannotStartAfterTheCutOff() {
            assertThatThrownBy(() -> rules.requireStartAllowed(
                    Role.CHILD, SessionType.FUN, at(FRIDAY, 20, 0), null))
                    .isInstanceOf(RuleViolation.class)
                    .extracting("kind").isEqualTo(RuleViolation.Kind.NOT_ALLOWED);

            assertThatThrownBy(() -> rules.requireStartAllowed(
                    Role.CHILD, SessionType.QUICK, at(FRIDAY, 20, 0), null))
                    .isInstanceOf(RuleViolation.class);
        }

        @Test
        void theCutOffBindsParentsToo() {
            // rule four says no session of any type, and a parent starting a
            // timer at nine in the evening is not a correction
            assertThatThrownBy(() -> rules.requireStartAllowed(
                    Role.PARENT, SessionType.FUN, at(FRIDAY, 21, 0), null))
                    .isInstanceOf(RuleViolation.class);
        }

        @Test
        void theFilmIsTheOneThingThatMayStartAfterTheCutOff() {
            rules.requireStartAllowed(Role.PARENT, SessionType.FILM, at(FRIDAY, 20, 30), null);
        }
    }

    @Nested
    @DisplayName("who may start what")
    class StartPermissions {

        @Test
        void aChildMayStartFunAndQuick() {
            rules.requireStartAllowed(Role.CHILD, SessionType.FUN, at(FRIDAY, 16, 0), null);
            rules.requireStartAllowed(Role.CHILD, SessionType.QUICK, at(FRIDAY, 16, 0), null);
        }

        @Test
        void aChildMayNotStartTheFilm() {
            assertThatThrownBy(() -> rules.requireStartAllowed(
                    Role.CHILD, SessionType.FILM, at(FRIDAY, 19, 0), null))
                    .isInstanceOf(RuleViolation.class)
                    .extracting("kind").isEqualTo(RuleViolation.Kind.NOT_ALLOWED);
        }

        @Test
        void aSecondSessionIsRefusedAndNamesTheOpenOne() {
            assertThatThrownBy(() -> rules.requireStartAllowed(
                    Role.CHILD, SessionType.FUN, at(FRIDAY, 16, 0), 42L))
                    .isInstanceOf(RuleViolation.class)
                    .extracting("kind").isEqualTo(RuleViolation.Kind.SESSION_ALREADY_OPEN);

            assertThat(violation(() -> rules.requireStartAllowed(
                    Role.CHILD, SessionType.FUN, at(FRIDAY, 16, 0), 42L)).openSessionId())
                    .isEqualTo(42L);
        }

        @Test
        void anExhaustedBudgetDoesNotBlockAStart() {
            // the app counts, it does not enforce. A child who has nothing left
            // must still be able to log honestly rather than be pushed into
            // using a device without logging it.
            rules.requireStartAllowed(Role.CHILD, SessionType.FUN, at(FRIDAY, 16, 0), null);
        }
    }

    @Nested
    @DisplayName("what a child may book by hand")
    class ManualBooking {

        @Test
        void aChildMayBookTodayBeforeTheCutOff() {
            rules.requireManualBookingAllowed(Role.CHILD, SessionType.FUN, FRIDAY, at(FRIDAY, 17, 0), 30);
        }

        @Test
        void aChildMayNotBookYesterday() {
            assertThat(violation(() -> rules.requireManualBookingAllowed(
                    Role.CHILD, SessionType.FUN, FRIDAY.minusDays(1), at(FRIDAY, 17, 0), 30)).kind())
                    .isEqualTo(RuleViolation.Kind.NOT_ALLOWED);
        }

        @Test
        void aChildMayNotBookTomorrow() {
            assertThat(violation(() -> rules.requireManualBookingAllowed(
                    Role.CHILD, SessionType.FUN, FRIDAY.plusDays(1), at(FRIDAY, 17, 0), 30)).kind())
                    .isEqualTo(RuleViolation.Kind.NOT_ALLOWED);
        }

        @Test
        void aChildMayNotBookAfterTheCutOff() {
            assertThat(violation(() -> rules.requireManualBookingAllowed(
                    Role.CHILD, SessionType.FUN, FRIDAY, at(FRIDAY, 20, 0), 30)).kind())
                    .isEqualTo(RuleViolation.Kind.NOT_ALLOWED);
        }

        @Test
        void aChildMayNotBookMoreThanTheSingleEntryLimit() {
            rules.requireManualBookingAllowed(Role.CHILD, SessionType.FUN, FRIDAY, at(FRIDAY, 17, 0), 240);
            assertThat(violation(() -> rules.requireManualBookingAllowed(
                    Role.CHILD, SessionType.FUN, FRIDAY, at(FRIDAY, 17, 0), 241)).kind())
                    .isEqualTo(RuleViolation.Kind.NOT_ALLOWED);
        }

        @Test
        void aParentMayBookAnyDayAtAnyTime() {
            // corrections are the parents' job and they happen in the evening,
            // after the child has gone to bed
            rules.requireManualBookingAllowed(Role.PARENT, SessionType.FUN,
                    FRIDAY.minusDays(30), at(FRIDAY, 22, 0), 600);
            rules.requireManualBookingAllowed(Role.PARENT, SessionType.FILM,
                    FRIDAY.plusDays(1), at(FRIDAY, 22, 0), 120);
        }

        @Test
        void nobodyMayBookZeroOrNegativeMinutes() {
            assertThat(violation(() -> rules.requireManualBookingAllowed(
                    Role.PARENT, SessionType.FUN, FRIDAY, at(FRIDAY, 17, 0), 0)).kind())
                    .isEqualTo(RuleViolation.Kind.INVALID);
            assertThat(violation(() -> rules.requireManualBookingAllowed(
                    Role.PARENT, SessionType.FUN, FRIDAY, at(FRIDAY, 17, 0), -10)).kind())
                    .isEqualTo(RuleViolation.Kind.INVALID);
        }

        @Test
        void aChildMayNotBookAFilm() {
            assertThat(violation(() -> rules.requireManualBookingAllowed(
                    Role.CHILD, SessionType.FILM, FRIDAY, at(FRIDAY, 17, 0), 90)).kind())
                    .isEqualTo(RuleViolation.Kind.NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("the balance is derived, and available now is the smaller of the two")
    class Balances {

        @Test
        void anEmptyWeekLeavesTheWholeBudget() {
            Balance b = rules.balance(FRIDAY, plainWeek(), List.of(), 0);
            assertThat(b.weeklyBudgetSeconds()).isEqualTo(minutes(480));
            assertThat(b.remainingWeekSeconds()).isEqualTo(minutes(480));
            assertThat(b.dailyCapSeconds()).isEqualTo(minutes(60));
            assertThat(b.remainingTodaySeconds()).isEqualTo(minutes(60));
            assertThat(b.remainingQuickSeconds()).isEqualTo(minutes(15));
            assertThat(b.availableNowSeconds())
                    .as("the day is the binding limit at the start of a week")
                    .isEqualTo(minutes(60));
        }

        @Test
        void availableNowIsTheRemainingWeekWhenTheWeekIsNearlyGone() {
            // 450 of 480 used earlier in the week, nothing used today
            List<Booking> week = List.of(
                    fun(MONDAY, 10, 0, 60),
                    fun(MONDAY.plusDays(1), 10, 0, 60),
                    fun(MONDAY.plusDays(2), 10, 0, 60),
                    fun(MONDAY.plusDays(3), 10, 0, 60),
                    fun(SATURDAY, 10, 0, 120),
                    fun(SUNDAY, 10, 0, 90));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.weekUsedSeconds()).isEqualTo(minutes(450));
            assertThat(b.remainingWeekSeconds()).isEqualTo(minutes(30));
            assertThat(b.remainingTodaySeconds()).isEqualTo(minutes(60));
            assertThat(b.availableNowSeconds())
                    .as("the week is the binding limit now")
                    .isEqualTo(minutes(30));
        }

        @Test
        void onlyFunCountsAgainstTheWeek() {
            List<Booking> week = List.of(
                    fun(FRIDAY, 14, 0, 30),
                    quick(FRIDAY, 15, 0, 10),
                    film(FRIDAY, 20, 30, 110));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.weekUsedSeconds()).isEqualTo(minutes(30));
            assertThat(b.dayUsedSeconds()).isEqualTo(minutes(30));
            assertThat(b.remainingWeekSeconds()).isEqualTo(minutes(450));
            assertThat(b.remainingTodaySeconds()).isEqualTo(minutes(30));
            assertThat(b.quickUsedSeconds()).isEqualTo(minutes(10));
            assertThat(b.remainingQuickSeconds()).isEqualTo(minutes(5));
        }

        @Test
        void theQuickBudgetIsDailyAndSeparate() {
            List<Booking> week = List.of(
                    quick(MONDAY, 9, 0, 15),
                    quick(FRIDAY, 9, 0, 5));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.quickUsedSeconds())
                    .as("Monday's quick minutes are not today's")
                    .isEqualTo(minutes(5));
            assertThat(b.remainingQuickSeconds()).isEqualTo(minutes(10));
            assertThat(b.remainingWeekSeconds())
                    .as("quick never touches the weekly budget")
                    .isEqualTo(minutes(480));
        }

        @Test
        void onlyTodayCountsAgainstTheDay() {
            List<Booking> week = List.of(fun(MONDAY, 10, 0, 60), fun(FRIDAY, 10, 0, 20));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.dayUsedSeconds()).isEqualTo(minutes(20));
            assertThat(b.remainingTodaySeconds()).isEqualTo(minutes(40));
            assertThat(b.weekUsedSeconds()).isEqualTo(minutes(80));
        }

        @Test
        void aSessionStartedJustBeforeMidnightCountsToTheDayItStarted() {
            // started Thursday 23:50, so Friday's ceiling is untouched
            List<Booking> week = List.of(fun(FRIDAY.minusDays(1), 23, 50, 40));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.dayUsedSeconds()).isZero();
            assertThat(b.remainingTodaySeconds()).isEqualTo(minutes(60));
            assertThat(b.weekUsedSeconds()).isEqualTo(minutes(40));
        }

        @Test
        void aNegativeAdjustmentComesOffTheWeek() {
            Balance b = rules.balance(FRIDAY, plainWeek(), List.of(), -120);
            assertThat(b.weeklyBudgetSeconds())
                    .as("the budget itself is reported without the adjustment")
                    .isEqualTo(minutes(480));
            assertThat(b.adjustmentSeconds()).isEqualTo(minutes(-120));
            assertThat(b.remainingWeekSeconds()).isEqualTo(minutes(360));
        }

        @Test
        void aPositiveAdjustmentIsAGift() {
            Balance b = rules.balance(FRIDAY, plainWeek(), List.of(), 30);
            assertThat(b.remainingWeekSeconds()).isEqualTo(minutes(510));
        }

        @Test
        void nothingEverGoesBelowZero() {
            List<Booking> week = List.of(fun(FRIDAY, 10, 0, 600), quick(FRIDAY, 9, 0, 90));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, -200);
            assertThat(b.remainingWeekSeconds()).isZero();
            assertThat(b.remainingTodaySeconds()).isZero();
            assertThat(b.remainingQuickSeconds()).isZero();
            assertThat(b.availableNowSeconds()).isZero();
        }

        @Test
        void unusedTimeDoesNotCarryToTheNextDay() {
            // nothing used on Monday to Thursday, and Friday still only gets 60
            Balance b = rules.balance(FRIDAY, plainWeek(), List.of(), 0);
            assertThat(b.remainingTodaySeconds())
                    .as("four unused days do not become Friday's time")
                    .isEqualTo(minutes(60));
        }

        @Test
        void aSessionShorterThanAMinuteCostsItsSeconds() {
            // 40 seconds used to round down to nothing, which made a string of
            // short sessions free
            List<Booking> week = List.of(new Booking(SessionType.FUN, at(FRIDAY, 16, 0), 40));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.weekUsedSeconds()).isEqualTo(40);
            assertThat(b.remainingTodaySeconds()).isEqualTo(minutes(60) - 40);
            assertThat(b.remainingWeekSeconds()).isEqualTo(minutes(480) - 40);
        }

        @Test
        void partMinutesAddUpInsteadOfBeingLost() {
            // three sessions of 1 min 40 s are 5 minutes, not 3
            List<Booking> week = List.of(
                    new Booking(SessionType.FUN, at(FRIDAY, 14, 0), 100),
                    new Booking(SessionType.FUN, at(FRIDAY, 15, 0), 100),
                    new Booking(SessionType.FUN, at(FRIDAY, 16, 0), 100));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.dayUsedSeconds()).isEqualTo(minutes(5));
        }

        @Test
        void quickSecondsComeOffTheQuickBudget() {
            List<Booking> week = List.of(new Booking(SessionType.QUICK, at(FRIDAY, 9, 0), 75));
            Balance b = rules.balance(FRIDAY, plainWeek(), week, 0);
            assertThat(b.quickUsedSeconds()).isEqualTo(75);
            assertThat(b.remainingQuickSeconds()).isEqualTo(minutes(15) - 75);
        }

        @Test
        void aBonusWeekendIsVisibleInTheBalance() {
            Balance b = rules.balance(SATURDAY, new WeekState(MONDAY, true), List.of(), 0);
            assertThat(b.weeklyBudgetSeconds()).isEqualTo(minutes(540));
            assertThat(b.dailyCapSeconds()).isEqualTo(minutes(150));
            assertThat(b.availableNowSeconds()).isEqualTo(minutes(150));
        }
    }

    @Nested
    @DisplayName("a forgotten stop costs the remaining day, never the week")
    class AutoClose {

        @Test
        void anAutoClosedSessionIsCappedAtTheRemainingDailyCeiling() {
            // started at 16:00 and still open at 23:59, which is nearly eight
            // hours. Only the 60 minutes the day had left may be charged.
            Instant started = at(FRIDAY, 16, 0);
            Instant closeAt = at(FRIDAY, 23, 59);
            Balance before = rules.balance(FRIDAY, plainWeek(), List.of(), 0);

            assertThat(CALENDAR.secondsBetween(started, closeAt)).isEqualTo(minutes(479));
            assertThat(rules.autoCloseSeconds(SessionType.FUN, started, closeAt, before))
                    .isEqualTo(minutes(60));
        }

        @Test
        void theCapUsesWhatIsLeftAfterTheRestOfTheDay() {
            Instant started = at(FRIDAY, 19, 0);
            Instant closeAt = at(FRIDAY, 23, 59);
            Balance before = rules.balance(FRIDAY, plainWeek(), List.of(fun(FRIDAY, 10, 0, 45)), 0);

            assertThat(before.remainingTodaySeconds()).isEqualTo(minutes(15));
            assertThat(rules.autoCloseSeconds(SessionType.FUN, started, closeAt, before))
                    .isEqualTo(minutes(15));
        }

        @Test
        void aShortForgottenSessionIsChargedInFull() {
            Instant started = at(FRIDAY, 23, 30);
            Instant closeAt = at(FRIDAY, 23, 59);
            Balance before = rules.balance(FRIDAY, plainWeek(), List.of(), 0);
            assertThat(rules.autoCloseSeconds(SessionType.FUN, started, closeAt, before))
                    .isEqualTo(minutes(29));
        }

        @Test
        void aForgottenQuickSessionIsCappedAtTheQuickBudget() {
            Instant started = at(FRIDAY, 9, 0);
            Instant closeAt = at(FRIDAY, 23, 59);
            Balance before = rules.balance(FRIDAY, plainWeek(), List.of(), 0);
            assertThat(rules.autoCloseSeconds(SessionType.QUICK, started, closeAt, before))
                    .isEqualTo(minutes(15));
        }

        @Test
        void aForgottenFilmIsChargedInFullBecauseItCostsNothing() {
            Instant started = at(FRIDAY, 20, 30);
            Instant closeAt = at(FRIDAY, 23, 59);
            Balance before = rules.balance(FRIDAY, plainWeek(), List.of(), 0);
            assertThat(rules.autoCloseSeconds(SessionType.FILM, started, closeAt, before))
                    .isEqualTo(minutes(209));
        }

        @Test
        void theCapIsExactToTheSecond() {
            // 59 min 30 s used, so a forgotten session gets the last 30 seconds
            Instant started = at(FRIDAY, 19, 0);
            Instant closeAt = at(FRIDAY, 23, 59);
            Balance before = rules.balance(FRIDAY, plainWeek(),
                    List.of(new Booking(SessionType.FUN, at(FRIDAY, 10, 0), minutes(59) + 30)), 0);
            assertThat(rules.autoCloseSeconds(SessionType.FUN, started, closeAt, before)).isEqualTo(30);
        }

        @Test
        void aDayWithNothingLeftChargesNothingMore() {
            Instant started = at(FRIDAY, 21, 0);
            Instant closeAt = at(FRIDAY, 23, 59);
            Balance before = rules.balance(FRIDAY, plainWeek(), List.of(fun(FRIDAY, 10, 0, 60)), 0);
            assertThat(before.remainingTodaySeconds()).isZero();
            assertThat(rules.autoCloseSeconds(SessionType.FUN, started, closeAt, before)).isZero();
        }
    }

    private static WeekState plainWeek() {
        return WeekState.plain(MONDAY);
    }

    private static Booking fun(LocalDate day, int hour, int minute, int minutes) {
        return new Booking(SessionType.FUN, at(day, hour, minute), minutes(minutes));
    }

    private static Booking quick(LocalDate day, int hour, int minute, int minutes) {
        return new Booking(SessionType.QUICK, at(day, hour, minute), minutes(minutes));
    }

    private static Booking film(LocalDate day, int hour, int minute, int minutes) {
        return new Booking(SessionType.FILM, at(day, hour, minute), minutes(minutes));
    }

    private static int minutes(int minutes) {
        return minutes * 60;
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(ZURICH).toInstant();
    }

    private static RuleViolation violation(Runnable call) {
        try {
            call.run();
        } catch (RuleViolation e) {
            return e;
        }
        throw new AssertionError("expected a RuleViolation but the call was allowed");
    }
}
