package com.campus.academic.api;

import java.time.Instant;
import com.campus.academic.application.AcademicAudit;
import com.campus.academic.application.AcademicGradeService;
import com.campus.academic.application.CurriculumService;
import com.campus.academic.domain.AcademicGrade;
import com.campus.academic.domain.CurriculumCourse;
import com.campus.shared.api.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {AdminAcademicGradeController.class, AdminCurriculumController.class})
public class AcademicRecordsErrorHandler {
    @ExceptionHandler(AcademicAudit.UnavailableException.class)
    ResponseEntity<Error> auditFailure(HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_WRITE_FAILED", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, AcademicGradeService.RequestValidationException.class,
            CurriculumService.RequestValidationException.class, AcademicGrade.InvalidGradeException.class,
            CurriculumCourse.InvalidCurriculumException.class})
    ResponseEntity<Error> validation(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformed(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", request);
    }

    @ExceptionHandler({AdminAcademicGradeController.InvalidQueryException.class, AdminCurriculumController.InvalidQueryException.class})
    ResponseEntity<Error> query(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER", request);
    }

    @ExceptionHandler({AcademicGradeService.StaleVersionException.class, CurriculumService.StaleVersionException.class,
            OptimisticLockingFailureException.class})
    ResponseEntity<Error> stale(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", request);
    }

    @ExceptionHandler(AcademicGradeService.DuplicateException.class)
    ResponseEntity<Error> gradeDuplicate(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "GRADE_ALREADY_RECORDED", request);
    }

    @ExceptionHandler(CurriculumService.DuplicateException.class)
    ResponseEntity<Error> curriculumDuplicate(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CURRICULUM_ENTRY_ALREADY_EXISTS", request);
    }

    @ExceptionHandler(AcademicGradeService.NotFoundException.class)
    ResponseEntity<Error> gradeMissing(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ACADEMIC_GRADE_NOT_FOUND", request);
    }

    @ExceptionHandler(CurriculumService.NotFoundException.class)
    ResponseEntity<Error> curriculumMissing(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CURRICULUM_ENTRY_NOT_FOUND", request);
    }

    @ExceptionHandler(CurriculumService.ProgramNotFoundException.class)
    ResponseEntity<Error> programMissing(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ACADEMIC_PROGRAM_NOT_FOUND", request);
    }

    @ExceptionHandler({CurriculumService.CourseNotFoundException.class, CurriculumService.PrerequisiteNotFoundException.class})
    ResponseEntity<Error> courseMissing(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ACADEMIC_COURSE_NOT_FOUND", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected(Exception exception, HttpServletRequest request) {
        var framework = ApiErrors.frameworkError(exception);
        if (framework != null) return error(HttpStatus.valueOf(framework.status()), framework.code(), request);
        ApiErrors.logUnexpected(request, exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request);
    }

    private ResponseEntity<Error> error(HttpStatus status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status.value(), code,
                status.getReasonPhrase(), request.getRequestURI(), ApiErrors.traceId(request)));
    }

    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
