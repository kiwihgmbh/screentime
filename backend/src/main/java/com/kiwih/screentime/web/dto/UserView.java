package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.Role;

import java.time.Instant;

/** No password and no hash, ever. */
public record UserView(Long id, String username, String displayName,
                       Role role, boolean active, Instant createdAt) {
}
