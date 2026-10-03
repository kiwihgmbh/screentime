package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.rules.SettingsScope;

import java.util.List;
import java.util.Map;

/**
 * One value set: what is in force this week, what will be next week, and
 * every row behind it, oldest first.
 */
public record ScopeSettingsView(
        SettingsScope scope,
        Map<String, String> thisWeek,
        Map<String, String> nextWeek,
        List<SettingRowView> history) {
}
