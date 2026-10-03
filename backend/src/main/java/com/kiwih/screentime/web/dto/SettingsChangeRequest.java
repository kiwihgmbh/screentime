package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.rules.SettingsScope;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

/**
 * Changed values for one scope. {@code validFrom} is this Monday or next
 * Monday; left out, it is this Monday. Any other date is refused.
 */
public record SettingsChangeRequest(
        @NotNull SettingsScope scope,
        @NotEmpty Map<String, String> values,
        LocalDate validFrom) {
}
