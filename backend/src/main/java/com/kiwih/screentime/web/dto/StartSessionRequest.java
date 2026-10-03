package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.SessionType;
import jakarta.validation.constraints.NotNull;

/**
 * No time and no date: the server decides when a session starts. There is
 * nothing in this request a client could use to move time around.
 */
public record StartSessionRequest(@NotNull Long deviceId, @NotNull SessionType type) {
}
