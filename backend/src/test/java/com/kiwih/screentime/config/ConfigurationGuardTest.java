package com.kiwih.screentime.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This repository is public and other families run it. A missing secret has to
 * be a loud failure before anything starts, never a default somebody could
 * guess, and the message has to name the variable rather than bury it in a
 * stack trace from inside a library.
 */
@DisplayName("the application refuses to start without what the deployment must supply")
class ConfigurationGuardTest {

    @Test
    void itStartsWhenEverythingIsSupplied() {
        ConfigurationGuard.validate(complete());
    }

    @Test
    void itRefusesWithoutAnAdminUser() {
        assertThatThrownBy(() -> ConfigurationGuard.validate(complete().withProperty("app.admin.username", "")))
                .isInstanceOf(MissingConfigurationException.class)
                .hasMessageContaining("APP_ADMIN_USER is not set")
                .hasMessageContaining("first parent account");
    }

    @Test
    void itRefusesWithoutAnAdminPassword() {
        assertThatThrownBy(() -> ConfigurationGuard.validate(complete().withProperty("app.admin.password", "")))
                .isInstanceOf(MissingConfigurationException.class)
                .hasMessageContaining("APP_ADMIN_PASSWORD is not set");
    }

    @Test
    void itRefusesWithoutAJwtSecret() {
        assertThatThrownBy(() -> ConfigurationGuard.validate(complete().withProperty("app.jwt.secret", "")))
                .isInstanceOf(MissingConfigurationException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    void itRefusesASecretTooShortToBeWorthSigningWith() {
        assertThatThrownBy(() -> ConfigurationGuard.validate(complete().withProperty("app.jwt.secret", "too-short")))
                .isInstanceOf(MissingConfigurationException.class)
                .hasMessageContaining("only 9 characters")
                .hasMessageContaining("at least 32");
    }

    @Test
    void itRefusesATokenLifetimeThatWouldExpireImmediately() {
        assertThatThrownBy(() -> ConfigurationGuard.validate(complete().withProperty("app.jwt.ttl-days", "0")))
                .isInstanceOf(MissingConfigurationException.class)
                .hasMessageContaining("JWT_TTL_DAYS");
    }

    @Test
    void itRefusesATimeZoneTheMachineDoesNotKnow() {
        // every day and week boundary is derived in this zone, so a typo here
        // would quietly move every budget
        assertThatThrownBy(() -> ConfigurationGuard.validate(complete().withProperty("app.timezone", "Europe/Zurrich")))
                .isInstanceOf(MissingConfigurationException.class)
                .hasMessageContaining("APP_TIMEZONE")
                .hasMessageContaining("Europe/Zurrich");
    }

    @Test
    void theRefusalNamesTheVariableAndWhatToDoAboutIt() {
        // the operator sees this block at the bottom of the failed startup,
        // not a page of bean creation failure from inside the JWT library
        MissingConfigurationException failure = new MissingConfigurationException(
                "JWT_SECRET is not set.", "Generate one with: openssl rand -base64 48");

        FailureAnalysis analysis = new MissingConfigurationFailureAnalyzer()
                .analyze(new RuntimeException(failure), failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).isEqualTo("JWT_SECRET is not set.");
        assertThat(analysis.getAction()).contains("openssl rand");
    }

    @Test
    void theGuardIsRegisteredSoItRunsBeforeAnyBeanIsCreated() throws Exception {
        // as a bean it had no guaranteed order and JwtService won the race
        String factories = new String(getClass().getResourceAsStream("/META-INF/spring.factories")
                .readAllBytes());
        assertThat(factories)
                .contains("org.springframework.context.ApplicationContextInitializer")
                .contains(ConfigurationGuard.class.getName())
                .contains("org.springframework.boot.diagnostics.FailureAnalyzer")
                .contains(MissingConfigurationFailureAnalyzer.class.getName());
    }

    private static MockEnvironment complete() {
        return new MockEnvironment()
                .withProperty("app.admin.username", "parent")
                .withProperty("app.admin.password", "a-password-for-the-test")
                .withProperty("app.jwt.secret", "a-test-secret-that-is-long-enough-for-hs256")
                .withProperty("app.jwt.ttl-days", "30")
                .withProperty("app.timezone", "Europe/Zurich");
    }
}
