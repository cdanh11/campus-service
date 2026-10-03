package com.campus.academic.api.enrollment;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.academic.application.EnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AdminEnrollmentController.class)
public class EnrollmentErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<Error> validation(HttpServletRequest request) { return error(400, "VALIDATION_FAILED", request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformed(HttpServletRequest request) { return error(400, "MALFORMED_REQUEST", request); }
    @ExceptionHandler(AdminEnrollmentController.InvalidQueryException.class)
    ResponseEntity<Error> query(HttpServletRequest request) { return error(400, "INVALID_QUERY_PARAMETER", request); }
    @ExceptionHandler({EnrollmentService.NotFoundException.class, AcademicDeliveryService.ResourceNotFoundException.class})
    ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "ACADEMIC_RESOURCE_NOT_FOUND", request); }
    @ExceptionHandler({EnrollmentService.StaleVersionException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Error> stale(HttpServletRequest request) { return error(409, "CONCURRENT_MODIFICATION", request); }
    @ExceptionHandler(EnrollmentService.InvalidStateException.class)
    ResponseEntity<Error> state(HttpServletRequest request) { return error(409, "INVALID_ACADEMIC_STATE", request); }
    @ExceptionHandler(EnrollmentService.StudentUnavailableException.class)
    ResponseEntity<Error> reference(HttpServletRequest request) { return error(409, "ACADEMIC_REFERENCE_UNAVAILABLE", request); }
    @ExceptionHandler(EnrollmentService.CapacityExceededException.class)
    ResponseEntity<Error> full(HttpServletRequest request) { return error(409, "SECTION_CAPACITY_EXCEEDED", request); }
    @ExceptionHandler(EnrollmentService.DuplicateException.class)
    ResponseEntity<Error> duplicate(HttpServletRequest request) { return error(409, "ACADEMIC_RESOURCE_ALREADY_EXISTS", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> integrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        if (exception.getMostSpecificCause() instanceof SQLException failure) {
            if ("23505".equals(failure.getSQLState())) return duplicate(request);
            if ("23503".equals(failure.getSQLState())) return reference(request);
        }
        throw exception;
    }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code,
                HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), null));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
