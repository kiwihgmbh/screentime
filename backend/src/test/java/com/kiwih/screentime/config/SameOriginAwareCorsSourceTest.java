package com.kiwih.screentime.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A browser sends an Origin header on every request that is not a plain GET,
 * including requests to the page's own origin. Measuring those against the
 * allowed origin list made signing in impossible in the normal deployment and
 * returned an empty 403, so the screen could only say "Something went wrong".
 *
 * The port matters as much as the host here. A reverse proxy that forwards the
 * host without the port, which nginx's $host does, makes a same origin request
 * look cross origin.
 */
@DisplayName("CORS applies to requests that really are cross origin")
class SameOriginAwareCorsSourceTest {

    private static final CorsConfiguration DELEGATED = new CorsConfiguration();

    private final SameOriginAwareCorsSource source =
            new SameOriginAwareCorsSource((CorsConfigurationSource) request -> DELEGATED);

    @Test
    void aRequestWithoutAnOriginIsNotACorsRequest() {
        assertThat(source.getCorsConfiguration(request("http", "localhost", 8080, null)))
                .isNull();
    }

    @Test
    void thePagesOwnOriginNeedsNoNegotiation() {
        assertThat(source.getCorsConfiguration(
                request("http", "localhost", 8080, "http://localhost:8080")))
                .as("this is the request that signing in actually makes")
                .isNull();
    }

    @Test
    void theDefaultPortCountsAsTheSameOriginWhenTheOriginLeavesItOut() {
        // behind TLS the browser sends https://screentime.example.com with no
        // port, and the request arrives on 443
        assertThat(source.getCorsConfiguration(
                request("https", "screentime.example.com", 443, "https://screentime.example.com")))
                .isNull();
        assertThat(source.getCorsConfiguration(
                request("http", "screentime.example.com", 80, "http://screentime.example.com")))
                .isNull();
    }

    @Test
    void anotherPortIsAnotherOrigin() {
        // the dev server on 4200 calling the backend on 8080 is genuinely
        // cross origin and has to go through the allowed origin list
        assertThat(source.getCorsConfiguration(
                request("http", "localhost", 8080, "http://localhost:4200")))
                .isSameAs(DELEGATED);
    }

    @Test
    void anotherSchemeIsAnotherOrigin() {
        assertThat(source.getCorsConfiguration(
                request("https", "screentime.example.com", 443, "http://screentime.example.com")))
                .isSameAs(DELEGATED);
    }

    @Test
    void anotherHostIsAnotherOrigin() {
        assertThat(source.getCorsConfiguration(
                request("https", "screentime.example.com", 443, "https://somewhere-else.example.com")))
                .as("the fix must not turn into permitting everything")
                .isSameAs(DELEGATED);
    }

    @Test
    void aHostThatOnlyLooksLikeTheRightOneIsAnotherOrigin() {
        assertThat(source.getCorsConfiguration(
                request("https", "screentime.example.com", 443,
                        "https://screentime.example.com.attacker.example")))
                .isSameAs(DELEGATED);
    }

    @Test
    void theHostComparisonIgnoresCaseButNothingElse() {
        assertThat(source.getCorsConfiguration(
                request("https", "screentime.example.com", 443, "HTTPS://Screentime.Example.COM")))
                .isNull();
    }

    @Test
    void aMalformedOriginIsNeverTreatedAsTheOwnOrigin() {
        assertThat(source.getCorsConfiguration(
                request("http", "localhost", 8080, "null")))
                .isSameAs(DELEGATED);
        assertThat(source.getCorsConfiguration(
                request("http", "localhost", 8080, "not a url at all")))
                .isSameAs(DELEGATED);
    }

    private static MockHttpServletRequest request(String scheme, String host, int port, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
