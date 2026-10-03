package com.campus.academic.api.delivery;

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

@RestController
@RequestMapping("/api/v1/admin/academic/sections")
public class AdminClassSectionController {
    private final AcademicDeliveryService service;
    public AdminClassSectionController(AcademicDeliveryService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Create section (ADMIN)", description = "New resources start in DRAFT; lifecycle transitions require PUT and expectedVersion.")
    public ResponseEntity<ClassSection> create(@Valid @RequestBody CreateRequest request) {
        var value = service.createSection(request.offeringId(), request.code(), request.capacity(), request.facultyId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(value.id()).toUri()).body(value);
    }

    @GetMapping("/{id}")
    public ClassSection get(@PathVariable UUID id) { return service.section(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Update section (ADMIN)", description = "Requires expectedVersion; stale version, invalid lifecycle or unavailable references return 409. Parent identifiers are immutable.")
    public ClassSection update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.updateSection(id, request.code(), request.capacity(), request.facultyId(), request.status(), request.expectedVersion());
    }

    @GetMapping
    @Operation(summary = "Query sections (ADMIN)", description = "Zero-based page; size 1–100; allowlisted field,asc/desc sort with ID tie-breaker.")
    public PageResponse<ClassSection> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String q, @RequestParam(required = false) UUID offeringId, @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "code,asc") String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !java.util.Set.of("asc", "desc").contains(parts[1])) throw new AcademicDeliveryService.InvalidQueryException();
        AcademicDeliverySearch query;
        try { query = new AcademicDeliverySearch(page, size, q, status, null, null, offeringId, parts[0], parts[1].equals("asc")); }
        catch (IllegalArgumentException exception) { throw new AcademicDeliveryService.InvalidQueryException(); }
        var result = service.sections(query);
        return PageResponse.from(result, page, size);
    }

    public record CreateRequest(@NotNull UUID offeringId, @NotBlank String code, @Min(1) int capacity, UUID facultyId) { }
    public record UpdateRequest(@NotBlank String code, @Min(1) int capacity, UUID facultyId, @NotNull AcademicDeliveryStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
}
