package com.kiwih.screentime.web.dto;

import com.kiwih.screentime.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 64) String displayName,
        @NotNull Role role) {
}
