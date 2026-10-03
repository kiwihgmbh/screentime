package com.kiwih.screentime.web.dto;

import java.time.Instant;

/** One item ticked for an entry, with the text as it read then. */
public record ChecklistTickView(Long itemId, String text, Instant tickedAt) {
}
