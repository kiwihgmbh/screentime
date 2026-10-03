package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.SessionType;

import java.time.Instant;

/** The session running right now, and what it is counting down against. */
public record OpenSessionView(
        Long id,
        SessionType type,
        Long deviceId,
        String deviceName,
        Instant startedAt,
        int elapsedMinutes,
        /** the budget this session is spending: the quick budget, or available now */
        int countdownAgainstMinutes) {
}
