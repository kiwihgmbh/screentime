package com.kiwih.screentime.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Sunday comparison. This is the part a child will check the hardest, so
 * every branch gets a test and every number in a test is one a parent could
 * read out loud.
 *
 * With the defaults: tolerance 10 minutes, largest penalty 120 minutes, and
 * 60 minutes added when the parent marks the week as deliberate.
 */
class WeeklyCheckTest {

    private final ScreentimeRules rules = new ScreentimeRules(
            ScreentimeSettings.DEFAULTS, new WeekCalendar(ZoneId.of("Europe/Zurich")));

    @Nested
    @DisplayName("a week that matches is clean")
    class CleanWeeks {

        @Test
        void aSmallDifferenceIsStillAMatch() {
            CheckOutcome o = rules.weeklyCheck(400, 405, false);
            assertThat(o.difference()).isEqualTo(5);
            assertThat(o.clean()).isTrue();
            assertThat(o.penaltyMinutes()).isZero();
            assertThat(o.adjustmentMinutes()).isZero();
            assertThat(o.bonusForFollowingWeek()).isTrue();
            assertThat(o.moreLoggedThanReported()).isFalse();
        }

        @Test
        void theToleranceItselfIsStillAMatch() {
            assertThat(rules.weeklyCheck(400, 410, false).clean()).isTrue();
            assertThat(rules.weeklyCheck(400, 390, false).clean())
                    .as("ten minutes in either direction is a match")
                    .isTrue();
        }

        @Test
        void anExactMatchIsClean() {
            CheckOutcome o = rules.weeklyCheck(400, 400, false);
            assertThat(o.difference()).isZero();
            assertThat(o.clean()).isTrue();
            assertThat(o.bonusForFollowingWeek()).isTrue();
        }

        @Test
        void aWeekWithNoScreenTimeAtAllIsClean() {
            CheckOutcome o = rules.weeklyCheck(0, 0, false);
            assertThat(o.clean()).isTrue();
            assertThat(o.bonusForFollowingWeek()).isTrue();
        }
    }

    @Nested
    @DisplayName("time missing from the log comes off the following week")
    class MissingTime {

        @Test
        void oneMinuteOverToleranceIsAlreadyADeduction() {
            CheckOutcome o = rules.weeklyCheck(400, 411, false);
            assertThat(o.difference()).isEqualTo(11);
            assertThat(o.clean()).isFalse();
            assertThat(o.penaltyMinutes())
                    .as("the whole difference is deducted, not only the part above tolerance")
                    .isEqualTo(11);
            assertThat(o.adjustmentMinutes()).isEqualTo(-11);
            assertThat(o.bonusForFollowingWeek()).isFalse();
        }

        @Test
        void thePenaltyIsTheDifference() {
            assertThat(rules.weeklyCheck(400, 460, false).penaltyMinutes()).isEqualTo(60);
        }

        @Test
        void noWeekCanLoseMoreThanTheCap() {
            CheckOutcome o = rules.weeklyCheck(100, 600, false);
            assertThat(o.difference()).isEqualTo(500);
            assertThat(o.penaltyMinutes())
                    .as("a week with nothing left to lose has nothing left to protect")
                    .isEqualTo(120);
            assertThat(o.adjustmentMinutes()).isEqualTo(-120);
        }

        @Test
        void theCapIsExactlyTheCap() {
            assertThat(rules.weeklyCheck(0, 120, false).penaltyMinutes()).isEqualTo(120);
            assertThat(rules.weeklyCheck(0, 121, false).penaltyMinutes()).isEqualTo(120);
            assertThat(rules.weeklyCheck(0, 119, false).penaltyMinutes()).isEqualTo(119);
        }
    }

    @Nested
    @DisplayName("going around the rules on purpose costs an extra hour")
    class Deliberate {

        @Test
        void theExtraHourIsAddedBeforeTheCap() {
            CheckOutcome o = rules.weeklyCheck(400, 430, true);
            assertThat(o.difference()).isEqualTo(30);
            assertThat(o.penaltyMinutes()).isEqualTo(90);
        }

        @Test
        void theExtraHourCannotPushAWeekPastTheCap() {
            CheckOutcome o = rules.weeklyCheck(400, 500, true);
            assertThat(o.difference()).isEqualTo(100);
            assertThat(o.penaltyMinutes())
                    .as("100 plus 60 is 160, capped at 120")
                    .isEqualTo(120);
        }

        @Test
        void aDeliberateWeekIsNeverCleanEvenWhenTheLogMatches() {
            // a parent who marks a week deliberate is saying the child used a
            // second account or changed a clock. Handing that week a bonus hour
            // would make the check worthless.
            CheckOutcome o = rules.weeklyCheck(400, 400, true);
            assertThat(o.difference()).isZero();
            assertThat(o.clean()).isFalse();
            assertThat(o.penaltyMinutes()).isEqualTo(60);
            assertThat(o.bonusForFollowingWeek()).isFalse();
        }

        @Test
        void aDeliberateWeekWithMoreLoggedThanReportedStillCostsTheExtraHour() {
            CheckOutcome o = rules.weeklyCheck(400, 300, true);
            assertThat(o.moreLoggedThanReported()).isTrue();
            assertThat(o.penaltyMinutes()).isEqualTo(60);
            assertThat(o.clean()).isFalse();
        }
    }

    @Nested
    @DisplayName("more booked than the devices saw is not a penalty, it is a question")
    class MoreLoggedThanReported {

        @Test
        void thereIsNoDeductionAndNoBonus() {
            CheckOutcome o = rules.weeklyCheck(400, 370, false);
            assertThat(o.difference()).isEqualTo(-30);
            assertThat(o.penaltyMinutes()).isZero();
            assertThat(o.adjustmentMinutes()).isZero();
            assertThat(o.clean())
                    .as("not a penalty, but not a clean week either")
                    .isFalse();
            assertThat(o.bonusForFollowingWeek()).isFalse();
            assertThat(o.moreLoggedThanReported())
                    .as("flagged so a parent goes and looks at the entries")
                    .isTrue();
        }

        @Test
        void theFlagIsOnlySetBeyondTolerance() {
            assertThat(rules.weeklyCheck(400, 395, false).moreLoggedThanReported()).isFalse();
            assertThat(rules.weeklyCheck(400, 390, false).moreLoggedThanReported()).isFalse();
            assertThat(rules.weeklyCheck(400, 389, false).moreLoggedThanReported()).isTrue();
        }
    }

    @Nested
    @DisplayName("the deduction and the bonus land on the week after the one checked")
    class WhichWeek {

        @Test
        void thePenaltyIsWrittenAgainstTheFollowingWeek() {
            LocalDate checked = LocalDate.of(2026, 9, 28);
            assertThat(rules.followingWeek(checked)).isEqualTo(LocalDate.of(2026, 10, 5));
        }

        @Test
        void theFollowingWeekIsStillSevenDaysLaterAcrossAClockChange() {
            assertThat(rules.followingWeek(LocalDate.of(2026, 3, 23)))
                    .isEqualTo(LocalDate.of(2026, 3, 30));
            assertThat(rules.followingWeek(LocalDate.of(2026, 10, 19)))
                    .isEqualTo(LocalDate.of(2026, 10, 26));
        }
    }

    @Test
    void theOutcomeCarriesTheNumbersItWasGiven() {
        CheckOutcome o = rules.weeklyCheck(412, 455, false);
        assertThat(o.loggedMinutes()).isEqualTo(412);
        assertThat(o.reportedMinutes()).isEqualTo(455);
        assertThat(o.difference()).isEqualTo(43);
    }
}
