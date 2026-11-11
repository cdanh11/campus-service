package com.campus.academic.api.me;

import java.sql.SQLException;
import java.time.Instant;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.academic.application.EnrollmentService;
import com.campus.academic.application.RegistrationService;
import com.campus.shared.api.ApiErrors;
import com.campus.student.application.StudentProfileNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = OwnRegistrationController.class)
public class RegistrationErrorHandler {
    @ExceptionHandler(StudentProfileNotFoundException.class)
    ResponseEntity<Error> profile(HttpServletRequest request) { return error(404, "STUDENT_PROFILE_NOT_FOUND", request); }
    @ExceptionHandler({RegistrationService.NotFoundException.class, EnrollmentService.NotFoundException.class,
            AcademicDeliveryService.ResourceNotFoundException.class})
    ResponseEntity<Error> missing(HttpServletRequest request) { return error(404, "ACADEMIC_RESOURCE_NOT_FOUND", request); }
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<Error> validation(HttpServletRequest request) { return error(400, "VALIDATION_FAILED", request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformed(HttpServletRequest request) { return error(400, "MALFORMED_REQUEST", request); }
    @ExceptionHandler(RegistrationService.WindowNotConfiguredException.class)
    ResponseEntity<Error> windowMissing(HttpServletRequest request) { return error(409, "REGISTRATION_WINDOW_NOT_CONFIGURED", request); }
    @ExceptionHandler(RegistrationService.WindowNotOpenException.class)
    ResponseEntity<Error> windowEarly(HttpServletRequest request) { return error(409, "REGISTRATION_WINDOW_NOT_OPEN", request); }
    @ExceptionHandler(RegistrationService.WindowClosedException.class)
    ResponseEntity<Error> windowLate(HttpServletRequest request) { return error(409, "REGISTRATION_WINDOW_CLOSED", request); }
    @ExceptionHandler(RegistrationService.CohortMissingException.class)
    ResponseEntity<Error> cohort(HttpServletRequest request) { return error(409, "REGISTRATION_COHORT_MISSING", request); }
    @ExceptionHandler(RegistrationService.PrerequisiteNotMetException.class)
    ResponseEntity<Error> prerequisite(HttpServletRequest request) { return error(409, "REGISTRATION_PREREQUISITE_NOT_MET", request); }
    @ExceptionHandler(RegistrationService.ScheduleConflictException.class)
    ResponseEntity<Error> schedule(HttpServletRequest request) { return error(409, "REGISTRATION_SCHEDULE_CONFLICT", request); }
    @ExceptionHandler({EnrollmentService.DuplicateException.class})
    ResponseEntity<Error> duplicate(HttpServletRequest request) { return error(409, "ACADEMIC_RESOURCE_ALREADY_EXISTS", request); }
    @ExceptionHandler(EnrollmentService.CapacityExceededException.class)
    ResponseEntity<Error> full(HttpServletRequest request) { return error(409, "SECTION_CAPACITY_EXCEEDED", request); }
    @ExceptionHandler(EnrollmentService.InvalidStateException.class)
    ResponseEntity<Error> state(HttpServletRequest request) { return error(409, "INVALID_ACADEMIC_STATE", request); }
    @ExceptionHandler(EnrollmentService.StudentUnavailableException.class)
    ResponseEntity<Error> reference(HttpServletRequest request) { return error(409, "ACADEMIC_REFERENCE_UNAVAILABLE", request); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Error> concurrent(HttpServletRequest request) { return error(409, "CONCURRENT_MODIFICATION", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> integrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        if (exception.getMostSpecificCause() instanceof SQLException failure) {
            if ("23505".equals(failure.getSQLState())) return duplicate(request);
            if ("23503".equals(failure.getSQLState())) return reference(request);
        }
        throw exception;
    }
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
