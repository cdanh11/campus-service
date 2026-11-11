package com.campus.student.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.campus.student.application.StudentManagementService;
import com.campus.student.application.StudentProfileService;
import com.campus.student.domain.Student;
import com.campus.student.domain.StudentSearch;
import com.campus.student.domain.StudentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/students")
public class AdminStudentController {
    private final StudentManagementService service;
    private final StudentProfileService profiles;

    public AdminStudentController(StudentManagementService service, StudentProfileService profiles) {
        this.service = service;
        this.profiles = profiles;
    }

    @PostMapping
    public ResponseEntity<Response> create(Authentication authentication, @Valid @RequestBody Request request) {
        Student value = service.create((UUID) authentication.getPrincipal(), request.studentNumber(), request.fullName(),
                request.email(), request.identityUserId(), request.organizationUnitId(), request.status());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{studentId}")
                .buildAndExpand(value.id()).toUri()).body(out(value));
    }

    @GetMapping
    public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "studentNumber,asc") String sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || (q != null && q.trim().codePointCount(0, q.trim().length()) > 100)) throw new InvalidQueryParameterException();
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !Set.of("studentNumber", "fullName", "email", "status", "createdAt", "updatedAt").contains(parts[0])
                || !(parts[1].equals("asc") || parts[1].equals("desc"))) throw new InvalidQueryParameterException();
        StudentStatus requested;
        try { requested = status == null ? null : StudentStatus.valueOf(status); }
        catch (IllegalArgumentException exception) { throw new InvalidQueryParameterException(); }
        var result = service.search(new StudentSearch(page, size, q == null || q.trim().isEmpty() ? null : q.trim(),
                requested, parts[0], parts[1].equals("asc")));
        return new PageResponse(result.content().stream().map(AdminStudentController::out).toList(), page, size,
                result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }

    @GetMapping("/{studentId}")
    public Response get(@PathVariable UUID studentId) { return out(service.get(studentId)); }

    @GetMapping("/lookup")
    public List<Response> lookup(@RequestParam List<UUID> ids) {
        if (ids.size() > 100) throw new InvalidQueryParameterException();
        return service.lookup(ids).stream().map(AdminStudentController::out).toList();
    }

    @PutMapping("/{studentId}")
    public Response update(Authentication authentication, @PathVariable UUID studentId, @Valid @RequestBody UpdateRequest request) {
        return out(service.update((UUID) authentication.getPrincipal(), studentId, request.studentNumber(), request.fullName(),
                request.email(), request.identityUserId(), request.organizationUnitId(), request.status(), request.expectedVersion()));
    }

    @GetMapping("/{studentId}/profile")
    public StudentProfileService.StudentProfile academicProfile(@PathVariable UUID studentId) {
        return profiles.academicProfile(studentId);
    }

    @PutMapping("/{studentId}/profile")
    public StudentProfileService.StudentProfile updateAcademicProfile(Authentication authentication, @PathVariable UUID studentId,
                                                                     @Valid @RequestBody AcademicProfileRequest request) {
        return profiles.updateAcademicProfile((UUID) authentication.getPrincipal(), studentId, request.cohort(),
                request.programId(), request.expectedVersion());
    }

    private static Response out(Student value) {
        return new Response(value.id(), value.studentNumber(), value.fullName(), value.email(), value.identityUserId(),
                value.organizationUnitId(), value.status().name(), value.rowVersion(), value.createdAt(), value.updatedAt());
    }

    public record Request(@NotBlank String studentNumber, @NotBlank String fullName, String email, UUID identityUserId,
                          @NotNull UUID organizationUnitId, StudentStatus status) { }
    public record UpdateRequest(@NotBlank String studentNumber, @NotBlank String fullName, String email, UUID identityUserId,
                                @NotNull UUID organizationUnitId, @NotNull StudentStatus status,
                                @NotNull @PositiveOrZero Long expectedVersion) { }
    public record AcademicProfileRequest(@Pattern(regexp = "^K[0-9]{2}$") String cohort, UUID programId,
                                         @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Response(UUID id, String studentNumber, String fullName, String email, UUID identityUserId,
                           UUID organizationUnitId, String status, long rowVersion, Instant createdAt, Instant updatedAt) { }
    public record PageResponse(List<Response> content, int page, int size, long totalElements, int totalPages) { }
    public static final class InvalidQueryParameterException extends RuntimeException { }
}
