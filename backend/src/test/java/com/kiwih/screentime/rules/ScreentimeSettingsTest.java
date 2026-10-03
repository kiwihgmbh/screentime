package com.kiwih.screentime.rules;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreentimeSettingsTest {

    @Test
    void theDefaultsAreTheOnesInTheSpecification() {
        ScreentimeSettings s = ScreentimeSettings.DEFAULTS;
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
    void theDailyCeilingsMustAddUpToMoreThanTheWeek() {
        // 5 x 60 + 2 x 120 = 540 against a weekly 480: the week binds, and so
        // does every day. This is the whole point of the arrangement.
        assertThat(ScreentimeSettings.DEFAULTS.dailyCeilingSumMinutes()).isEqualTo(540);
    }

    @Test
    void ceilingsThatDoNotReachTheWeeklyBudgetAreRejectedWithBothNumbers() {
        assertThatThrownBy(() -> withValues(m -> {
            m.put("weekdayCapMinutes", "60");
            m.put("weekendCapMinutes", "30");
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("360")
                .hasMessageContaining("480");
    }

    @Test
    void ceilingsThatExactlyEqualTheWeeklyBudgetAreRejected() {
        // equal means the daily ceilings alone decide and the weekly budget
        // never does any work, which is the case the rule exists to prevent
        assertThatThrownBy(() -> withValues(m -> {
            m.put("weeklyMinutes", "540");
            m.put("weekdayCapMinutes", "60");
            m.put("weekendCapMinutes", "120");
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("540");
    }

    @Test
    void ceilingsOneMinuteAboveTheWeeklyBudgetAreAccepted() {
        ScreentimeSettings s = withValues(m -> {
            m.put("weeklyMinutes", "539");
            m.put("weekdayCapMinutes", "60");
            m.put("weekendCapMinutes", "120");
        });
        assertThat(s.weeklyMinutes()).isEqualTo(539);
    }

    @Test
    void aCutOffHourOutsideTheClockIsRejected() {
        assertThatThrownBy(() -> withValues(m -> m.put("cutoffHour", "24")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoffHour");
        assertThatThrownBy(() -> withValues(m -> m.put("cutoffHour", "-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoffHour");
    }

    @Test
    void negativeValuesAreRejectedAndTheMessageNamesTheKey() {
        assertThatThrownBy(() -> withValues(m -> m.put("quickDailyMinutes", "-5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quickDailyMinutes");
    }

    @Test
    void aMissingKeyIsRejectedAndTheMessageNamesIt() {
        Map<String, String> m = ScreentimeSettings.DEFAULTS.toMap();
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
        assertThat(ScreentimeSettings.fromMap(ScreentimeSettings.DEFAULTS.toMap()))
                .isEqualTo(ScreentimeSettings.DEFAULTS);
        assertThat(ScreentimeSettings.DEFAULTS.toMap())
                .as("exactly the keys the settings table holds, no more and no fewer")
                .containsOnlyKeys(ScreentimeSettings.KEYS.toArray(String[]::new));
    }

    private static ScreentimeSettings withValues(java.util.function.Consumer<Map<String, String>> change) {
        Map<String, String> m = new HashMap<>(ScreentimeSettings.DEFAULTS.toMap());
        change.accept(m);
        return ScreentimeSettings.fromMap(m);
    }
}
