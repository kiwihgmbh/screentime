package com.kiwih.screentime.web;

import com.kiwih.screentime.service.AuthService;
import com.kiwih.screentime.web.dto.LoginRequest;
import com.kiwih.screentime.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Exchange a user name and password for an access token")
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.Authenticated result = authService.login(request.username(), request.password());
        return new LoginResponse(result.token(), result.expiresAt(),
                result.user().getId(), result.user().getUsername(),
                result.user().getDisplayName(), result.user().getRole());
    }
}
