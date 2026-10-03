package com.campus.dormitory.api;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.dormitory.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = AdminDormitoryInventoryController.class)
public class DormitoryInventoryErrorHandler {
    @ExceptionHandler(AccommodationAssignmentService.AlreadyAssignedException.class)
    ResponseEntity<Error> assigned(HttpServletRequest request) { return error(409, "ACCOMMODATION_ALREADY_ASSIGNED", request); }
    @ExceptionHandler(AccommodationAssignmentService.StudentUnavailableException.class)
    ResponseEntity<Error> student(HttpServletRequest request) { return error(409, "STUDENT_UNAVAILABLE", request); }
    @ExceptionHandler(AccommodationAssignmentService.InvalidAssignmentStateException.class)
    ResponseEntity<Error> assignmentState(HttpServletRequest request) { return error(409, "INVALID_ASSIGNMENT_STATE", request); }
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<Error> validation(HttpServletRequest request) { return error(400, "VALIDATION_FAILED", request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformed(HttpServletRequest request) { return error(400, "MALFORMED_REQUEST", request); }
    @ExceptionHandler(DormitoryInventoryService.InvalidQueryException.class)
    ResponseEntity<Error> query(HttpServletRequest request) { return error(400, "INVALID_QUERY_PARAMETER", request); }
    @ExceptionHandler(DormitoryInventoryService.NotFoundException.class)
    ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "DORMITORY_RESOURCE_NOT_FOUND", request); }
    @ExceptionHandler({DormitoryInventoryService.StaleVersionException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Error> stale(HttpServletRequest request) { return error(409, "CONCURRENT_MODIFICATION", request); }
    @ExceptionHandler(DormitoryInventoryService.InvalidStateException.class)
    ResponseEntity<Error> state(HttpServletRequest request) { return error(409, "INVALID_DORMITORY_STATE", request); }
    @ExceptionHandler(DormitoryInventoryService.ReferenceUnavailableException.class)
    ResponseEntity<Error> reference(HttpServletRequest request) { return error(409, "DORMITORY_REFERENCE_UNAVAILABLE", request); }
    @ExceptionHandler(DormitoryAudit.UnavailableException.class)
    ResponseEntity<Error> audit(HttpServletRequest request) { return error(500, "AUDIT_WRITE_FAILED", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> integrity(DataIntegrityViolationException failure, HttpServletRequest request) {
        if (failure.getMostSpecificCause() instanceof SQLException sql) {
            if ("23505".equals(sql.getSQLState())) return error(409, "DORMITORY_CODE_ALREADY_EXISTS", request);
            if ("23503".equals(sql.getSQLState())) return error(409, "DORMITORY_REFERENCE_UNAVAILABLE", request);
        }
        throw failure;
    }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code, HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), null));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
