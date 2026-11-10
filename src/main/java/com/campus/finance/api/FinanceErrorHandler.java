package com.campus.finance.api;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.finance.application.*;
import com.campus.shared.api.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Scoped to the admin controllers: a package-wide scope would also cover OwnFinanceController,
// whose own advice must answer its profile errors.
@RestControllerAdvice(assignableTypes = {AdminFinanceController.class, AdminManualPaymentController.class})
public class FinanceErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<Error> validation(HttpServletRequest request) { return error(400, "VALIDATION_FAILED", request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformed(HttpServletRequest request) { return error(400, "MALFORMED_REQUEST", request); }
    @ExceptionHandler({AdminFinanceController.InvalidQueryException.class, AdminManualPaymentController.InvalidQueryException.class})
    ResponseEntity<Error> query(HttpServletRequest request) { return error(400, "INVALID_QUERY_PARAMETER", request); }
    @ExceptionHandler(FinanceObligationService.NotFoundException.class)
    ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "FINANCE_RESOURCE_NOT_FOUND", request); }
    @ExceptionHandler({FinanceObligationService.StaleVersionException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Error> stale(HttpServletRequest request) { return error(409, "CONCURRENT_MODIFICATION", request); }
    @ExceptionHandler(FinanceObligationService.ReferenceUnavailableException.class)
    ResponseEntity<Error> reference(HttpServletRequest request) { return error(409, "FINANCE_REFERENCE_UNAVAILABLE", request); }
    @ExceptionHandler(FinanceObligationService.InvalidStateException.class)
    ResponseEntity<Error> state(HttpServletRequest request) { return error(409, "INVALID_FINANCE_STATE", request); }
    @ExceptionHandler(ManualPaymentService.ExceedsBalanceException.class)
    ResponseEntity<Error> balance(HttpServletRequest request) { return error(409, "PAYMENT_EXCEEDS_BALANCE", request); }
    @ExceptionHandler(ManualPaymentService.IdempotencyKeyReusedException.class)
    ResponseEntity<Error> idempotency(HttpServletRequest request) { return error(409, "IDEMPOTENCY_KEY_REUSED", request); }
    @ExceptionHandler(FinanceAudit.UnavailableException.class)
    ResponseEntity<Error> audit(HttpServletRequest request) { return error(500, "AUDIT_WRITE_FAILED", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> integrity(DataIntegrityViolationException failure, HttpServletRequest request) {
        if (failure.getMostSpecificCause() instanceof SQLException sql) {
            if ("23505".equals(sql.getSQLState())) return error(409, "FINANCE_CODE_ALREADY_EXISTS", request);
            if ("23503".equals(sql.getSQLState())) return error(409, "FINANCE_REFERENCE_UNAVAILABLE", request);
        }
        throw failure;
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected(Exception exception, HttpServletRequest request) {
        var framework = ApiErrors.frameworkError(exception);
        if (framework != null) return error(framework.status(), framework.code(), request);
        ApiErrors.logUnexpected(request, exception);
        return error(500, "INTERNAL_ERROR", request);
    }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code, HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), ApiErrors.traceId(request)));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
