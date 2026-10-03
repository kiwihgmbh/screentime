package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.Session;

import java.time.Instant;

/** What a session looked like, for the audit trail. */
public record SessionSnapshot(
        Long id, Long userId, Instant startedAt, Instant endedAt, Integer minutes,
        Long deviceId, String type, String source, boolean autoClosed, String note) {

    public static SessionSnapshot of(Session s) {
        return new SessionSnapshot(s.getId(), s.getUserId(), s.getStartedAt(), s.getEndedAt(),
                s.getMinutes(), s.getDeviceId(), s.getType().name(), s.getSource().name(),
                s.isAutoClosed(), s.getNote());
    }
}
