package com.kiwih.screentime.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AdjustmentRequest(
        @NotNull LocalDate weekStart,
        @NotNull Integer minutes,
        @NotBlank @Size(max = 256) String reason) {
}
