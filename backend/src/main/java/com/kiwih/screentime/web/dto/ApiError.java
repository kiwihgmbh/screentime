package com.kiwih.screentime.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * One error shape for the whole API, so the frontend has one thing to handle.
 * {@code details} carries the extras a specific failure needs, such as the id
 * of the session that is already running.
 */
public record ApiError(int status, String error, String message,
                       Instant at, Map<String, Object> details) {
}
