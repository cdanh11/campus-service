package com.campus.academic.api.delivery;

import com.campus.academic.application.AcademicAdministrationService;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.time.LocalDate;
import java.util.UUID;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.academic.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/academic/offerings")
public class AdminCourseOfferingController {
    private final AcademicDeliveryService service;
    private final AcademicAdministrationService administration;
    public AdminCourseOfferingController(AcademicDeliveryService service, AcademicAdministrationService administration) { this.service = service; this.administration = administration; }

    @PostMapping
    @Operation(summary = "Create offering (administrative permission)", description = "New resources start in DRAFT; lifecycle transitions require PUT and expectedVersion.")
    public ResponseEntity<CourseOffering> create(Authentication authentication, @Valid @RequestBody CreateRequest request) {
        var value = administration.createOffering((UUID) authentication.getPrincipal(), request.termId(), request.courseId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(value.id()).toUri()).body(value);
    }

    @GetMapping("/{id}")
    public CourseOffering get(@PathVariable UUID id) { return service.offering(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Update offering (administrative permission)", description = "Requires expectedVersion; stale version, invalid lifecycle or unavailable references return 409. Parent identifiers are immutable.")
    public CourseOffering update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return administration.updateOffering((UUID) authentication.getPrincipal(), id, request.status(), request.expectedVersion());
    }

    @GetMapping
    @Operation(summary = "Query offerings (administrative permission)", description = "Zero-based page; size 1–100; allowlisted field,asc/desc sort with ID tie-breaker.")
    public PageResponse<CourseOffering> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) UUID termId, @RequestParam(required = false) UUID courseId, @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !java.util.Set.of("asc", "desc").contains(parts[1])) throw new AcademicDeliveryService.InvalidQueryException();
        AcademicDeliverySearch query;
        try { query = new AcademicDeliverySearch(page, size, null, status, termId, courseId, null, parts[0], parts[1].equals("asc")); }
        catch (IllegalArgumentException exception) { throw new AcademicDeliveryService.InvalidQueryException(); }
        var result = service.offerings(query);
        return PageResponse.from(result, page, size);
    }

    public record CreateRequest(@NotNull UUID termId, @NotNull UUID courseId) { }
    public record UpdateRequest(@NotNull AcademicDeliveryStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
}
