package com.campus.library.api;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.library.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = AdminLibraryController.class)
public class LibraryErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class}) ResponseEntity<Error> validation(HttpServletRequest request) { return error(400, "VALIDATION_FAILED", request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class}) ResponseEntity<Error> malformed(HttpServletRequest request) { return error(400, "MALFORMED_REQUEST", request); }
    @ExceptionHandler(AdminLibraryController.InvalidQueryException.class) ResponseEntity<Error> query(HttpServletRequest request) { return error(400, "INVALID_QUERY_PARAMETER", request); }
    @ExceptionHandler(LibraryService.NotFoundException.class) ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "LIBRARY_RESOURCE_NOT_FOUND", request); }
    @ExceptionHandler({LibraryService.StaleVersionException.class, OptimisticLockingFailureException.class}) ResponseEntity<Error> stale(HttpServletRequest request) { return error(409, "CONCURRENT_MODIFICATION", request); }
    @ExceptionHandler(LibraryService.InvalidStateException.class) ResponseEntity<Error> state(HttpServletRequest request) { return error(409, "INVALID_LIBRARY_STATE", request); }
    @ExceptionHandler(LibraryService.UnavailableReferenceException.class) ResponseEntity<Error> reference(HttpServletRequest request) { return error(409, "LIBRARY_REFERENCE_UNAVAILABLE", request); }
    @ExceptionHandler(LibraryService.CopyAlreadyLoanedException.class) ResponseEntity<Error> unavailable(HttpServletRequest request) { return error(409, "LIBRARY_COPY_ALREADY_LOANED", request); }
    @ExceptionHandler(LibraryAudit.UnavailableException.class) ResponseEntity<Error> audit(HttpServletRequest request) { return error(500, "AUDIT_WRITE_FAILED", request); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Error> integrity(DataIntegrityViolationException failure, HttpServletRequest request) {
        if (failure.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState()))
            return error(409, "LIBRARY_UNIQUE_CONFLICT", request);
        throw failure;
    }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code, HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), null));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
