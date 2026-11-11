package com.campus.academic.api.me;

import java.time.Instant;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.academic.application.StudentRecordService;
import com.campus.shared.api.ApiErrors;
import com.campus.student.application.StudentProfileNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OwnAcademicController.class)
public class OwnAcademicErrorHandler {
    @ExceptionHandler(StudentProfileNotFoundException.class)
    ResponseEntity<Error> profile(HttpServletRequest request) { return error(404, "STUDENT_PROFILE_NOT_FOUND", request); }
    @ExceptionHandler(AcademicDeliveryService.ResourceNotFoundException.class)
    ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "ACADEMIC_RESOURCE_NOT_FOUND", request); }
    @ExceptionHandler(StudentRecordService.CurriculumNotAvailableException.class)
    ResponseEntity<Error> curriculumMissing(HttpServletRequest request) { return error(404, "ACADEMIC_CURRICULUM_NOT_AVAILABLE", request); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Error> validation(HttpServletRequest request) { return error(400, "INVALID_QUERY_PARAMETER", request); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected(Exception exception, HttpServletRequest request) {
        var framework = ApiErrors.frameworkError(exception);
        if (framework != null) return error(framework.status(), framework.code(), request);
        ApiErrors.logUnexpected(request, exception);
        return error(500, "INTERNAL_ERROR", request);
    }
    private ResponseEntity<Error> error(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status, code,
                HttpStatus.valueOf(status).getReasonPhrase(), request.getRequestURI(), ApiErrors.traceId(request)));
    }
    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
