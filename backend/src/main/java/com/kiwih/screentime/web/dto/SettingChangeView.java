package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.rules.SettingsScope;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One line of the change log: who changed what, when, and from which value to
 * which. {@code kind} is SETTING or HOLIDAY_PERIOD. For a setting, {@code key}
 * is the setting and {@code validFrom} the Monday it applies from; for a
 * holiday period, {@code key} is its name and {@code from}/{@code to} its dates
 * before and after, absent when it was created or deleted.
 */
public record SettingChangeView(
        Instant at,
        String by,
        String kind,
        String action,
        SettingsScope scope,
        String key,
        String from,
        String to,
        LocalDate validFrom) {
}
