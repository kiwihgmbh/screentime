package com.kiwih.screentime.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Checks the environment before a single bean is created.
 *
 * This runs as an initializer rather than as a bean on purpose. As a bean it
 * had no guaranteed order, and {@code JwtService} was constructed first: a
 * deployment with a short secret got a page of Spring bean creation failure
 * from inside the JWT library instead of being told which variable was wrong.
 *
 * This repository is public and other families run it. A missing secret has to
 * fail loudly, early, and in words the person deploying it can act on.
 */
public class ConfigurationGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final int MIN_SECRET_LENGTH = 32;

    private static final String FILL_IN_ENV =
            "Copy .env.example to .env and fill it in. See docs/DEPLOY.md.";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    /** Visible for tests, which check each refusal without starting a context. */
    public static void validate(Environment environment) {
        requirePresent(environment, "app.admin.username", "APP_ADMIN_USER",
                "the first parent account cannot be created without a user name");
        requirePresent(environment, "app.admin.password", "APP_ADMIN_PASSWORD",
                "the first parent account cannot be created without a password");
        requirePresent(environment, "app.jwt.secret", "JWT_SECRET",
                "access tokens cannot be signed without a secret");

        String secret = environment.getProperty("app.jwt.secret", "").trim();
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new MissingConfigurationException(
                    "JWT_SECRET is only " + secret.length() + " characters long. HS256 needs at least "
                            + MIN_SECRET_LENGTH + ".",
                    "Generate one with: openssl rand -base64 48");
        }

        Integer ttlDays = environment.getProperty("app.jwt.ttl-days", Integer.class, 30);
        if (ttlDays < 1) {
            throw new MissingConfigurationException(
                    "JWT_TTL_DAYS is " + ttlDays + ", which would expire every token immediately.",
                    "Set JWT_TTL_DAYS to at least 1. The default is 30, so a child is not "
                            + "logged out every day and stops booking.");
        }

        String timezone = environment.getProperty("app.timezone", "Europe/Zurich");
        try {
            java.time.ZoneId.of(timezone);
        } catch (RuntimeException e) {
            throw new MissingConfigurationException(
                    "APP_TIMEZONE is \"" + timezone + "\", which is not a time zone this machine knows.",
                    "Use a zone name such as Europe/Zurich. Every day and week boundary in the "
                            + "application is derived in this zone.");
        }
    }

    private static void requirePresent(Environment environment, String property,
                                       String variable, String why) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new MissingConfigurationException(
                    variable + " is not set, and " + why + ".", FILL_IN_ENV);
        }
    }
}
