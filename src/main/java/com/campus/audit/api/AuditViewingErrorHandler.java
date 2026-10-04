package com.campus.audit.api;

import java.time.Instant;
import com.campus.audit.application.AuditViewingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = AdminAuditController.class)
public class AuditViewingErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class}) ResponseEntity<Error> invalid(HttpServletRequest request) { return error(400, "INVALID_QUERY_PARAMETER", request); }
    @ExceptionHandler(AuditViewingService.NotFoundException.class) ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "AUDIT_EVENT_NOT_FOUND", request); }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code, HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), null));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
