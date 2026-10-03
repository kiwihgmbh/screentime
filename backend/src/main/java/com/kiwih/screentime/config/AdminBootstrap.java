package com.kiwih.screentime.config;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.User;
import com.kiwih.screentime.repo.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first parent account on first startup. Nothing is seeded in a
 * migration, so a public repository carries no account anybody could log in
 * with.
 *
 * Runs once: if a parent already exists the configured values are left alone,
 * including the password. Changing a password is done in the app.
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;

    public AdminBootstrap(AppProperties properties, UserRepository users,
                          PasswordEncoder passwordEncoder, TransactionTemplate transactions) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
    }

    @PostConstruct
    void createFirstParentIfMissing() {
        transactions.executeWithoutResult(status -> {
            if (users.existsByRole(Role.PARENT)) {
                log.info("A parent account already exists, leaving accounts untouched.");
                return;
            }
            String username = properties.getAdmin().getUsername().trim();
            if (users.existsByUsername(username)) {
                throw new IllegalStateException("The user name " + username
                        + " from APP_ADMIN_USER is already taken by a non parent account.");
            }
            users.save(new User(
                    username,
                    passwordEncoder.encode(properties.getAdmin().getPassword()),
                    username,
                    Role.PARENT));
            log.info("Created the first parent account \"{}\". Change its password in the app.", username);
        });
    }
}
