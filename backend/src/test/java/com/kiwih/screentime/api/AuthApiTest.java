package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("logging in, and what each role may reach")
class AuthApiTest extends ApiTestBase {

    @Test
    void theFirstParentAccountIsCreatedAtStartupAndNotSeeded() {
        Integer parents = jdbc.queryForObject(
                "select count(*) from users where role = 'PARENT'", Integer.class);
        assertThat(parents)
                .as("exactly the one account the environment configured, and no migration seeded it")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select username from users where role = 'PARENT'", String.class))
                .isEqualTo(PARENT_USERNAME);
    }

    @Test
    void aPasswordIsStoredOnlyAsABcryptHash() {
        String hash = jdbc.queryForObject(
                "select password_hash from users where username = ?", String.class, PARENT_USERNAME);
        assertThat(hash).startsWith("$2");
        assertThat(hash).doesNotContain(PARENT_PASSWORD);
    }

    @Test
    void loginReturnsATokenThatLastsThirtyDays() throws Exception {
        JsonNode body = readBody(mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(
                        Map.of("username", PARENT_USERNAME, "password", PARENT_PASSWORD))))
                .andExpect(status().isOk()));

        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("role").asText()).isEqualTo("PARENT");
        assertThat(java.time.Instant.parse(body.get("expiresAt").asText()))
                .isEqualTo(time.instant().plus(java.time.Duration.ofDays(30)));
    }

    @Test
    void aWrongPasswordIsRefusedWithoutSayingWhichPartWasWrong() throws Exception {
        JsonNode body = readBody(mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(
                        Map.of("username", PARENT_USERNAME, "password", "not-the-password"))))
                .andExpect(status().isUnauthorized()));
        assertThat(body.get("message").asText()).isEqualTo("Wrong user name or password.");
    }

    @Test
    void anUnknownUserGetsTheSameAnswerAsAWrongPassword() throws Exception {
        JsonNode body = readBody(mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(
                        Map.of("username", "nobody", "password", "whatever-password"))))
                .andExpect(status().isUnauthorized()));
        assertThat(body.get("message").asText()).isEqualTo("Wrong user name or password.");
    }

    @Test
    void aDeactivatedAccountCannotLogIn() throws Exception {
        Long childId = jdbc.queryForObject(
                "select id from users where username = ?", Long.class, CHILD_USERNAME);
        asParent(put("/api/users/" + childId), Map.of("active", false))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(
                                Map.of("username", CHILD_USERNAME, "password", CHILD_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutATokenNothingIsReadable() throws Exception {
        mvc.perform(get("/api/account/current")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void aGarbledTokenIsNotAToken() throws Exception {
        mvc.perform(get("/api/account/current").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() throws Exception {
        // the same payload shape, signed with a different key
        String foreign = io.jsonwebtoken.Jwts.builder()
                .subject("1").claim("username", PARENT_USERNAME).claim("role", "PARENT")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "a-different-secret-that-is-long-enough-for-hs256".getBytes()))
                .compact();
        mvc.perform(get("/api/account/current").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        String token = login(PARENT_USERNAME, PARENT_PASSWORD);
        asParent(get("/api/account/current")).andExpect(status().isOk());

        time.set(time.instant().plus(java.time.Duration.ofDays(31)));
        mvc.perform(get("/api/account/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aChildCanReadTheSettingsButNotChangeThem() throws Exception {
        // the rules page shows the numbers the account actually runs on, so a
        // child that cannot read them would be shown something else
        JsonNode settings = readBody(asChild(get("/api/settings")).andExpect(status().isOk()));
        assertThat(settings.get("weeklyMinutes").asText()).isEqualTo("480");
        assertThat(settings.get("cutoffHour").asText()).isEqualTo("20");

        asChild(put("/api/settings"), Map.of("weeklyMinutes", "9999"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aChildCanReachTheirOwnAccount() throws Exception {
        asChild(get("/api/account/current")).andExpect(status().isOk());
        asChild(get("/api/account/week")).andExpect(status().isOk());
        asChild(get("/api/account/history")).andExpect(status().isOk());
        asChild(get("/api/sessions")).andExpect(status().isOk());
    }

    @Test
    void aChildCannotReachAnythingThatBelongsToAParent() throws Exception {
        asChild(put("/api/settings"), Map.of("weeklyMinutes", "9999")).andExpect(status().isForbidden());
        asChild(get("/api/users")).andExpect(status().isForbidden());
        asChild(post("/api/users"), Map.of("username", "x", "password", "12345678", "role", "PARENT"))
                .andExpect(status().isForbidden());
        asChild(post("/api/adjustments"), Map.of("weekStart", MONDAY.toString(), "minutes", 60, "reason", "because"))
                .andExpect(status().isForbidden());
        asChild(post("/api/checks"), Map.of("weekStart", MONDAY.toString(),
                "reported", java.util.List.of(Map.of("deviceId", deviceId("iPad"), "minutes", 10)),
                "deliberate", false))
                .andExpect(status().isForbidden());
        asChild(put("/api/weeks/" + MONDAY + "/holiday"), Map.of("holiday", true))
                .andExpect(status().isForbidden());
    }

    @Test
    void aChildCannotEditOrDeleteAnEntry() throws Exception {
        Long id = bookAsChild(30);
        asChild(put("/api/sessions/" + id), Map.of("minutes", 5)).andExpect(status().isForbidden());
        asChild(delete("/api/sessions/" + id)).andExpect(status().isForbidden());
    }

    @Test
    void aSecondActiveChildAccountIsRefused() throws Exception {
        asParent(post("/api/users"), Map.of(
                "username", "second-child", "password", "another-password", "role", "CHILD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theAppCannotBeLeftWithoutAParent() throws Exception {
        Long parentId = jdbc.queryForObject(
                "select id from users where username = ?", Long.class, PARENT_USERNAME);
        asParent(put("/api/users/" + parentId), Map.of("active", false))
                .andExpect(status().isForbidden());
    }

    @Test
    void noEndpointEverReturnsAPasswordOrAHash() throws Exception {
        String users = asParent(get("/api/users")).andReturn().getResponse().getContentAsString();
        assertThat(users).doesNotContain("password").doesNotContain("$2a$").doesNotContain("$2b$");
    }

    private Long bookAsChild(int minutes) throws Exception {
        return readBody(asChild(post("/api/sessions/manual"), Map.of(
                "minutes", minutes, "deviceId", deviceId("iPad"), "type", "FUN")))
                .get("id").asLong();
    }
}
