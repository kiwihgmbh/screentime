package com.kiwih.screentime.rules;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreentimeSettingsTest {

    @Test
    void theTermDefaultsAreTheOnesInTheSpecification() {
        ScreentimeSettings s = ScreentimeSettings.TERM_DEFAULTS;
        assertThat(s.weeklyMinutes()).isEqualTo(480);
        assertThat(s.weekdayCapMinutes()).isEqualTo(60);
        assertThat(s.weekendCapMinutes()).isEqualTo(120);
        assertThat(s.quickDailyMinutes()).isEqualTo(15);
        assertThat(s.cutoffHour()).isEqualTo(20);
        assertThat(s.bonusMinutes()).isEqualTo(60);
        assertThat(s.bonusWeekendCapMinutes()).isEqualTo(150);
        assertThat(s.maxPenaltyMinutes()).isEqualTo(120);
        assertThat(s.toleranceMinutes()).isEqualTo(10);
        assertThat(s.manualMaxMinutes()).isEqualTo(240);
        assertThat(s.deliberatePenaltyMinutes()).isEqualTo(60);
    }

    @Test
    void theHolidayDefaultsAreTheOnesInTheSpecification() {
        ScreentimeSettings s = ScreentimeSettings.HOLIDAY_DEFAULTS;
        assertThat(s.weeklyMinutes()).isEqualTo(720);
        assertThat(s.weekdayCapMinutes()).isEqualTo(120);
        assertThat(s.weekendCapMinutes()).isEqualTo(120);
        assertThat(s.cutoffHour()).isEqualTo(21);
        // everything else is the same as in term time
        assertThat(s.quickDailyMinutes()).isEqualTo(15);
        assertThat(s.bonusMinutes()).isEqualTo(60);
        assertThat(s.bonusWeekendCapMinutes()).isEqualTo(150);
        assertThat(s.maxPenaltyMinutes()).isEqualTo(120);
        assertThat(s.toleranceMinutes()).isEqualTo(10);
        assertThat(s.manualMaxMinutes()).isEqualTo(240);
        assertThat(s.deliberatePenaltyMinutes()).isEqualTo(60);
    }

    @Test
    void theMinuteValuesAreEveryKeyExceptTheCutOffHour() {
        assertThat(ScreentimeSettings.TERM_DEFAULTS.minuteValues().keySet())
                .containsExactlyElementsOf(ScreentimeSettings.KEYS.stream()
                        .filter(k -> !k.equals("cutoffHour")).toList());
    }

    @Test
    void theDailyCeilingsMustAddUpToMoreThanTheWeek() {
        // 5 x 60 + 2 x 120 = 540 against a weekly 480: the week binds, and so
        // does every day. This is the whole point of the arrangement.
        assertThat(ScreentimeSettings.TERM_DEFAULTS.dailyCeilingSumMinutes()).isEqualTo(540);
    }

    @Test
    void aMissingKeyIsRejectedAndTheMessageNamesIt() {
        Map<String, String> m = ScreentimeSettings.TERM_DEFAULTS.toMap();
        m.remove("toleranceMinutes");
        assertThatThrownBy(() -> ScreentimeSettings.fromMap(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toleranceMinutes");
    }

    @Test
    void aValueThatIsNotANumberIsRejectedAndTheMessageNamesTheKey() {
        assertThatThrownBy(() -> withValues(m -> m.put("weeklyMinutes", "lots")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weeklyMinutes");
    }

    @Test
    void aMapSurvivesTheRoundTrip() {
        assertThat(ScreentimeSettings.fromMap(ScreentimeSettings.TERM_DEFAULTS.toMap()))
                .isEqualTo(ScreentimeSettings.TERM_DEFAULTS);
        assertThat(ScreentimeSettings.TERM_DEFAULTS.toMap())
                .as("exactly the keys the settings table holds, no more and no fewer")
                .containsOnlyKeys(ScreentimeSettings.KEYS.toArray(String[]::new));
    }

    private static ScreentimeSettings withValues(java.util.function.Consumer<Map<String, String>> change) {
        Map<String, String> m = new HashMap<>(ScreentimeSettings.TERM_DEFAULTS.toMap());
        change.accept(m);
        return ScreentimeSettings.fromMap(m);
    }
}
