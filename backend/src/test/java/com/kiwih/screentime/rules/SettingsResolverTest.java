package com.kiwih.screentime.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which values apply to a week. This is where a week that starts in term time
 * and ends in the holidays gets decided, and where a change made today has to
 * leave last week alone.
 */
class SettingsResolverTest {

    private static final WeekCalendar CALENDAR = new WeekCalendar(ZoneId.of("Europe/Zurich"));
    private final SettingsResolver resolver = new SettingsResolver(CALENDAR);

    /** Long ago, so the defaults are in force for every week a test looks at. */
    private static final LocalDate SINCE_EVER = LocalDate.of(2000, 1, 3);

    // Monday 5 October 2026 to Sunday 11 October 2026, then the week after
    private static final LocalDate MON = LocalDate.of(2026, 10, 5);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate THU = MON.plusDays(3);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate SAT = MON.plusDays(5);
    private static final LocalDate SUN = MON.plusDays(6);
    private static final LocalDate NEXT_MON = MON.plusWeeks(1);

    /**
     * Holiday values that differ from the term values in every ceiling, so a
     * test can tell which set a number came from.
     */
    private static final ScreentimeSettings HOLIDAY = with(ScreentimeSettings.HOLIDAY_DEFAULTS, m -> {
        m.put("weekdayCapMinutes", "110");
        m.put("weekendCapMinutes", "130");
        m.put("bonusWeekendCapMinutes", "170");
    });

    @Nested
    @DisplayName("a week that starts in term time and ends in the holidays")
    class TermIntoHolidays {

        @Test
        void holidaysFromSaturdayLeaveTheWeekOnTermValues() {
            // two holiday days of seven
            WeekSettings week = resolve(MON, holiday("Autumn holidays", SAT, SAT.plusDays(15)));

            assertThat(week.scope()).isEqualTo(SettingsScope.TERM);
            assertThat(week.holidayDayCount()).isEqualTo(2);
            assertThat(week.holidayDays()).containsExactly(SAT, SUN);
            assertThat(week.holidayNames()).containsExactly("Autumn holidays");
            assertThat(week.values().weeklyMinutes()).isEqualTo(480);
            assertThat(week.values().cutoffHour()).isEqualTo(20);
        }

        @Test
        void butEachHolidayDayStillGetsTheHolidayCeiling() {
            WeekSettings week = resolve(MON, holiday("Autumn holidays", SAT, SAT.plusDays(15)));
            ScreentimeRules rules = new ScreentimeRules(week, CALENDAR);

            assertThat(rules.dailyCapMinutes(FRI, WeekState.plain(MON)))
                    .as("Friday is still a school day")
                    .isEqualTo(60);
            assertThat(rules.dailyCapMinutes(SAT, WeekState.plain(MON)))
                    .as("Saturday is a holiday day, so the holiday weekend ceiling")
                    .isEqualTo(130);
            assertThat(rules.dailyCapMinutes(SUN, WeekState.plain(MON))).isEqualTo(130);
        }

        @Test
        void aHolidayWeekdayInATermWeekGetsTheHolidayWeekdayCeiling() {
            // holidays from Friday: three days, still a term week
            WeekSettings week = resolve(MON, holiday("Autumn holidays", FRI, FRI.plusDays(16)));
            ScreentimeRules rules = new ScreentimeRules(week, CALENDAR);

            assertThat(week.scope()).isEqualTo(SettingsScope.TERM);
            assertThat(week.holidayDayCount()).isEqualTo(3);
            assertThat(rules.dailyCapMinutes(THU, WeekState.plain(MON))).isEqualTo(60);
            assertThat(rules.dailyCapMinutes(FRI, WeekState.plain(MON))).isEqualTo(110);
        }

        @Test
        void onlyTheCeilingMovesOnAHolidayDayInATermWeek() {
            // the weekly budget and the cut off cannot be split across two sets
            // without becoming impossible to explain, so they stay term values
            WeekSettings week = resolve(MON, holiday("Autumn holidays", FRI, FRI.plusDays(16)));
            ScreentimeRules rules = new ScreentimeRules(week, CALENDAR);

            assertThat(rules.weeklyBudgetMinutes(WeekState.plain(MON))).isEqualTo(480);
            assertThat(rules.settings().cutoffHour()).isEqualTo(20);
            assertThat(rules.settings().quickDailyMinutes()).isEqualTo(15);
        }

        @Test
        void fourHolidayDaysMakeTheWholeWeekAHolidayWeek() {
            // holidays from Thursday: Thursday to Sunday is four of seven
            WeekSettings week = resolve(MON, holiday("Autumn holidays", THU, THU.plusDays(17)));
            ScreentimeRules rules = new ScreentimeRules(week, CALENDAR);

            assertThat(week.scope()).isEqualTo(SettingsScope.HOLIDAY);
            assertThat(week.holidayDayCount()).isEqualTo(4);
            assertThat(rules.weeklyBudgetMinutes(WeekState.plain(MON))).isEqualTo(720);
            assertThat(rules.settings().cutoffHour()).isEqualTo(21);
            assertThat(rules.dailyCapMinutes(MON, WeekState.plain(MON)))
                    .as("Monday is a school day, but the holiday set applies to the whole week")
                    .isEqualTo(110);
            assertThat(rules.dailyCapMinutes(SAT, WeekState.plain(MON))).isEqualTo(130);
        }

        @Test
        void aBonusOnAHolidayWeekendDayUsesTheHolidayBonusCeiling() {
            WeekSettings week = resolve(MON, holiday("Autumn holidays", SAT, SAT.plusDays(15)));
            ScreentimeRules rules = new ScreentimeRules(week, CALENDAR);
            WeekState bonus = new WeekState(MON, true);

            assertThat(rules.dailyCapMinutes(SAT, bonus)).isEqualTo(170);
            assertThat(rules.weeklyBudgetMinutes(bonus))
                    .as("a term week's bonus is the term bonus")
                    .isEqualTo(480 + 60);
        }
    }

    @Nested
    @DisplayName("a week that ends the holidays")
    class HolidaysIntoTerm {

        @Test
        void holidaysUntilWednesdayLeaveTheWeekOnTermValues() {
            WeekSettings week = resolve(MON, holiday("Autumn holidays", MON.minusDays(16), WED));

            assertThat(week.scope()).isEqualTo(SettingsScope.TERM);
            assertThat(week.holidayDays()).containsExactly(MON, TUE, WED);
            assertThat(new ScreentimeRules(week, CALENDAR).dailyCapMinutes(WED, WeekState.plain(MON)))
                    .isEqualTo(110);
            assertThat(new ScreentimeRules(week, CALENDAR).dailyCapMinutes(THU, WeekState.plain(MON)))
                    .isEqualTo(60);
        }

        @Test
        void holidaysUntilThursdayMakeItAHolidayWeek() {
            WeekSettings week = resolve(MON, holiday("Autumn holidays", MON.minusDays(16), THU));
            assertThat(week.scope()).isEqualTo(SettingsScope.HOLIDAY);
            assertThat(week.holidayDayCount()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("counting holiday days")
    class Counting {

        @Test
        void aWeekWithoutHolidaysIsATermWeek() {
            WeekSettings week = resolve(MON);
            assertThat(week.scope()).isEqualTo(SettingsScope.TERM);
            assertThat(week.holidayDayCount()).isZero();
            assertThat(week.holidayNames()).isEmpty();
        }

        @Test
        void aWholeWeekOfHolidayIsAHolidayWeek() {
            WeekSettings week = resolve(MON, holiday("Summer holidays", MON.minusWeeks(2), SUN.plusWeeks(3)));
            assertThat(week.scope()).isEqualTo(SettingsScope.HOLIDAY);
            assertThat(week.holidayDayCount()).isEqualTo(7);
        }

        @Test
        void twoPeriodsInOneWeekAreCountedTogetherAndBothNamed() {
            // a long weekend at the start and the holidays at the end: 2 + 2
            WeekSettings week = resolve(MON,
                    holiday("Long weekend", MON.minusDays(2), TUE),
                    holiday("Autumn holidays", SAT, SAT.plusDays(15)));

            assertThat(week.holidayDayCount()).isEqualTo(4);
            assertThat(week.scope()).isEqualTo(SettingsScope.HOLIDAY);
            assertThat(week.holidayNames()).containsExactly("Long weekend", "Autumn holidays");
        }

        @Test
        void aPeriodOutsideTheWeekDoesNotCount() {
            WeekSettings week = resolve(MON,
                    holiday("Summer holidays", MON.minusWeeks(10), MON.minusDays(1)),
                    holiday("Christmas", NEXT_MON, NEXT_MON.plusDays(13)));
            assertThat(week.holidayDayCount()).isZero();
            assertThat(week.holidayNames()).isEmpty();
        }

        @Test
        void aSingleDayPeriodCountsOneDay() {
            WeekSettings week = resolve(MON, holiday("Teacher training", WED, WED));
            assertThat(week.holidayDays()).containsExactly(WED);
        }

        @Test
        void theWeekWithTheClockChangeStillHasSevenDays() {
            // Monday 19 to Sunday 25 October 2026; the Sunday is 25 hours long
            LocalDate dstMonday = LocalDate.of(2026, 10, 19);
            WeekSettings week = resolve(dstMonday, holiday("Autumn holidays", dstMonday, dstMonday.plusDays(6)));
            assertThat(week.holidayDayCount()).isEqualTo(7);
            assertThat(week.holidayDays()).last().isEqualTo(LocalDate.of(2026, 10, 25));
        }

        @Test
        void anyDayOfTheWeekResolvesToItsMonday() {
            assertThat(resolver.resolve(FRI, history(), List.of()).weekStart()).isEqualTo(MON);
        }
    }

    @Nested
    @DisplayName("the threshold is a setting")
    class Threshold {

        @Test
        void loweringItToThreeMakesAWeekFromFridayAHolidayWeek() {
            SettingsHistory h = history(global("holidayWeekThresholdDays", "3", SINCE_EVER));
            WeekSettings week = resolver.resolve(MON, h, List.of(holiday("Autumn holidays", FRI, FRI.plusDays(16))));
            assertThat(week.scope()).isEqualTo(SettingsScope.HOLIDAY);
            assertThat(week.holidayWeekThresholdDays()).isEqualTo(3);
        }

        @Test
        void raisingItToSevenKeepsAFourDayWeekOnTermValues() {
            SettingsHistory h = history(global("holidayWeekThresholdDays", "7", SINCE_EVER));
            WeekSettings week = resolver.resolve(MON, h, List.of(holiday("Autumn holidays", THU, THU.plusDays(17))));
            assertThat(week.scope()).isEqualTo(SettingsScope.TERM);
        }

        @Test
        void itIsResolvedOnTheMondayLikeEveryOtherValue() {
            SettingsHistory h = history(global("holidayWeekThresholdDays", "3", NEXT_MON));
            List<Holiday> fromFriday = List.of(holiday("Autumn holidays", FRI, FRI.plusDays(16)));
            assertThat(resolver.resolve(MON, h, fromFriday).scope())
                    .as("the new threshold only starts next week")
                    .isEqualTo(SettingsScope.TERM);
        }
    }

    @Nested
    @DisplayName("settings are versioned by the Monday they start on")
    class Versioning {

        @Test
        void aChangeFromNextMondayLeavesThisWeekAlone() {
            SettingsHistory h = history(term("weeklyMinutes", "420", NEXT_MON));
            assertThat(resolver.resolve(MON, h, List.of()).values().weeklyMinutes()).isEqualTo(480);
            assertThat(resolver.resolve(NEXT_MON, h, List.of()).values().weeklyMinutes()).isEqualTo(420);
        }

        @Test
        void aChangeFromThisMondayAppliesToThisWeekAndNotToLastWeek() {
            SettingsHistory h = history(term("weeklyMinutes", "420", MON));
            assertThat(resolver.resolve(MON, h, List.of()).values().weeklyMinutes()).isEqualTo(420);
            assertThat(resolver.resolve(MON.minusWeeks(1), h, List.of()).values().weeklyMinutes())
                    .as("last week's budget does not change because somebody saved today")
                    .isEqualTo(480);
        }

        @Test
        void aChangeMadeMidWeekIsInForceForTheWholeWeekItStartsOn() {
            // valid_from is always a Monday; a row that starts on this Monday
            // counts from Monday morning, whatever day it was saved
            SettingsHistory h = history(term("weekdayCapMinutes", "70", MON));
            ScreentimeRules rules = new ScreentimeRules(resolver.resolve(MON, h, List.of()), CALENDAR);
            assertThat(rules.dailyCapMinutes(MON, WeekState.plain(MON))).isEqualTo(70);
        }

        @Test
        void twoSavesForTheSameMondayAreDecidedByTheLaterOne() {
            SettingsHistory h = history(
                    term("weeklyMinutes", "420", MON, 100),
                    term("weeklyMinutes", "450", MON, 101));
            assertThat(resolver.resolve(MON, h, List.of()).values().weeklyMinutes()).isEqualTo(450);
        }

        @Test
        void eachKeyIsResolvedOnItsOwn() {
            SettingsHistory h = history(
                    term("weeklyMinutes", "420", MON.minusWeeks(3)),
                    term("cutoffHour", "19", NEXT_MON));
            ScreentimeSettings thisWeek = resolver.resolve(MON, h, List.of()).values();
            assertThat(thisWeek.weeklyMinutes()).isEqualTo(420);
            assertThat(thisWeek.cutoffHour()).isEqualTo(20);
        }

        @Test
        void theHolidayCeilingOnATermWeekHolidayDayIsTheOneInForceOnThatMonday() {
            SettingsHistory h = history(holidayValue("weekendCapMinutes", "140", NEXT_MON));
            WeekSettings week = resolver.resolve(MON, h, List.of(holiday("Autumn holidays", SAT, SAT.plusDays(15))));
            assertThat(new ScreentimeRules(week, CALENDAR).dailyCapMinutes(SAT, WeekState.plain(MON)))
                    .isEqualTo(130);
        }

        @Test
        void termAndHolidayValuesAreSeparate() {
            SettingsHistory h = history(term("cutoffHour", "19", SINCE_EVER.plusWeeks(1)));
            assertThat(resolver.resolve(MON, h, List.of()).holidayValues().cutoffHour()).isEqualTo(21);
        }

        @Test
        void aKeyWithNothingInForceIsAnErrorNotASilentDefault() {
            // the migration seeds every key long before any week the app
            // shows; a gap means the table is broken, and a made up default
            // would hide that
            SettingsHistory h = new SettingsHistory(List.of(
                    new SettingValue(SettingsScope.TERM, "weeklyMinutes", "480", NEXT_MON, 1)));
            assertThatThrownBy(() -> h.values(SettingsScope.TERM, MON))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("weeklyMinutes");
        }

        @Test
        void aValidFromThatIsNotAMondayIsRefused() {
            assertThatThrownBy(() -> new SettingValue(SettingsScope.TERM, "weeklyMinutes", "480", TUE, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(TUE.toString());
        }
    }

    // ------------------------------------------------------------------ helpers

    private WeekSettings resolve(LocalDate weekStart, Holiday... holidays) {
        return resolver.resolve(weekStart, history(), List.of(holidays));
    }

    private static long sequence = 1_000;

    private static Holiday holiday(String name, LocalDate from, LocalDate to) {
        return new Holiday(sequence++, name, from, to);
    }

    /** The term defaults and {@link #HOLIDAY} since ever, plus the given rows. */
    private static SettingsHistory history(SettingValue... extra) {
        List<SettingValue> rows = new ArrayList<>();
        ScreentimeSettings.TERM_DEFAULTS.toMap().forEach((k, v) -> rows.add(term(k, v, SINCE_EVER)));
        HOLIDAY.toMap().forEach((k, v) -> rows.add(holidayValue(k, v, SINCE_EVER)));
        rows.add(global("holidayWeekThresholdDays", "4", SINCE_EVER));
        // the extra rows were built before the defaults above, so they are
        // renumbered to come after them, keeping their order among themselves
        for (SettingValue row : extra) {
            rows.add(new SettingValue(row.scope(), row.key(), row.value(), row.validFrom(), sequence++));
        }
        return new SettingsHistory(rows);
    }

    private static SettingValue term(String key, String value, LocalDate from) {
        return term(key, value, from, sequence++);
    }

    private static SettingValue term(String key, String value, LocalDate from, long seq) {
        return new SettingValue(SettingsScope.TERM, key, value, from, seq);
    }

    private static SettingValue holidayValue(String key, String value, LocalDate from) {
        return new SettingValue(SettingsScope.HOLIDAY, key, value, from, sequence++);
    }

    private static SettingValue global(String key, String value, LocalDate from) {
        return new SettingValue(SettingsScope.GLOBAL, key, value, from, sequence++);
    }

    private static ScreentimeSettings with(ScreentimeSettings base, Consumer<Map<String, String>> change) {
        Map<String, String> m = new HashMap<>(base.toMap());
        change.accept(m);
        return ScreentimeSettings.fromMap(m);
    }

    static {
        assertThat(MON.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }
}
