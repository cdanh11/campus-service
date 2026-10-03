package com.campus.dormitory.api;

import java.util.*;
import com.campus.dormitory.application.*;
import com.campus.dormitory.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController @SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/admin/dormitory/assignments")
public class AdminAccommodationAssignmentController {
    private final AccommodationAssignmentService service;
    public AdminAccommodationAssignmentController(AccommodationAssignmentService service) { this.service = service; }
    @PostMapping @Operation(summary = "Assign a current bed (ADMIN)")
    public ResponseEntity<AccommodationAssignment> create(Authentication authentication, @Valid @RequestBody CreateRequest body) {
        var value = service.create((UUID) authentication.getPrincipal(), body.studentId(), body.bedId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(value.id()).toUri()).body(value);
    }
    @GetMapping("/{id}") public AccommodationAssignment get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping("/{id}") @Operation(summary = "Release accommodation (ADMIN)", description = "RELEASED is terminal; expectedVersion required; references immutable.")
    public AccommodationAssignment release(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ReleaseRequest body) {
        if (body.status() != AssignmentStatus.RELEASED) throw new AccommodationAssignmentService.InvalidAssignmentStateException();
        return service.release((UUID) authentication.getPrincipal(), id, body.expectedVersion());
    }
    @GetMapping
    public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) UUID studentId, @RequestParam(required = false) UUID bedId,
                             @RequestParam(required = false) String status, @RequestParam(defaultValue = "assignedAt,desc") String sort) {
        AssignmentSearch query;
        try {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !Set.of("asc", "desc").contains(parts[1])) throw new IllegalArgumentException();
            query = new AssignmentSearch(page, size, studentId, bedId, status == null ? null : AssignmentStatus.valueOf(status), parts[0], parts[1].equals("asc"));
        } catch (IllegalArgumentException failure) { throw new DormitoryInventoryService.InvalidQueryException(); }
        var result = service.search(query);
        return new PageResponse(result.content(), page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }
    @Schema(name = "AccommodationAssignmentCreateRequest")
    public record CreateRequest(@NotNull UUID studentId, @NotNull UUID bedId) { }
    @Schema(name = "AccommodationAssignmentReleaseRequest")
    public record ReleaseRequest(@NotNull AssignmentStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name = "AccommodationAssignmentPage")
    public record PageResponse(List<AccommodationAssignment> content, int page, int size, long totalElements, int totalPages) { }
}
