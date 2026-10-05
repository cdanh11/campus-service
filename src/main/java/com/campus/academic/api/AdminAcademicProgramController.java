package com.campus.academic.api;

import com.campus.academic.application.AcademicAdministrationService;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/academic/programs")
public class AdminAcademicProgramController {
    private final AcademicCatalogService service;
    private final AcademicAdministrationService administration;

    public AdminAcademicProgramController(AcademicCatalogService service, AcademicAdministrationService administration) {
        this.service = service; this.administration = administration;
    }

    @PostMapping
    @Operation(summary = "Create an academic program (administrative permission)")
    public ResponseEntity<Response> create(Authentication authentication, @Valid @RequestBody Request request) {
        var value = administration.createProgram((UUID) authentication.getPrincipal(), request.code(), request.name(), request.organizationUnitId(), request.status());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(value.id()).toUri()).body(out(value));
    }

    @GetMapping
    @Operation(summary = "Search academic programs (administrative permission)", description = "Zero-based page, size 1–100 (default 20), q up to 100 characters matched literally against code/name, optional ACTIVE/INACTIVE status; sort field,direction with id tie-breaker.")
    public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "code,asc") String sort) {
        var search = AcademicCatalogQuery.parse(page, size, q, status, sort,
                Set.of("code", "name", "status", "createdAt", "updatedAt"));
        var result = service.programs(search);
        return new PageResponse(result.content().stream().map(AdminAcademicProgramController::out).toList(),
                page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return out(service.program(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an academic program (administrative permission)", description = "Requires expectedVersion from the last read; stale versions and duplicate codes return 409.")
    public Response update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return out(administration.updateProgram((UUID) authentication.getPrincipal(), id, request.code(), request.name(), request.organizationUnitId(),
                request.status(), request.expectedVersion()));
    }

    private static Response out(AcademicProgram value) {
        return new Response(value.id(), value.code(), value.name(), value.organizationUnitId(),
                value.status().name(), value.rowVersion(), value.createdAt(), value.updatedAt());
    }

    public record Request(@NotBlank String code, @NotBlank String name, @NotNull UUID organizationUnitId,
                          AcademicCatalogStatus status) { }
    public record UpdateRequest(@NotBlank String code, @NotBlank String name, @NotNull UUID organizationUnitId,
                                @NotNull AcademicCatalogStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Response(UUID id, String code, String name, UUID organizationUnitId, String status,
                           long rowVersion, Instant createdAt, Instant updatedAt) { }
    public record PageResponse(List<Response> content, int page, int size, long totalElements, int totalPages) { }
}
