package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.User;
import com.kiwih.screentime.repo.UserRepository;
import com.kiwih.screentime.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User name and password in, a token out. No OAuth, no registration. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public Authenticated login(String username, String password) {
        User user = users.findByUsername(username == null ? "" : username.trim())
                .orElse(null);

        // the same answer whether the name or the password was wrong, and the
        // hash is still compared when there is no user so that a missing
        // account does not answer measurably faster than a wrong password
        boolean matches = user != null
                && user.isActive()
                && passwordEncoder.matches(password, user.getPasswordHash());
        if (!matches) {
            if (user == null) {
                passwordEncoder.encode(password == null ? "" : password);
            }
            throw new BadCredentialsException("Wrong user name or password.");
        }

        JwtService.Issued issued = jwtService.issue(user);
        return new Authenticated(user, issued.token(), issued.expiresAt());
    }

    public record Authenticated(User user, String token, java.time.Instant expiresAt) {
    }
}
