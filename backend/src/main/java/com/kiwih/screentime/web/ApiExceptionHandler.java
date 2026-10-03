package com.kiwih.screentime.web;

import com.kiwih.screentime.rules.RuleViolation;
import com.kiwih.screentime.service.AccountResolver;
import com.kiwih.screentime.service.NotFoundException;
import com.kiwih.screentime.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a refusal into a status code. The rules themselves know nothing about
 * HTTP; this is the only place that translates.
 *
 * Messages are written to be shown to a child, because they will be.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(RuleViolation.class)
    public ResponseEntity<ApiError> onRuleViolation(RuleViolation e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case SESSION_ALREADY_OPEN -> HttpStatus.CONFLICT;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        Map<String, Object> details = new LinkedHashMap<>();
        if (e.openSessionId() != null) {
            details.put("openSessionId", e.openSessionId());
        }
        return body(status, e.getMessage(), details);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> onBadCredentials(BadCredentialsException e) {
        return body(HttpStatus.UNAUTHORIZED, "Wrong user name or password.", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "Only a parent can do that.", Map.of());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), Map.of());
    }

    @ExceptionHandler(AccountResolver.NoAccountException.class)
    public ResponseEntity<ApiError> onNoAccount(AccountResolver.NoAccountException e) {
        return body(HttpStatus.CONFLICT, e.getMessage(), Map.of());
    }

    /** A settings change that does not hold together, and anything else refused by a value object. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return body(HttpStatus.BAD_REQUEST,
                message.isBlank() ? "The request is not valid." : message, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onAnythingElse(Exception e) {
        // the message is deliberately generic: an internal failure must not
        // describe the inside of the application to the browser
        log.error("Unhandled failure", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.", Map.of());
    }

    private ResponseEntity<ApiError> body(HttpStatus status, String message, Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ApiError(
                status.value(), status.getReasonPhrase(), message, clock.instant(),
                details.isEmpty() ? null : details));
    }
}
