package com.campus.event.api;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.event.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses=AdminEventController.class)
public class EventErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class}) ResponseEntity<Error> validation(HttpServletRequest request) { return error(400,"VALIDATION_FAILED",request); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<Error> malformed(HttpServletRequest request) { return error(400,"MALFORMED_REQUEST",request); }
    @ExceptionHandler(AdminEventController.InvalidQueryException.class) ResponseEntity<Error> query(HttpServletRequest request) { return error(400,"INVALID_QUERY_PARAMETER",request); }
    @ExceptionHandler(EventCatalogService.NotFoundException.class) ResponseEntity<Error> missing(HttpServletRequest request) { return error(404,"EVENT_NOT_FOUND",request); }
    @ExceptionHandler({EventCatalogService.StaleVersionException.class,OptimisticLockingFailureException.class}) ResponseEntity<Error> stale(HttpServletRequest request) { return error(409,"CONCURRENT_MODIFICATION",request); }
    @ExceptionHandler(EventCatalogService.InvalidStateException.class) ResponseEntity<Error> state(HttpServletRequest request) { return error(409,"INVALID_EVENT_STATE",request); }
    @ExceptionHandler(EventAudit.UnavailableException.class) ResponseEntity<Error> audit(HttpServletRequest request) { return error(500,"AUDIT_WRITE_FAILED",request); }
    @ExceptionHandler(EventRegistrationService.NotFoundException.class) ResponseEntity<Error> registrationMissing(HttpServletRequest request) { return error(404,"EVENT_REGISTRATION_NOT_FOUND",request); }
    @ExceptionHandler(EventRegistrationService.DuplicateMembershipException.class) ResponseEntity<Error> duplicateMembership(HttpServletRequest request) { return error(409,"EVENT_REGISTRATION_ALREADY_EXISTS",request); }
    @ExceptionHandler(EventRegistrationService.StudentUnavailableException.class) ResponseEntity<Error> student(HttpServletRequest request) { return error(409,"EVENT_STUDENT_UNAVAILABLE",request); }
    @ExceptionHandler(EventRegistrationService.CapacityExceededException.class) ResponseEntity<Error> capacity(HttpServletRequest request) { return error(409,"EVENT_CAPACITY_EXCEEDED",request); }
    @ExceptionHandler(EventRegistrationService.ForbiddenActionException.class) ResponseEntity<Error> forbidden(HttpServletRequest request) { return error(403,"ACCESS_DENIED",request); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Error> integrity(DataIntegrityViolationException failure,HttpServletRequest request) {
        if (failure.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState()))
            return error(409,"EVENT_CODE_ALREADY_EXISTS",request);
        throw failure;
    }
    private ResponseEntity<Error> error(int status,String code,HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(),status,code,HttpStatus.valueOf(status).getReasonPhrase(),request.getRequestURI(),null));
    }
    public record Error(Instant timestamp,int status,String code,String message,String path,String traceId) { }
}
