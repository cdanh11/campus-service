package com.campus.academic.api;

import java.time.Instant;
import java.util.*;
import com.campus.academic.application.AcademicCatalogService;
import com.campus.academic.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/academic/courses")
public class AdminAcademicCourseController {
    private final AcademicCatalogService service;

    public AdminAcademicCourseController(AcademicCatalogService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create an academic course (ADMIN)")
    public ResponseEntity<Response> create(@Valid @RequestBody Request request) {
        var value = service.createCourse(request.code(), request.title(), request.credits(), request.organizationUnitId(), request.status());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(value.id()).toUri()).body(out(value));
    }

    @GetMapping
    @Operation(summary = "Search academic courses (ADMIN)", description = "Zero-based page, size 1–100 (default 20), q up to 100 characters matched literally against code/title, optional ACTIVE/INACTIVE status; sort field,direction with id tie-breaker.")
    public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "code,asc") String sort) {
        var search = AcademicCatalogQuery.parse(page, size, q, status, sort,
                Set.of("code", "title", "credits", "status", "createdAt", "updatedAt"));
        var result = service.courses(search);
        return new PageResponse(result.content().stream().map(AdminAcademicCourseController::out).toList(),
                page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return out(service.course(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an academic course (ADMIN)", description = "Requires expectedVersion from the last read; stale versions and duplicate codes return 409.")
    public Response update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return out(service.updateCourse(id, request.code(), request.title(), request.credits(), request.organizationUnitId(),
                request.status(), request.expectedVersion()));
    }

    private static Response out(AcademicCourse value) {
        return new Response(value.id(), value.code(), value.title(), value.credits(), value.organizationUnitId(),
                value.status().name(), value.rowVersion(), value.createdAt(), value.updatedAt());
    }

    public record Request(@NotBlank String code, @NotBlank String title, @NotNull @Min(1) @Max(30) Integer credits, @NotNull UUID organizationUnitId,
                          AcademicCatalogStatus status) { }
    public record UpdateRequest(@NotBlank String code, @NotBlank String title, @NotNull @Min(1) @Max(30) Integer credits, @NotNull UUID organizationUnitId,
                                @NotNull AcademicCatalogStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Response(UUID id, String code, String title, int credits, UUID organizationUnitId, String status,
                           long rowVersion, Instant createdAt, Instant updatedAt) { }
    public record PageResponse(List<Response> content, int page, int size, long totalElements, int totalPages) { }
}
