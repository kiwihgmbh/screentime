package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.User;
import com.kiwih.screentime.repo.UserRepository;
import com.kiwih.screentime.security.AppPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Whose account a request is about.
 *
 * There is one account, the child's, and the parents keep it. A child is always
 * looking at their own; a parent is always looking at the child's, because a
 * parent has no screen time of their own to account for. That is also why
 * {@code sessions} has both a {@code user_id} and a {@code created_by}: a
 * parent booking a correction or starting the film writes a row that belongs to
 * the child and records who entered it.
 */
@Service
public class AccountResolver {

    private final UserRepository users;

    public AccountResolver(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User resolve(AppPrincipal caller) {
        if (caller.role() == Role.CHILD) {
            return users.findById(caller.userId()).orElseThrow(
                    () -> new IllegalStateException("The signed in account no longer exists."));
        }
        List<User> children = users.findAllByOrderByUsernameAsc().stream()
                .filter(u -> u.getRole() == Role.CHILD && u.isActive())
                .toList();
        if (children.isEmpty()) {
            throw new NoAccountException(
                    "There is no child account yet. Create one under users first.");
        }
        if (children.size() > 1) {
            throw new NoAccountException(
                    "There is more than one active child account. Version one keeps one account.");
        }
        return children.getFirst();
    }

    /** Raised when there is no single child account to show. */
    public static class NoAccountException extends RuntimeException {
        public NoAccountException(String message) {
            super(message);
        }
    }
}
