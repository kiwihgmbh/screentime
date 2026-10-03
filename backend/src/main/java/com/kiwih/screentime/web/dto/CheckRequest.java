package com.kiwih.screentime.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;

public record CheckRequest(
        @NotNull LocalDate weekStart,
        @NotEmpty @Valid List<ReportedDeviceInput> reported,
        boolean deliberate) {

    public record ReportedDeviceInput(@NotNull Long deviceId, @NotNull @PositiveOrZero Integer minutes) {
    }
}
