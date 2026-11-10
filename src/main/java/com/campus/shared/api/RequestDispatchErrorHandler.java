package com.campus.shared.api;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Raised before controller selection, so module-scoped advices never see these failures.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class RequestDispatchErrorHandler {
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoResourceFoundException.class})
    ResponseEntity<Error> dispatch(Exception exception, HttpServletRequest request) {
        var failure = ApiErrors.frameworkError(exception);
        int status = failure != null ? failure.status() : HttpStatus.INTERNAL_SERVER_ERROR.value();
        String code = failure != null ? failure.code() : "INTERNAL_ERROR";
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code,
                HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), ApiErrors.traceId(request)));
    }

    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
