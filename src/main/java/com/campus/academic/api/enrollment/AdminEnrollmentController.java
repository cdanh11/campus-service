package com.campus.academic.api.enrollment;

import java.util.Set;
import java.util.UUID;
import com.campus.academic.application.EnrollmentService;
import com.campus.academic.api.delivery.PageResponse;
import com.campus.academic.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/academic/enrollments")
public class AdminEnrollmentController {
    private final EnrollmentService service;
    public AdminEnrollmentController(EnrollmentService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Enroll an active student (ADMIN)", description = "Requires open section/offering, active term and available capacity. Duplicate membership returns 409; use PUT to re-enroll withdrawn membership.")
    public ResponseEntity<Enrollment> create(@Valid @RequestBody CreateRequest request) {
        var value = service.create(request.studentId(), request.sectionId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(value.id()).toUri()).body(value);
    }

    @GetMapping("/{id}")
    public Enrollment get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Withdraw or re-enroll (ADMIN)", description = "Requires expectedVersion. Withdrawal releases capacity and is allowed after closure. Re-enrollment rechecks eligibility and capacity. Identifiers cannot change; repeating the current status returns 409.")
    public Enrollment update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, request.status(), request.expectedVersion());
    }

    @GetMapping
    @Operation(summary = "Query enrollments (ADMIN)", description = "Page >= 0, size 1–100; studentId/sectionId/status filters; status/createdAt/updatedAt sort with stable ID tie-breaker.")
    public PageResponse<Enrollment> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) UUID studentId, @RequestParam(required = false) UUID sectionId,
                                         @RequestParam(required = false) EnrollmentStatus status,
                                         @RequestParam(defaultValue = "createdAt,desc") String sort) {
        var parts = sort.split(",", -1);
        if (parts.length != 2 || !Set.of("asc", "desc").contains(parts[1])) throw new InvalidQueryException();
        EnrollmentSearch query;
        try { query = new EnrollmentSearch(page, size, studentId, sectionId, status, parts[0], parts[1].equals("asc")); }
        catch (IllegalArgumentException exception) { throw new InvalidQueryException(); }
        var result = service.search(query);
        return new PageResponse<>(result.content(), page, size, result.totalElements(),
                (int) Math.ceil((double) result.totalElements() / size));
    }

    public record CreateRequest(@NotNull UUID studentId, @NotNull UUID sectionId) { }
    public record UpdateRequest(@NotNull EnrollmentStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    public static final class InvalidQueryException extends RuntimeException { }
}
