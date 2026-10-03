package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.Role;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 64) String displayName,
        @Size(min = 8, max = 128) String password,
        Role role,
        Boolean active) {
}
