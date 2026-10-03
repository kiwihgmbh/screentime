package com.kiwih.screentime.web.dto;

import jakarta.validation.constraints.NotNull;

public record HolidayRequest(@NotNull Boolean holiday) {
}
