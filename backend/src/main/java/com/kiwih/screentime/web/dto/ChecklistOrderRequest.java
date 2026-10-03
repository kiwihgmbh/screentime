package com.kiwih.screentime.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Every item, in the order the child sees them. */
public record ChecklistOrderRequest(@NotNull List<Long> ids) {
}
