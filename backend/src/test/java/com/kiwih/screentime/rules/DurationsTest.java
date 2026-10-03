package com.kiwih.screentime.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DurationsTest {

    @ParameterizedTest(name = "{0} s is {1} min")
    @CsvSource({
            "0, 0",
            "29, 0",
            "30, 1",
            "89, 1",
            "90, 2",
            "300, 5",
            "28830, 481"})
    void loggedSecondsAreComparedWithTheDevicesToTheNearestMinute(int seconds, int minutes) {
        // the devices report whole minutes and round them themselves. Rounding
        // the log down instead would make it run below them every week.
        assertThat(Durations.nearestMinute(seconds)).isEqualTo(minutes);
    }

    @Test
    void minutesBecomeSeconds() {
        assertThat(Durations.seconds(480)).isEqualTo(28_800);
        assertThat(Durations.seconds(-30)).isEqualTo(-1_800);
    }
}
