package com.campus.identity.api;

import java.time.Instant;

import com.campus.identity.application.AuthenticationService;
import com.campus.identity.application.AdminUserManagementService;
import com.campus.identity.application.ConcurrentModificationException;
import com.campus.identity.application.LastActiveAdministratorRequiredException;
import com.campus.identity.application.SecurityMutationCoordinator.InvalidExpectedVersionException;
import com.campus.identity.domain.UserAccount.InvalidAccountStatusTransitionException;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.student.application.StudentManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> validation(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> malformed(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request is invalid", request); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return exception.getName().endsWith("Id") ? error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request is invalid", request) : error(HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER", "Query parameter is invalid", request);
    }
    @ExceptionHandler(AdminUserManagementService.RequestValidationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> requestValidation(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request); }
    @ExceptionHandler(InvalidExpectedVersionException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> invalidExpectedVersion(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request); }
    @ExceptionHandler(AdminUserController.InvalidQueryParameterException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> query(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER", "Query parameter is invalid", request); }
    @ExceptionHandler(AdminUserManagementService.UnknownRoleException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> unknownRole(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "Role is not recognized", request); }
    @ExceptionHandler(AdminUserManagementService.UserNotFoundException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> userNotFound(HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found", request); }
    @ExceptionHandler(AdminUserManagementService.EmailAlreadyExistsException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> duplicate(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use", request); }
    @ExceptionHandler(AdminUserManagementService.SelfModificationNotAllowedException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> self(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "SELF_MODIFICATION_NOT_ALLOWED", "Self modification is not allowed", request); }
    @ExceptionHandler(LastActiveAdministratorRequiredException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> lastAdmin(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN_REQUIRED", "At least one active administrator is required", request); }
    @ExceptionHandler(ConcurrentModificationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> concurrent(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "User was modified concurrently", request); }
    @ExceptionHandler(InvalidAccountStatusTransitionException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> transition(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Account status transition is not allowed", request); }
    @ExceptionHandler(OrganizationUnitManagementService.RequestValidationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> organizationValidation(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request); }
    @ExceptionHandler(OrganizationUnitManagementService.OrganizationUnitNotFoundException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> organizationNotFound(HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, "ORGANIZATION_UNIT_NOT_FOUND", "Organization unit was not found", request); }
    @ExceptionHandler(OrganizationUnitManagementService.OrganizationUnitCodeAlreadyExistsException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> organizationDuplicate(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "ORGANIZATION_UNIT_CODE_ALREADY_EXISTS", "Organization unit code is already in use", request); }
    @ExceptionHandler(OrganizationUnitManagementService.ConcurrentOrganizationUnitModificationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> organizationConcurrent(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Organization unit was modified concurrently", request); }
    @ExceptionHandler(StudentManagementService.RequestValidationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> studentValidation(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request); }
    @ExceptionHandler(StudentManagementService.StudentNotFoundException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> studentNotFound(HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found", request); }
    @ExceptionHandler(StudentManagementService.StudentNumberAlreadyExistsException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> studentDuplicate(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "STUDENT_NUMBER_ALREADY_EXISTS", "Student number is already in use", request); }
    @ExceptionHandler(StudentManagementService.OrganizationUnitUnavailableException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> studentOrganization(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "ORGANIZATION_UNIT_UNAVAILABLE", "Organization unit is unavailable", request); }
    @ExceptionHandler(StudentManagementService.ConcurrentStudentModificationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> studentConcurrent(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Student was modified concurrently", request); }
    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> internal(HttpServletRequest request) { return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request); }
    private org.springframework.http.ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) { return org.springframework.http.ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI(), null)); }
    public record ErrorResponse(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
