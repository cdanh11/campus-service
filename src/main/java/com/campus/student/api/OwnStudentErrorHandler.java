package com.campus.student.api;

import java.time.Instant;
import com.campus.shared.api.ApiErrors;
import com.campus.student.application.StudentProfileNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OwnStudentController.class)
public class OwnStudentErrorHandler {
    @ExceptionHandler(StudentProfileNotFoundException.class)
    ResponseEntity<Error> missing(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Error(Instant.now(), 404, "STUDENT_PROFILE_NOT_FOUND", "Not Found", request.getRequestURI(), ApiErrors.traceId(request)));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected(Exception exception, HttpServletRequest request) {
        var framework = ApiErrors.frameworkError(exception);
        if (framework != null) return error(framework.status(), framework.code(), request);
        ApiErrors.logUnexpected(request, exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", request);
    }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new Error(Instant.now(), status, code, HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), ApiErrors.traceId(request)));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
