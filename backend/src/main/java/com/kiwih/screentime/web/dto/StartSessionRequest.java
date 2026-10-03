package com.kiwih.screentime.web.dto;

import java.util.List;
import com.kiwih.screentime.domain.SessionType;
import jakarta.validation.constraints.NotNull;

/**
 * No time and no date: the server decides when a session starts. There is
 * nothing in this request a client could use to move time around.
 *
 * {@code checklistItemIds} are the items the child ticked for this start. The
 * server checks them against its own list for its own today.
 */
public record StartSessionRequest(@NotNull Long deviceId, @NotNull SessionType type,
                                  List<Long> checklistItemIds) {
}
