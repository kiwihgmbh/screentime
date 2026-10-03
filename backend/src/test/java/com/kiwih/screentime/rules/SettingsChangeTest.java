package com.kiwih.screentime.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a parent's save turns into: the rows to write, or a refusal. The date
 * a change starts on is one of two Mondays, decided by the server, and a
 * change is checked against every later week it reaches, not just the first.
 */
class SettingsChangeTest {

    private static final LocalDate SINCE_EVER = LocalDate.of(2000, 1, 3);
    private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 9, 28);
    private static final LocalDate NEXT_MONDAY = THIS_MONDAY.plusWeeks(1);

    private final SettingsChange change = new SettingsChange(new SettingsValidator());

    @Nested
    @DisplayName("when a change starts")
    class Start {

        @Test
        void withoutADateItStartsThisMonday() {
            List<SettingValue> rows = change.plan(history(), SettingsScope.TERM,
                    Map.of("weeklyMinutes", "420"), null, THIS_MONDAY);
            assertThat(rows).singleElement().satisfies(r -> {
                assertThat(r.validFrom()).isEqualTo(THIS_MONDAY);
                assertThat(r.value()).isEqualTo("420");
                assertThat(r.scope()).isEqualTo(SettingsScope.TERM);
            });
        }

        @Test
        void itCanStartNextMonday() {
            List<SettingValue> rows = change.plan(history(), SettingsScope.TERM,
                    Map.of("weeklyMinutes", "420"), NEXT_MONDAY, THIS_MONDAY);
            assertThat(rows).singleElement().extracting(SettingValue::validFrom).isEqualTo(NEXT_MONDAY);
        }

        @Test
        void thePastIsRefusedSoLastWeekCannotChange() {
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.TERM,
                    Map.of("weeklyMinutes", "420"), THIS_MONDAY.minusWeeks(1), THIS_MONDAY))
                    .isInstanceOf(RuleViolation.class)
                    .hasMessageContaining(THIS_MONDAY.toString())
                    .hasMessageContaining(NEXT_MONDAY.toString());
        }

        @Test
        void aWeekFurtherAheadIsRefused() {
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.TERM,
                    Map.of("weeklyMinutes", "420"), NEXT_MONDAY.plusWeeks(1), THIS_MONDAY))
                    .isInstanceOf(RuleViolation.class);
        }

        @Test
        void aDayThatIsNotAMondayIsRefused() {
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.TERM,
                    Map.of("weeklyMinutes", "420"), THIS_MONDAY.plusDays(2), THIS_MONDAY))
                    .isInstanceOf(RuleViolation.class)
                    .extracting("kind").isEqualTo(RuleViolation.Kind.INVALID);
        }
    }

    @Nested
    @DisplayName("what gets written")
    class Rows {

        @Test
        void onlyChangedKeysGetARow() {
            List<SettingValue> rows = change.plan(history(), SettingsScope.TERM,
                    Map.of("weeklyMinutes", "420", "cutoffHour", "20"), null, THIS_MONDAY);
            assertThat(rows).extracting(SettingValue::key).containsExactly("weeklyMinutes");
        }

        @Test
        void savingTheSameValuesWritesNothing() {
            assertThat(change.plan(history(), SettingsScope.TERM,
                    ScreentimeSettings.TERM_DEFAULTS.toMap(), null, THIS_MONDAY)).isEmpty();
        }

        @Test
        void valuesAreStoredTrimmed() {
            assertThat(change.plan(history(), SettingsScope.TERM, Map.of("weeklyMinutes", " 420 "),
                    null, THIS_MONDAY)).singleElement().extracting(SettingValue::value).isEqualTo("420");
        }

        @Test
        void holidayValuesAreTheirOwnScope() {
            List<SettingValue> rows = change.plan(history(), SettingsScope.HOLIDAY,
                    Map.of("cutoffHour", "22"), null, THIS_MONDAY);
            assertThat(rows).singleElement().extracting(SettingValue::scope).isEqualTo(SettingsScope.HOLIDAY);
        }

        @Test
        void theThresholdIsTheOnlyGeneralSetting() {
            assertThat(change.plan(history(), SettingsScope.GLOBAL,
                    Map.of("holidayWeekThresholdDays", "3"), null, THIS_MONDAY)).hasSize(1);
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.GLOBAL,
                    Map.of("weeklyMinutes", "420"), null, THIS_MONDAY))
                    .hasMessageContaining("weeklyMinutes");
        }

        @Test
        void anUnknownKeyIsRefusedByName() {
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.TERM,
                    Map.of("unlimitedScreens", "true"), null, THIS_MONDAY))
                    .isInstanceOf(RuleViolation.class)
                    .hasMessageContaining("unlimitedScreens");
        }

        @Test
        void theThresholdIsAWholeNumber() {
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.GLOBAL,
                    Map.of("holidayWeekThresholdDays", "four"), null, THIS_MONDAY))
                    .isInstanceOf(RuleViolation.class)
                    .hasMessageContaining("four");
        }
    }

    @Nested
    @DisplayName("what is validated")
    class Validation {

        @Test
        void theChangeIsMergedWithTheValuesInForceAndValidatedAsAWhole() {
            // 5 x 60 + 2 x 30 = 360 against 480
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.TERM,
                    Map.of("weekendCapMinutes", "30", "bonusWeekendCapMinutes", "200"), null, THIS_MONDAY))
                    .isInstanceOfSatisfying(InvalidSettingsException.class, e ->
                            assertThat(e.getMessage()).contains("360").contains("480"));
        }

        @Test
        void holidayValuesAreValidatedWithTheHolidayRules() {
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.HOLIDAY,
                    Map.of("cutoffHour", "11"), null, THIS_MONDAY))
                    .hasMessageStartingWith("Holiday values:");
        }

        @Test
        void aThresholdOutsideTheWeekIsRefused() {
            assertThatThrownBy(() -> change.plan(history(), SettingsScope.GLOBAL,
                    Map.of("holidayWeekThresholdDays", "8"), null, THIS_MONDAY))
                    .isInstanceOf(InvalidSettingsException.class);
        }

        @Test
        void aChangeFromThisWeekIsCheckedAgainstAChangeAlreadyPlannedForNextWeek() {
            // next week's budget is already set to 520. Lowering the weekday
            // ceiling to 55 from this week is fine for this week (515 > 480)
            // but breaks next week (515 is not more than 520).
            SettingsHistory h = history(new SettingValue(SettingsScope.TERM, "weeklyMinutes", "520", NEXT_MONDAY, 500));

            assertThatThrownBy(() -> change.plan(h, SettingsScope.TERM,
                    Map.of("weekdayCapMinutes", "55"), THIS_MONDAY, THIS_MONDAY))
                    .isInstanceOfSatisfying(InvalidSettingsException.class, e -> {
                        // the bonus week of next week breaks as well: 580 against 575
                        assertThat(e.problems()).extracting(SettingsProblem::rule).containsExactlyInAnyOrder(
                                SettingsValidator.Rule.DAILY_CEILINGS_EXCEED_WEEK,
                                SettingsValidator.Rule.BONUS_CEILINGS_EXCEED_BONUS_WEEK);
                        assertThat(e.problems()).allSatisfy(p ->
                                assertThat(p.message()).startsWith("From the week of " + NEXT_MONDAY));
                        assertThat(e.getMessage()).contains("515").contains("520");
                    });
        }

        @Test
        void aChangeThatHoldsInEveryWeekItReachesIsAccepted() {
            SettingsHistory h = history(new SettingValue(SettingsScope.TERM, "weeklyMinutes", "520", NEXT_MONDAY, 500));
            assertThat(change.plan(h, SettingsScope.TERM, Map.of("weekdayCapMinutes", "57"), THIS_MONDAY, THIS_MONDAY))
                    .hasSize(1);
        }

        @Test
        void aChangeForNextWeekIsComparedWithNextWeeksValues() {
            // next week's budget is 520, so a weekday ceiling of 55 from next
            // week is refused although it would be fine against this week's 480
            SettingsHistory h = history(new SettingValue(SettingsScope.TERM, "weeklyMinutes", "520", NEXT_MONDAY, 500));
            assertThatThrownBy(() -> change.plan(h, SettingsScope.TERM,
                    Map.of("weekdayCapMinutes", "55"), NEXT_MONDAY, THIS_MONDAY))
                    .isInstanceOf(InvalidSettingsException.class);
        }

        @Test
        void aRowLaterTheSameMondayWinsOverThePlannedOne() {
            // a parent who saves twice in a week: the second save is compared
            // with the first one, not with the values from before
            SettingsHistory h = history(new SettingValue(SettingsScope.TERM, "weeklyMinutes", "420", THIS_MONDAY, 500));
            assertThat(change.plan(h, SettingsScope.TERM, Map.of("weeklyMinutes", "420"), null, THIS_MONDAY))
                    .as("420 is already in force this week")
                    .isEmpty();
        }
    }

    private static SettingsHistory history(SettingValue... extra) {
        List<SettingValue> rows = new ArrayList<>();
        long seq = 1;
        for (var e : ScreentimeSettings.TERM_DEFAULTS.toMap().entrySet()) {
            rows.add(new SettingValue(SettingsScope.TERM, e.getKey(), e.getValue(), SINCE_EVER, seq++));
        }
        for (var e : ScreentimeSettings.HOLIDAY_DEFAULTS.toMap().entrySet()) {
            rows.add(new SettingValue(SettingsScope.HOLIDAY, e.getKey(), e.getValue(), SINCE_EVER, seq++));
        }
        rows.add(new SettingValue(SettingsScope.GLOBAL, "holidayWeekThresholdDays", "4", SINCE_EVER, seq));
        rows.addAll(List.of(extra));
        return new SettingsHistory(rows);
    }
}
