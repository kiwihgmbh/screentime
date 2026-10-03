package com.kiwih.screentime.security;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.User;

/** Who is calling. Carried in the token so no request has to be told who it is. */
public record AppPrincipal(Long userId, String username, String displayName, Role role) {

    public static AppPrincipal of(User user) {
        return new AppPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public boolean isParent() {
        return role == Role.PARENT;
    }

    public String authority() {
        return "ROLE_" + role.name();
    }
}
