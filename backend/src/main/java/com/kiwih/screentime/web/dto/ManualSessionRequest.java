package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.SessionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The one request that carries a date, and only because a parent correcting a
 * past day has to be able to say which day. For a child the date is checked
 * against the server's today and refused if it differs; it is never trusted to
 * decide anything.
 */
public record ManualSessionRequest(
        @NotNull @Positive Integer minutes,
        @NotNull Long deviceId,
        @NotNull SessionType type,
        LocalDate date,
        @Size(max = 512) String note,
        /** the checklist items ticked for this entry, as for a start */
        java.util.List<Long> checklistItemIds) {
}
