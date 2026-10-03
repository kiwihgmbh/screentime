package com.kiwih.screentime.rules;

import com.kiwih.screentime.rules.SettingsValidator.Rule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsValidatorTest {

    private final SettingsValidator validator = new SettingsValidator();

    @Test
    void theTermDefaultsHoldTogether() {
        assertThat(validator.validate(SettingsScope.TERM, ScreentimeSettings.TERM_DEFAULTS)).isEmpty();
    }

    @Test
    void theHolidayDefaultsHoldTogether() {
        // 5 x 120 + 2 x 120 = 840 against 720, and 720 + 60 = 780 against
        // 5 x 120 + 2 x 150 = 900
        assertThat(validator.validate(SettingsScope.HOLIDAY, ScreentimeSettings.HOLIDAY_DEFAULTS)).isEmpty();
    }

    @Nested
    class TheDailyCeilingsMustExceedTheWeeklyBudget {

        @Test
        void ceilingsBelowTheBudgetAreRejectedWithBothNumbers() {
            // 5 x 60 + 2 x 30 = 360 against 480
            List<SettingsProblem> problems = term(m -> {
                m.put("weekendCapMinutes", "30");
                m.put("bonusWeekendCapMinutes", "200");
            });

            SettingsProblem p = only(problems, Rule.DAILY_CEILINGS_EXCEED_WEEK);
            assertThat(p.message()).contains("360").contains("480");
            assertThat(p.keys()).containsExactlyInAnyOrder(
                    "weeklyMinutes", "weekdayCapMinutes", "weekendCapMinutes");
        }

        @Test
        void ceilingsExactlyEqualToTheBudgetAreRejected() {
            // equal means the weekly budget never does any work, which is the
            // case the rule exists to prevent
            List<SettingsProblem> problems = term(m -> {
                m.put("weeklyMinutes", "540");
                m.put("bonusMinutes", "0");
                m.put("bonusWeekendCapMinutes", "150");
            });
            assertThat(only(problems, Rule.DAILY_CEILINGS_EXCEED_WEEK).message()).contains("540");
        }

        @Test
        void ceilingsOneMinuteAboveTheBudgetAreAccepted() {
            assertThat(term(m -> m.put("weeklyMinutes", "539"))).noneMatch(is(Rule.DAILY_CEILINGS_EXCEED_WEEK));
        }
    }

    @Nested
    class TheBonusWeekMustStillBind {

        @Test
        void aBonusWeekWhoseBudgetReachesTheCeilingsIsRejectedWithBothNumbers() {
            // 500 + 60 = 560 against 5 x 60 + 2 x 120 = 540. The ordinary week
            // is fine (540 > 500), only the bonus week is broken.
            List<SettingsProblem> problems = term(m -> {
                m.put("weeklyMinutes", "500");
                m.put("bonusWeekendCapMinutes", "120");
            });

            assertThat(problems).hasSize(1);
            SettingsProblem p = only(problems, Rule.BONUS_CEILINGS_EXCEED_BONUS_WEEK);
            assertThat(p.message()).contains("560").contains("540");
            assertThat(p.keys()).containsExactlyInAnyOrder(
                    "weeklyMinutes", "bonusMinutes", "weekdayCapMinutes", "bonusWeekendCapMinutes");
        }

        @Test
        void equalIsRejected() {
            // 480 + 60 = 540 against 5 x 60 + 2 x 120 = 540
            List<SettingsProblem> problems = term(m -> m.put("bonusWeekendCapMinutes", "120"));
            assertThat(problems).anyMatch(is(Rule.BONUS_CEILINGS_EXCEED_BONUS_WEEK));
        }

        @Test
        void oneMinuteBelowIsAccepted() {
            // 479 + 60 = 539 against 540
            List<SettingsProblem> problems = term(m -> {
                m.put("weeklyMinutes", "479");
                m.put("bonusWeekendCapMinutes", "120");
            });
            assertThat(problems).isEmpty();
        }
    }

    @Nested
    class TheBonusWeekendCeilingCannotBeLowerThanTheOrdinaryOne {

        @Test
        void aLowerBonusCeilingIsRejectedWithBothNumbers() {
            List<SettingsProblem> problems = term(m -> {
                m.put("weekdayCapMinutes", "100");
                m.put("bonusWeekendCapMinutes", "110");
            });

            assertThat(problems).hasSize(1);
            SettingsProblem p = only(problems, Rule.BONUS_WEEKEND_CAP_AT_LEAST_WEEKEND_CAP);
            assertThat(p.message()).contains("110").contains("120");
        }

        @Test
        void anEqualBonusCeilingIsAccepted() {
            List<SettingsProblem> problems = term(m -> {
                m.put("weekdayCapMinutes", "100");
                m.put("bonusWeekendCapMinutes", "120");
            });
            assertThat(problems).isEmpty();
        }
    }

    @Nested
    class TheCutOffHour {

        @ParameterizedTest
        @ValueSource(ints = {12, 20, 23})
        void isAcceptedFromNoonToEleven(int hour) {
            assertThat(term(m -> m.put("cutoffHour", String.valueOf(hour)))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 11, 24})
        void isRejectedOutsideThatAndTheMessageNamesTheValue(int hour) {
            SettingsProblem p = only(term(m -> m.put("cutoffHour", String.valueOf(hour))), Rule.CUTOFF_HOUR_RANGE);
            assertThat(p.message()).contains(String.valueOf(hour)).contains("12").contains("23");
            assertThat(p.keys()).containsExactly("cutoffHour");
        }
    }

    @Nested
    class MinuteValues {

        @ParameterizedTest
        @ValueSource(strings = {
                "weeklyMinutes", "weekdayCapMinutes", "weekendCapMinutes", "quickDailyMinutes",
                "bonusMinutes", "bonusWeekendCapMinutes", "maxPenaltyMinutes", "toleranceMinutes",
                "manualMaxMinutes", "deliberatePenaltyMinutes"})
        void cannotBeNegative(String key) {
            SettingsProblem p = only(term(m -> m.put(key, "-1")), Rule.NOT_NEGATIVE, key);
            assertThat(p.message()).contains(key).contains("-1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"quickDailyMinutes", "toleranceMinutes", "manualMaxMinutes", "bonusMinutes"})
        void zeroIsAllowed(String key) {
            assertThat(term(m -> m.put(key, "0"))).noneMatch(is(Rule.NOT_NEGATIVE));
        }

        @Test
        void theWeeklyBudgetCannotExceedTheMinutesInAWeek() {
            SettingsProblem p = only(term(m -> m.put("weeklyMinutes", "10081")), Rule.WEEKLY_MAXIMUM);
            assertThat(p.message()).contains("10081").contains("10080");
        }

        @Test
        void aWeekOfExactly10080MinutesIsNotTooLong() {
            assertThat(term(m -> m.put("weeklyMinutes", "10080"))).noneMatch(is(Rule.WEEKLY_MAXIMUM));
        }
    }

    @Test
    void everyBrokenRuleIsReportedAtOnceSoTheFormCanMarkEachField() {
        List<SettingsProblem> problems = term(m -> {
            m.put("cutoffHour", "8");
            m.put("toleranceMinutes", "-3");
            m.put("weekendCapMinutes", "30");
        });
        assertThat(problems).extracting(SettingsProblem::rule).contains(
                Rule.CUTOFF_HOUR_RANGE, Rule.NOT_NEGATIVE, Rule.DAILY_CEILINGS_EXCEED_WEEK);
    }

    @Test
    void theMessageSaysWhichValueSetIsBroken() {
        ScreentimeSettings broken = with(ScreentimeSettings.HOLIDAY_DEFAULTS, m -> m.put("cutoffHour", "11"));
        assertThat(validator.validate(SettingsScope.HOLIDAY, broken))
                .singleElement()
                .satisfies(p -> assertThat(p.message()).startsWith("Holiday values:"));
        assertThat(validator.validate(SettingsScope.TERM, broken))
                .singleElement()
                .satisfies(p -> assertThat(p.message()).startsWith("Term values:"));
    }

    @Test
    void requireValidThrowsWithEveryMessageAndKeepsTheProblems() {
        ScreentimeSettings broken = with(ScreentimeSettings.TERM_DEFAULTS, m -> {
            m.put("cutoffHour", "8");
            m.put("weekendCapMinutes", "30");
        });
        assertThatThrownBy(() -> validator.requireValid(SettingsScope.TERM, broken))
                .isInstanceOfSatisfying(InvalidSettingsException.class, e -> {
                    assertThat(e.kind()).isEqualTo(RuleViolation.Kind.INVALID);
                    assertThat(e.problems()).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(e.getMessage()).contains("8").contains("360").contains("480");
                });
    }

    @Test
    void requireValidAcceptsAValidSet() {
        validator.requireValid(SettingsScope.TERM, ScreentimeSettings.TERM_DEFAULTS);
    }

    @Nested
    class TheHolidayWeekThreshold {

        @ParameterizedTest
        @ValueSource(ints = {1, 4, 7})
        void isADayCountFromOneToSeven(int days) {
            assertThat(validator.validateHolidayWeekThreshold(days)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 8})
        void isRejectedOutsideThat(int days) {
            assertThat(validator.validateHolidayWeekThreshold(days))
                    .singleElement()
                    .satisfies(p -> {
                        assertThat(p.rule()).isEqualTo(Rule.HOLIDAY_WEEK_THRESHOLD_RANGE);
                        assertThat(p.keys()).containsExactly("holidayWeekThresholdDays");
                        assertThat(p.message()).contains(String.valueOf(days));
                    });
        }
    }

    private List<SettingsProblem> term(Consumer<Map<String, String>> change) {
        return validator.validate(SettingsScope.TERM, with(ScreentimeSettings.TERM_DEFAULTS, change));
    }

    private static ScreentimeSettings with(ScreentimeSettings base, Consumer<Map<String, String>> change) {
        Map<String, String> m = new HashMap<>(base.toMap());
        change.accept(m);
        return ScreentimeSettings.fromMap(m);
    }

    private static java.util.function.Predicate<SettingsProblem> is(Rule rule) {
        return p -> p.rule() == rule;
    }

    private static SettingsProblem only(List<SettingsProblem> problems, Rule rule) {
        assertThat(problems).filteredOn(is(rule)).as("problems for %s in %s", rule, problems).hasSize(1);
        return problems.stream().filter(is(rule)).findFirst().orElseThrow();
    }

    private static SettingsProblem only(List<SettingsProblem> problems, Rule rule, String key) {
        List<SettingsProblem> matching = problems.stream()
                .filter(is(rule)).filter(p -> p.keys().contains(key)).toList();
        assertThat(matching).as("problems for %s on %s in %s", rule, key, problems).hasSize(1);
        return matching.get(0);
    }
}
