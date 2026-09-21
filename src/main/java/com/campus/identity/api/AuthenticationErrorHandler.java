package com.campus.identity.api;

import java.time.Instant;

import com.campus.identity.application.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class AuthenticationErrorHandler {
    @ExceptionHandler(AuthenticationService.AuthenticationFailure.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> authenticationFailure(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed", request);
    }
    @ExceptionHandler(AuthenticationService.RefreshTokenInvalid.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> refreshInvalid(HttpServletRequest request) { return error(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token is invalid", request); }
    @ExceptionHandler(AuthenticationService.RefreshTokenMissing.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> refreshMissing(HttpServletRequest request) { return error(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISSING", "Refresh token is required", request); }
    @ExceptionHandler(AuthenticationService.RefreshTokenExpired.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> refreshExpired(HttpServletRequest request) { return error(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh token has expired", request); }
    @ExceptionHandler(AuthenticationService.RefreshTokenRevoked.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> refreshRevoked(HttpServletRequest request) { return error(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REVOKED", "Refresh token is revoked", request); }
    @ExceptionHandler(AuthenticationService.RefreshTokenReused.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> refreshReused(HttpServletRequest request) { return error(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "Refresh token reuse detected", request); }
    @ExceptionHandler(AuthenticationService.AccountNotActive.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> accountNotActive(HttpServletRequest request) { return error(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Account is not permitted to authenticate", request); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public org.springframework.http.ResponseEntity<ErrorResponse> malformed(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request is invalid", request); }
    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> internal(HttpServletRequest request) { return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request); }
    private org.springframework.http.ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) { return org.springframework.http.ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI(), null)); }
    public record ErrorResponse(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
