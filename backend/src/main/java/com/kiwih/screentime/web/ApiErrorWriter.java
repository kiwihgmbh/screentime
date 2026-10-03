package com.kiwih.screentime.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwih.screentime.web.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;

/**
 * Writes the body for the two refusals that happen in the filter chain, before
 * any controller runs.
 *
 * It writes the JSON itself rather than calling {@code sendError}. A
 * {@code sendError} makes the container dispatch to {@code /error}, which
 * re-enters this filter chain with no authentication left and turns every 403
 * into a 401 on the way out. The frontend signs the user out on a 401, so that
 * mix up would log a child out for tapping something only a parent may do.
 */
@Component
public class ApiErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** No usable token: an API answers with a status, never a redirect to a login page. */
    @Override
    public void commence(jakarta.servlet.http.HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    /** A valid token, but not the role this needs. */
    @Override
    public void handle(jakarta.servlet.http.HttpServletRequest request,
                       HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException deniedException)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Only a parent can do that.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                status.value(), status.getReasonPhrase(), message, clock.instant(), null));
    }
}
