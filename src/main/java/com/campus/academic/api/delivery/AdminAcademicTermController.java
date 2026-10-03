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
@RequestMapping("/api/v1/admin/academic/terms")
public class AdminAcademicTermController {
    private final AcademicDeliveryService service;
    public AdminAcademicTermController(AcademicDeliveryService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Create term (ADMIN)", description = "New resources start in PLANNED; lifecycle transitions require PUT and expectedVersion.")
    public ResponseEntity<AcademicTerm> create(@Valid @RequestBody CreateRequest request) {
        var value = service.createTerm(request.code(), request.name(), request.startDate(), request.endDate());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(value.id()).toUri()).body(value);
    }

    @GetMapping("/{id}")
    public AcademicTerm get(@PathVariable UUID id) { return service.term(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Update term (ADMIN)", description = "Requires expectedVersion; stale version, invalid lifecycle or unavailable references return 409. Parent identifiers are immutable.")
    public AcademicTerm update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.updateTerm(id, request.code(), request.name(), request.startDate(), request.endDate(), request.status(), request.expectedVersion());
    }

    @GetMapping
    @Operation(summary = "Query terms (ADMIN)", description = "Zero-based page; size 1–100; allowlisted field,asc/desc sort with ID tie-breaker.")
    public PageResponse<AcademicTerm> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "code,asc") String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !java.util.Set.of("asc", "desc").contains(parts[1])) throw new AcademicDeliveryService.InvalidQueryException();
        AcademicDeliverySearch query;
        try { query = new AcademicDeliverySearch(page, size, q, status, null, null, null, parts[0], parts[1].equals("asc")); }
        catch (IllegalArgumentException exception) { throw new AcademicDeliveryService.InvalidQueryException(); }
        var result = service.terms(query);
        return PageResponse.from(result, page, size);
    }

    public record CreateRequest(@NotBlank String code, @NotBlank String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate) { }
    public record UpdateRequest(@NotBlank String code, @NotBlank String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotNull AcademicTermStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
}
