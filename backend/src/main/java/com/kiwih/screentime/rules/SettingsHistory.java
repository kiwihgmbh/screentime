package com.kiwih.screentime.rules;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every settings row there has ever been, and the answer to "what was in force
 * on this Monday". Nothing is ever overwritten: a change is a new row with a
 * new {@code validFrom}, so last week's values are still here, and a change
 * made today cannot alter a week that is already over.
 */
public final class SettingsHistory {

    private static final Comparator<SettingValue> NEWEST_FIRST =
            Comparator.comparing(SettingValue::validFrom).thenComparingLong(SettingValue::sequence).reversed();

    private final List<SettingValue> rows;

    public SettingsHistory(List<SettingValue> rows) {
        this.rows = rows.stream().sorted(NEWEST_FIRST).toList();
    }

    public List<SettingValue> rows() {
        return rows;
    }

    /**
     * For each key of the scope, the newest row that starts on or before the
     * Monday. Two rows for the same Monday are decided by the later save.
     */
    public Map<String, String> inForce(SettingsScope scope, LocalDate monday) {
        Map<String, String> values = new HashMap<>();
        for (SettingValue row : rows) {
            if (row.scope() == scope && !row.validFrom().isAfter(monday)) {
                values.putIfAbsent(row.key(), row.value());
            }
        }
        return values;
    }

    public ScreentimeSettings values(SettingsScope scope, LocalDate monday) {
        Map<String, String> values = inForce(scope, monday);
        for (String key : ScreentimeSettings.KEYS) {
            if (!values.containsKey(key)) {
                // the migration seeds every key long before any week the app
                // shows, so a gap means the table is broken. Making up a
                // default here would hide that.
                throw new IllegalStateException(
                        "No " + scope + " value for " + key + " is in force on " + monday + ".");
            }
        }
        return ScreentimeSettings.fromMap(values);
    }

    public int holidayWeekThresholdDays(LocalDate monday) {
        String raw = inForce(SettingsScope.GLOBAL, monday).get(SettingsValidator.HOLIDAY_WEEK_THRESHOLD_DAYS);
        if (raw == null) {
            throw new IllegalStateException("No " + SettingsValidator.HOLIDAY_WEEK_THRESHOLD_DAYS
                    + " is in force on " + monday + ".");
        }
        return Integer.parseInt(raw.trim());
    }
}
