package com.kiwih.screentime.api;

import com.kiwih.screentime.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status codes, over a real HTTP connection.
 *
 * MockMvc does not perform the container's dispatch to {@code /error}, so it
 * cannot see what a refusal in the security filter chain actually returns to a
 * browser. It once returned 401 for every 403 here, which would have signed a
 * child out for tapping something only a parent may do. These tests run against
 * a real Tomcat so that cannot come back unnoticed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("what a browser actually receives")
class StatusCodeOverHttpTest extends PostgresTestBase {

    @Autowired
    TestRestTemplate rest;

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private String parentToken;
    private String childToken;

    @BeforeEach
    void signIn() {
        jdbc.update("delete from weekly_check_devices");
        jdbc.update("delete from adjustments");
        jdbc.update("delete from weekly_checks");
        jdbc.update("delete from sessions");
        jdbc.update("delete from audit_log");
        jdbc.update("delete from users where role = 'CHILD'");

        parentToken = login("test-parent", "test-only-not-a-real-secret");
        rest.exchange("/api/users", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", "child",
                        "password", "child-test-password",
                        "displayName", "Child",
                        "role", "CHILD"), auth(parentToken)),
                String.class);
        childToken = login("child", "child-test-password");
    }

    @Test
    void noTokenIsUnauthorizedWithAReadableBody() {
        ResponseEntity<Map<String, Object>> response = get("/api/account/current", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .as("the frontend parses one error shape for everything")
                .isNotNull();
        assertThat(response.getBody()).containsEntry("status", 401);
        assertThat((String) response.getBody().get("message")).isNotBlank();
    }

    @Test
    void aChildReachingAParentEndpointIsForbiddenAndNotSignedOut() {
        // 403 and 401 mean different things to the frontend: it signs the user
        // out on a 401, so a wrong code here logs a child out for tapping the
        // wrong thing
        assertThat(get("/api/users", childToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> settings = exchange(
                "/api/settings", HttpMethod.PUT, childToken, Map.of("weeklyMinutes", "9999"));
        assertThat(settings.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(settings.getBody()).containsEntry("status", 403);

        ResponseEntity<Map<String, Object>> adjustment = exchange(
                "/api/adjustments", HttpMethod.POST, childToken,
                Map.of("weekStart", "2026-09-28", "minutes", -10, "reason", "x"));
        assertThat(adjustment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aChildIsStillSignedInAfterBeingRefused() {
        assertThat(get("/api/users", childToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/account/current", childToken).getStatusCode())
                .as("the same token still works for what the child may do")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aChildCanReadTheSettingsTheRulesPageShows() {
        ResponseEntity<Map<String, Object>> response = get("/api/settings", childToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("cutoffHour", "20");
    }

    @Test
    void aBookingRefusedByARuleIsForbiddenWithTheReason() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/sessions/manual", HttpMethod.POST, childToken,
                Map.of("minutes", 30, "deviceId", deviceId(), "type", "FUN", "date", "2020-01-01"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((String) response.getBody().get("message"))
                .contains("only book time for today");
    }

    @Test
    void aSecondSessionIsAConflictThatNamesTheRunningOne() {
        exchange("/api/sessions/start", HttpMethod.POST, childToken,
                Map.of("deviceId", deviceId(), "type", "FUN"));

        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/sessions/start", HttpMethod.POST, childToken,
                Map.of("deviceId", deviceId(), "type", "FUN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsKey("details");
    }

    @Test
    void somethingThatIsNotThereIsANotFound() {
        assertThat(exchange("/api/sessions/999999", HttpMethod.PUT, parentToken,
                Map.of("minutes", 5)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aWrongPasswordIsUnauthorizedAndSaysNothingMore() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/auth/login", HttpMethod.POST, null,
                Map.of("username", "test-parent", "password", "wrong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Wrong user name or password.");
    }

    /**
     * A browser sends an Origin header on every request that is not a plain
     * GET, including requests to the page's own origin. Treating those as
     * cross origin made signing in impossible in the normal deployment, where
     * nginx serves the app and proxies /api under one host name, and the
     * rejection came back as an empty 403 so the screen could only say
     * "Something went wrong".
     *
     * MockMvc sends no Origin header, so only a test over real HTTP sees this.
     */
    @Test
    void signingInWorksWithTheOriginHeaderABrowserSends() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setOrigin("http://localhost:" + port);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", "test-parent",
                        "password", "test-only-not-a-real-secret"), headers),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
    }

    @Test
    void everyWriteWorksWithTheOriginHeaderToo() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(childToken);
        headers.setOrigin("http://localhost:" + port);

        assertThat(rest.exchange("/api/sessions/manual", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "minutes", 20, "deviceId", deviceId(), "type", "FUN"), headers),
                String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void theDevServerIsStillAllowedToCallTheApiFromItsOwnPort() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setOrigin("http://localhost:4200");

        ResponseEntity<String> response = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", "test-parent",
                        "password", "test-only-not-a-real-secret"), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin())
                .as("a genuinely cross origin caller gets the CORS headers")
                .isEqualTo("http://localhost:4200");
    }

    @Test
    void anotherSiteStillCannotCallTheApiFromABrowser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setOrigin("https://somewhere-else.example.com");

        assertThat(rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", "test-parent",
                        "password", "test-only-not-a-real-secret"), headers),
                String.class).getStatusCode())
                .as("the fix must not turn into permitting everything")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aPreflightFromTheDevServerIsAnswered() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:4200");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(List.of("authorization", "content-type"));

        ResponseEntity<Void> response = rest.exchange(
                "/api/sessions/manual", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("http://localhost:4200");
    }

    @Test
    void theApiDocumentationIsReachableWithoutATokenSoItCanBeRead() {
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------ helpers

    private String login(String username, String password) {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/auth/login", HttpMethod.POST, null,
                Map.of("username", username, "password", password));
        assertThat(response.getStatusCode()).as("login for %s", username).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("token");
    }

    private Long deviceId() {
        return jdbc.queryForObject("select id from devices where name = 'iPad'", Long.class);
    }

    private ResponseEntity<Map<String, Object>> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(token)),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Map<String, Object>> exchange(
            String path, HttpMethod method, String token, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, auth(token)),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
