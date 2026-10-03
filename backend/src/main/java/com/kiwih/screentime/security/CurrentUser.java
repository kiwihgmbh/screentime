package com.kiwih.screentime.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The caller, from the security context. Never from a request body. */
@Component
public class CurrentUser {

    public AppPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppPrincipal principal)) {
            throw new IllegalStateException("No authenticated caller. This endpoint must be secured.");
        }
        return principal;
    }
}
