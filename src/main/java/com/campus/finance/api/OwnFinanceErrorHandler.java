package com.campus.finance.api;

import java.time.Instant;
import com.campus.student.application.StudentProfileNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OwnFinanceController.class)
public class OwnFinanceErrorHandler {
    @ExceptionHandler(StudentProfileNotFoundException.class)
    ResponseEntity<Error> profile(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Error(Instant.now(), 404, "STUDENT_PROFILE_NOT_FOUND", "Not Found", request.getRequestURI(), null));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
