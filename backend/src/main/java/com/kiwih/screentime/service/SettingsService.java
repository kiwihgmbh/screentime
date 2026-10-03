package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.Setting;
import com.kiwih.screentime.repo.SettingRepository;
import com.kiwih.screentime.rules.ScreentimeRules;
import com.kiwih.screentime.rules.ScreentimeSettings;
import com.kiwih.screentime.rules.WeekCalendar;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the settings rows and hands out the rules built from them. Settings are
 * rows, never constants, so the rules are constructed per request from whatever
 * the table says at that moment.
 */
@Service
public class SettingsService {

    private final SettingRepository settings;
    private final WeekCalendar calendar;
    private final AuditService audit;
    private final Clock clock;

    public SettingsService(SettingRepository settings, WeekCalendar calendar,
                           AuditService audit, Clock clock) {
        this.settings = settings;
        this.calendar = calendar;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScreentimeSettings current() {
        Map<String, String> values = new HashMap<>();
        settings.findAll().forEach(s -> values.put(s.getKey(), s.getValue()));
        return ScreentimeSettings.fromMap(values);
    }

    @Transactional(readOnly = true)
    public ScreentimeRules rules() {
        return new ScreentimeRules(current(), calendar);
    }

    /**
     * Saves a changed set of values. The whole set is validated together before
     * anything is written, so a half applied change can never leave the daily
     * ceilings below the weekly budget.
     */
    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public ScreentimeSettings update(Map<String, String> changes, Long byUserId) {
        Map<String, String> merged = new LinkedHashMap<>(current().toMap());
        changes.forEach((key, value) -> {
            if (!ScreentimeSettings.KEYS.contains(key)) {
                throw new IllegalArgumentException("There is no setting called " + key + ".");
            }
            merged.put(key, value);
        });

        // throws before anything is persisted when the set does not hold together
        ScreentimeSettings validated = ScreentimeSettings.fromMap(merged);

        Map<String, String> after = validated.toMap();
        for (Setting row : settings.findAll()) {
            String newValue = after.get(row.getKey());
            if (newValue != null && !newValue.equals(row.getValue())) {
                String oldValue = row.getValue();
                row.update(newValue, clock.instant(), byUserId);
                audit.updated(AuditService.SETTING, null,
                        Map.of("key", row.getKey(), "value", oldValue),
                        Map.of("key", row.getKey(), "value", newValue),
                        byUserId);
            }
        }
        return validated;
    }
}
