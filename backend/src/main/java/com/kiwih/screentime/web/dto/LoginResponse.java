package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.Role;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, Long userId,
                            String username, String displayName, Role role) {
}
