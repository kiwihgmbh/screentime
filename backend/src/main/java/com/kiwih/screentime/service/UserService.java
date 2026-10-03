package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.User;
import com.kiwih.screentime.repo.UserRepository;
import com.kiwih.screentime.rules.RuleViolation;
import com.kiwih.screentime.security.AppPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Accounts, created by a parent inside the app. There is no registration and no
 * invitation flow: a family of four does not need one.
 *
 * Nothing here ever writes a password into the audit trail.
 */
@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAllByOrderByUsernameAsc();
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public User create(AppPrincipal caller, String username, String password,
                       String displayName, Role role) {
        String name = requireText(username, "A user name").trim();
        requirePassword(password);
        if (users.existsByUsername(name)) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "The user name " + name + " is already taken.");
        }
        if (role == Role.CHILD && hasActiveChild()) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "There is already an active child account. Version one keeps one account.");
        }
        User saved = users.save(new User(name, passwordEncoder.encode(password),
                displayName == null || displayName.isBlank() ? name : displayName.trim(), role));
        audit.created(AuditService.USER, saved.getId(), snapshot(saved), caller.userId());
        return saved;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public User update(AppPrincipal caller, Long id, String displayName, String password,
                       Role role, Boolean active) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        Object before = snapshot(user);

        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (password != null && !password.isBlank()) {
            requirePassword(password);
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        if (role != null && role != user.getRole()) {
            if (role == Role.CHILD && hasActiveChild()) {
                throw new RuleViolation(RuleViolation.Kind.INVALID,
                        "There is already an active child account.");
            }
            user.setRole(role);
            requireAParentRemains(user);
        }
        if (active != null) {
            user.setActive(active);
            if (!active) {
                requireAParentRemains(user);
            }
        }
        audit.updated(AuditService.USER, user.getId(), before, snapshot(user), caller.userId());
        return user;
    }

    private boolean hasActiveChild() {
        return users.findAllByOrderByUsernameAsc().stream()
                .anyMatch(u -> u.getRole() == Role.CHILD && u.isActive());
    }

    /** Locking everyone out of a self hosted app is not a recoverable mistake. */
    private void requireAParentRemains(User changed) {
        boolean anyParentLeft = users.findAllByOrderByUsernameAsc().stream()
                .anyMatch(u -> u.getRole() == Role.PARENT && u.isActive());
        if (!anyParentLeft) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    "This would leave the app without an active parent account.");
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "A password needs at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new RuleViolation(RuleViolation.Kind.INVALID, what + " is required.");
        }
        return value;
    }

    /** No password, not even its hash, ever reaches the audit trail. */
    private static Object snapshot(User u) {
        return new Snapshot(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole().name(), u.isActive());
    }

    private record Snapshot(Long id, String username, String displayName, String role, boolean active) {
    }
}
