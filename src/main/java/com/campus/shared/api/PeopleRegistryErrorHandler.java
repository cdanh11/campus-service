package com.campus.shared.api;

import java.time.Instant;

import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.api.AdminOrganizationUnitController;
import com.campus.student.api.AdminStudentController;
import com.campus.personnel.api.AdminFacultyStaffController;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.student.application.StudentManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = {"com.campus.organization.api", "com.campus.student.api", "com.campus.personnel.api"})
public class PeopleRegistryErrorHandler {
    @ExceptionHandler({AdminOrganizationUnitController.InvalidQueryParameterException.class, AdminStudentController.InvalidQueryParameterException.class, AdminFacultyStaffController.InvalidQueryParameterException.class})
    ResponseEntity<ErrorResponse> invalidQuery(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER", "Query parameter is invalid", request); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> argumentValidation(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> malformed(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request is invalid", request); }
    @ExceptionHandler({OrganizationUnitManagementService.RequestValidationException.class, StudentManagementService.RequestValidationException.class, FacultyStaffManagementService.RequestValidationException.class})
    ResponseEntity<ErrorResponse> validation(HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request); }
    @ExceptionHandler(OrganizationUnitManagementService.OrganizationUnitNotFoundException.class)
    ResponseEntity<ErrorResponse> organizationNotFound(HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, "ORGANIZATION_UNIT_NOT_FOUND", "Organization unit was not found", request); }
    @ExceptionHandler(OrganizationUnitManagementService.OrganizationUnitCodeAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> organizationDuplicate(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "ORGANIZATION_UNIT_CODE_ALREADY_EXISTS", "Organization unit code is already in use", request); }
    @ExceptionHandler({OrganizationUnitManagementService.ConcurrentOrganizationUnitModificationException.class, StudentManagementService.ConcurrentStudentModificationException.class, FacultyStaffManagementService.ConcurrentFacultyStaffModificationException.class})
    ResponseEntity<ErrorResponse> concurrent(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Record was modified concurrently", request); }
    @ExceptionHandler(StudentManagementService.StudentNotFoundException.class)
    ResponseEntity<ErrorResponse> studentNotFound(HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found", request); }
    @ExceptionHandler(StudentManagementService.StudentNumberAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> studentDuplicate(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "STUDENT_NUMBER_ALREADY_EXISTS", "Student number is already in use", request); }
    @ExceptionHandler(FacultyStaffManagementService.FacultyStaffNotFoundException.class)
    ResponseEntity<ErrorResponse> personnelNotFound(HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, "FACULTY_STAFF_NOT_FOUND", "Faculty or staff member was not found", request); }
    @ExceptionHandler(FacultyStaffManagementService.PersonnelNumberAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> personnelDuplicate(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "PERSONNEL_NUMBER_ALREADY_EXISTS", "Personnel number is already in use", request); }
    @ExceptionHandler({StudentManagementService.OrganizationUnitUnavailableException.class, FacultyStaffManagementService.OrganizationUnitUnavailableException.class})
    ResponseEntity<ErrorResponse> unavailableUnit(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "ORGANIZATION_UNIT_UNAVAILABLE", "Organization unit is unavailable", request); }
    @ExceptionHandler({StudentManagementService.IdentityUserUnavailableException.class, FacultyStaffManagementService.IdentityUserUnavailableException.class})
    ResponseEntity<ErrorResponse> unavailableIdentity(HttpServletRequest request) { return error(HttpStatus.CONFLICT, "IDENTITY_USER_UNAVAILABLE", "Identity user is unavailable", request); }
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) { return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI(), null)); }
    public record ErrorResponse(Instant timestamp, int status, String code, String message, String path, String traceId) { }
}
