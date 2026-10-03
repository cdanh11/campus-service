package com.campus.academic.api;

import java.time.Instant;
import org.springframework.dao.OptimisticLockingFailureException;
import com.campus.academic.application.AcademicCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = {AdminAcademicProgramController.class, AdminAcademicCourseController.class})
public class AcademicCatalogErrorHandler {
    @ExceptionHandler(com.campus.academic.application.AcademicAudit.UnavailableException.class)
    ResponseEntity<Error> auditFailure(HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_WRITE_FAILED", request);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, AcademicCatalogService.RequestValidationException.class})
    ResponseEntity<Error> validation(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformed(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", request);
    }

    @ExceptionHandler(AcademicCatalogQuery.InvalidQueryParameterException.class)
    ResponseEntity<Error> query(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER", request);
    }

    @ExceptionHandler({AcademicCatalogService.ConcurrentModificationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Error> stale(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", request);
    }

    @ExceptionHandler(AcademicCatalogService.ProgramCodeAlreadyExistsException.class)
    ResponseEntity<Error> programDuplicate(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PROGRAM_CODE_ALREADY_EXISTS", request);
    }

    @ExceptionHandler(AcademicCatalogService.CourseCodeAlreadyExistsException.class)
    ResponseEntity<Error> courseDuplicate(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "COURSE_CODE_ALREADY_EXISTS", request);
    }

    @ExceptionHandler(AcademicCatalogService.ProgramNotFoundException.class)
    ResponseEntity<Error> programMissing(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ACADEMIC_PROGRAM_NOT_FOUND", request);
    }

    @ExceptionHandler(AcademicCatalogService.CourseNotFoundException.class)
    ResponseEntity<Error> courseMissing(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ACADEMIC_COURSE_NOT_FOUND", request);
    }

    @ExceptionHandler(AcademicCatalogService.OrganizationUnitUnavailableException.class)
    ResponseEntity<Error> unit(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ORGANIZATION_UNIT_UNAVAILABLE", request);
    }

    private ResponseEntity<Error> error(HttpStatus status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(Instant.now(), status.value(), code, status.getReasonPhrase(), request.getRequestURI(), null));
    }

    public record Error(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
