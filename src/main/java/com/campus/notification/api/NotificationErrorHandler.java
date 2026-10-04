package com.campus.notification.api;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.notification.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses=AdminNotificationController.class)
public class NotificationErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class}) ResponseEntity<Error> validation(HttpServletRequest r) { return error(400,"VALIDATION_FAILED",r); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<Error> malformed(HttpServletRequest r) { return error(400,"MALFORMED_REQUEST",r); }
    @ExceptionHandler(AdminNotificationController.InvalidQueryException.class) ResponseEntity<Error> query(HttpServletRequest r) { return error(400,"INVALID_QUERY_PARAMETER",r); }
    @ExceptionHandler(NotificationService.NotFoundException.class) ResponseEntity<Error> missing(HttpServletRequest r) { return error(404,"NOTIFICATION_NOT_FOUND",r); }
    @ExceptionHandler({NotificationService.StaleVersionException.class,OptimisticLockingFailureException.class}) ResponseEntity<Error> stale(HttpServletRequest r) { return error(409,"CONCURRENT_MODIFICATION",r); }
    @ExceptionHandler(NotificationService.InvalidStateException.class) ResponseEntity<Error> state(HttpServletRequest r) { return error(409,"INVALID_NOTIFICATION_STATE",r); }
    @ExceptionHandler(NotificationService.ReferenceUnavailableException.class) ResponseEntity<Error> reference(HttpServletRequest r) { return error(409,"NOTIFICATION_REFERENCE_UNAVAILABLE",r); }
    @ExceptionHandler(NotificationAudit.UnavailableException.class) ResponseEntity<Error> audit(HttpServletRequest r) { return error(500,"AUDIT_WRITE_FAILED",r); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Error> integrity(DataIntegrityViolationException failure,HttpServletRequest r) {
        if (failure.getMostSpecificCause() instanceof SQLException sql) {
            if ("23505".equals(sql.getSQLState())) return error(409,"NOTIFICATION_CODE_ALREADY_EXISTS",r);
            if ("23503".equals(sql.getSQLState())) return error(409,"NOTIFICATION_REFERENCE_UNAVAILABLE",r);
        }
        throw failure;
    }
    private ResponseEntity<Error> error(int status,String code,HttpServletRequest r) { return ResponseEntity.status(status).body(new Error(Instant.now(),status,code,HttpStatus.valueOf(status).getReasonPhrase(),r.getRequestURI(),null)); }
    public record Error(Instant timestamp,int status,String code,String message,String path,String traceId) { }
}
