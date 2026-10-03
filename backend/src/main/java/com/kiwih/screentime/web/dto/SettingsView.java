package com.kiwih.screentime.web.dto;

import java.time.LocalDate;
import java.util.List;

/** Everything the parent settings page shows: both value sets, the threshold, and the change log. */
public record SettingsView(
        LocalDate thisWeek,
        LocalDate nextWeek,
        ScopeSettingsView term,
        ScopeSettingsView holiday,
        ScopeSettingsView general,
        List<SettingChangeView> changes) {
}
