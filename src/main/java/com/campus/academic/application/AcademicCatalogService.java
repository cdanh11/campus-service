package com.campus.academic.application;

import java.time.Clock;
import java.sql.SQLException;
import java.util.UUID;
import com.campus.academic.domain.*;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.shared.application.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicCatalogService {
    private final AcademicProgramRepository programs;
    private final AcademicCourseRepository courses;
    private final OrganizationUnitManagementService units;
    private final Clock clock;

    public AcademicCatalogService(AcademicProgramRepository programs, AcademicCourseRepository courses,
                                  OrganizationUnitManagementService units, Clock clock) {
        this.programs = programs;
        this.courses = courses;
        this.units = units;
        this.clock = clock;
    }

    @Transactional
    public AcademicProgram createProgram(String code, String name, UUID organizationUnitId, AcademicCatalogStatus status) {
        unit(organizationUnitId);
        AcademicProgram value;
        try {
            value = AcademicProgram.create(UUID.randomUUID(), code, name, organizationUnitId,
                    status == null ? AcademicCatalogStatus.ACTIVE : status, clock.instant());
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException();
        }
        if (programs.existsByCode(value.code())) {
            throw new ProgramCodeAlreadyExistsException();
        }
        try {
            return programs.save(value);
        } catch (DataIntegrityViolationException exception) {
            throw integrityViolation(exception, new ProgramCodeAlreadyExistsException());
        }
    }

    @Transactional(readOnly = true)
    public AcademicProgram program(UUID id) {
        return programs.findById(id).orElseThrow(ProgramNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PageResult<AcademicProgram> programs(AcademicCatalogSearch search) {
        if (search.sortField().equals("title") || search.sortField().equals("credits")) {
            throw new RequestValidationException();
        }
        return programs.search(search);
    }

    @Transactional
    public AcademicProgram updateProgram(UUID id, String code, String name, UUID organizationUnitId,
                                     AcademicCatalogStatus status, long expectedVersion) {
        var old = program(id);
        unit(organizationUnitId);
        try {
            return programs.saveMutation(new AcademicProgram(id, code, name, organizationUnitId,
                    status, old.rowVersion(), old.createdAt(), clock.instant()), expectedVersion);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException();
        } catch (DataIntegrityViolationException exception) {
            throw integrityViolation(exception, new ProgramCodeAlreadyExistsException());
        }
    }

    @Transactional
    public AcademicCourse createCourse(String code, String title, int credits, UUID organizationUnitId, AcademicCatalogStatus status) {
        unit(organizationUnitId);
        AcademicCourse value;
        try {
            value = AcademicCourse.create(UUID.randomUUID(), code, title, credits, organizationUnitId,
                    status == null ? AcademicCatalogStatus.ACTIVE : status, clock.instant());
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException();
        }
        if (courses.existsByCode(value.code())) {
            throw new CourseCodeAlreadyExistsException();
        }
        try {
            return courses.save(value);
        } catch (DataIntegrityViolationException exception) {
            throw integrityViolation(exception, new CourseCodeAlreadyExistsException());
        }
    }

    @Transactional(readOnly = true)
    public AcademicCourse course(UUID id) {
        return courses.findById(id).orElseThrow(CourseNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PageResult<AcademicCourse> courses(AcademicCatalogSearch search) {
        if (search.sortField().equals("name")) {
            throw new RequestValidationException();
        }
        return courses.search(search);
    }

    @Transactional
    public AcademicCourse updateCourse(UUID id, String code, String title, int credits, UUID organizationUnitId,
                                     AcademicCatalogStatus status, long expectedVersion) {
        var old = course(id);
        unit(organizationUnitId);
        try {
            return courses.saveMutation(new AcademicCourse(id, code, title, credits, organizationUnitId,
                    status, old.rowVersion(), old.createdAt(), clock.instant()), expectedVersion);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException();
        } catch (DataIntegrityViolationException exception) {
            throw integrityViolation(exception, new CourseCodeAlreadyExistsException());
        }
    }

    private RuntimeException integrityViolation(DataIntegrityViolationException exception, RuntimeException duplicate) {
        if (exception.getMostSpecificCause() instanceof SQLException failure) {
            if ("23505".equals(failure.getSQLState())) {
                return duplicate;
            }
            if ("23503".equals(failure.getSQLState())) {
                return new OrganizationUnitUnavailableException();
            }
        }
        return exception;
    }

    private void unit(UUID id) {
        try {
            if (units.get(id).status() != OrganizationUnitStatus.ACTIVE) {
                throw new OrganizationUnitUnavailableException();
            }
        } catch (OrganizationUnitManagementService.OrganizationUnitNotFoundException exception) {
            throw new OrganizationUnitUnavailableException();
        }
    }

    public static final class RequestValidationException extends RuntimeException { }
    public static final class ProgramCodeAlreadyExistsException extends RuntimeException { }
    public static final class ProgramNotFoundException extends RuntimeException { }
    public static final class CourseCodeAlreadyExistsException extends RuntimeException { }
    public static final class CourseNotFoundException extends RuntimeException { }
    public static final class OrganizationUnitUnavailableException extends RuntimeException { }
    public static final class ConcurrentModificationException extends RuntimeException { }
}
