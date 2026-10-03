package com.kiwih.screentime.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Applies the CORS rules only to requests that are actually cross origin.
 *
 * A browser sends an {@code Origin} header on every request that is not a
 * plain GET, including requests to the page's own origin. Spring treats any
 * request carrying that header as a CORS request, so a same origin POST was
 * being checked against the allowed origin list and rejected with an empty
 * 403. In the normal deployment, where nginx serves the app and proxies
 * {@code /api} under one host name, that meant signing in did not work at all.
 *
 * A same origin request has nothing to negotiate. Returning {@code null} here
 * tells the CORS filter to leave it alone and carry on down the chain.
 */
class SameOriginAwareCorsSource implements CorsConfigurationSource {

    private final CorsConfigurationSource delegate;

    SameOriginAwareCorsSource(CorsConfigurationSource delegate) {
        this.delegate = delegate;
    }

    @Override
    @Nullable
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || isSameOrigin(request, origin)) {
            return null;
        }
        return delegate.getCorsConfiguration(request);
    }

    /**
     * Whether the origin is the one this request was addressed to.
     *
     * The scheme, host and port come from the request as the application sees
     * it, which behind a reverse proxy means after the forwarded headers have
     * been applied. That is why {@code forward-headers-strategy} is set: without
     * it a request arriving over HTTPS would look like plain HTTP here and no
     * origin would ever match.
     */
    private static boolean isSameOrigin(HttpServletRequest request, String origin) {
        UriComponents sent;
        try {
            sent = UriComponentsBuilder.fromUriString(origin).build();
        } catch (RuntimeException e) {
            // a malformed Origin is not this request's own origin
            return false;
        }
        if (sent.getScheme() == null || sent.getHost() == null) {
            return false;
        }
        return sent.getScheme().equalsIgnoreCase(request.getScheme())
                && sent.getHost().equalsIgnoreCase(request.getServerName())
                && port(sent.getScheme(), sent.getPort())
                        == port(request.getScheme(), request.getServerPort());
    }

    /** An origin may leave the default port out, a request never does. */
    private static int port(String scheme, int port) {
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
