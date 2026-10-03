package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.SessionSource;
import com.kiwih.screentime.domain.SessionType;

import java.time.Instant;
import java.time.LocalDate;

/** One entry, with everything needed to explain it and nothing more. */
public record SessionView(
        Long id,
        /** the local day the entry belongs to, derived on the server */
        LocalDate day,
        SessionType type,
        SessionSource source,
        Long deviceId,
        String deviceName,
        Instant startedAt,
        Instant endedAt,
        int minutes,
        boolean running,
        boolean autoClosed,
        String note,
        /** who entered it, so a parent's correction is visible as one */
        String createdBy) {
}
