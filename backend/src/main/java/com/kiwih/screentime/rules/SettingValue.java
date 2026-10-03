package com.kiwih.screentime.rules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One row of the settings history: a value for one key in one scope, in force
 * from a Monday on. Rows are never changed; a new value is a new row.
 *
 * {@code sequence} orders two rows for the same key and Monday, which happens
 * when a parent saves twice in one week. The later one wins.
 */
public record SettingValue(SettingsScope scope, String key, String value, LocalDate validFrom, long sequence) {

    public SettingValue {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(validFrom, "validFrom");
        // a week is decided by the values in force on its Monday, so a value
        // that starts on a Wednesday would mean something nobody can explain
        if (validFrom.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException(
                    "Settings start on a Monday, but " + key + " was given " + validFrom
                            + ", a " + validFrom.getDayOfWeek() + ".");
        }
    }
}
