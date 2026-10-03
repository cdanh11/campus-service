package com.campus.academic.api.delivery;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.academic.domain.AcademicLifecycle;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = AdminAcademicTermController.class)
public class AcademicDeliveryErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<Error> validation(HttpServletRequest request) { return error(400, "VALIDATION_FAILED", request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformed(HttpServletRequest request) { return error(400, "MALFORMED_REQUEST", request); }
    @ExceptionHandler(AcademicDeliveryService.InvalidQueryException.class)
    ResponseEntity<Error> query(HttpServletRequest request) { return error(400, "INVALID_QUERY_PARAMETER", request); }
    @ExceptionHandler(AcademicDeliveryService.ResourceNotFoundException.class)
    ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "ACADEMIC_RESOURCE_NOT_FOUND", request); }
    @ExceptionHandler({AcademicDeliveryService.StaleVersionException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Error> stale(HttpServletRequest request) { return error(409, "CONCURRENT_MODIFICATION", request); }
    @ExceptionHandler({AcademicDeliveryService.InvalidStateException.class, AcademicLifecycle.InvalidTransitionException.class})
    ResponseEntity<Error> state(HttpServletRequest request) { return error(409, "INVALID_ACADEMIC_STATE", request); }
    @ExceptionHandler(AcademicDeliveryService.ReferenceUnavailableException.class)
    ResponseEntity<Error> reference(HttpServletRequest request) { return error(409, "ACADEMIC_REFERENCE_UNAVAILABLE", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> integrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        if (exception.getMostSpecificCause() instanceof SQLException failure) {
            if ("23505".equals(failure.getSQLState())) return error(409, "ACADEMIC_RESOURCE_ALREADY_EXISTS", request);
            if ("23503".equals(failure.getSQLState())) return error(409, "ACADEMIC_REFERENCE_UNAVAILABLE", request);
        }
        throw exception;
    }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code, HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), null));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
