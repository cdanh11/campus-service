package com.campus.dormitory.api;

import java.util.*;
import com.campus.dormitory.application.DormitoryInventoryService;
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

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/admin/dormitory")
public class AdminDormitoryInventoryController {
    private final DormitoryInventoryService service;
    public AdminDormitoryInventoryController(DormitoryInventoryService service) { this.service = service; }

    @PostMapping("/{resource:buildings|rooms|beds}")
    @Operation(summary = "Create Dormitory inventory (ADMIN)", description = "ACTIVE initially; rooms/beds require immutable parentId and active ancestors.")
    public ResponseEntity<InventoryItem> create(Authentication authentication, @PathVariable String resource, @Valid @RequestBody CreateRequest body) {
        var value = service.create((UUID) authentication.getPrincipal(), kind(resource), body.parentId(), body.code(), body.name());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(value.id()).toUri()).body(value);
    }

    @GetMapping("/{resource:buildings|rooms|beds}/{id}")
    public InventoryItem get(@PathVariable String resource, @PathVariable UUID id) { return service.get(kind(resource), id); }

    @PutMapping("/{resource:buildings|rooms|beds}/{id}")
    @Operation(summary = "Update Dormitory inventory (ADMIN)", description = "expectedVersion required; parents immutable; deactivate active children before their parent.")
    public InventoryItem update(Authentication authentication, @PathVariable String resource, @PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        return service.update((UUID) authentication.getPrincipal(), kind(resource), id, body.code(), body.name(), body.status(), body.expectedVersion());
    }

    @GetMapping("/{resource:buildings|rooms|beds}")
    public PageResponse list(@PathVariable String resource, @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String q,
                             @RequestParam(required = false) String status, @RequestParam(required = false) UUID parentId,
                             @RequestParam(defaultValue = "code,asc") String sort) {
        InventorySearch query;
        try {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !Set.of("asc", "desc").contains(parts[1])) throw new IllegalArgumentException();
            query = new InventorySearch(page, size, q, status == null ? null : InventoryStatus.valueOf(status),
                    parentId, parts[0], parts[1].equals("asc"));
        } catch (IllegalArgumentException failure) { throw new DormitoryInventoryService.InvalidQueryException(); }
        var result = service.search(kind(resource), query);
        return new PageResponse(result.content(), page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }

    private InventoryKind kind(String resource) {
        return switch (resource) { case "buildings" -> InventoryKind.BUILDING; case "rooms" -> InventoryKind.ROOM; case "beds" -> InventoryKind.BED;
            default -> throw new DormitoryInventoryService.NotFoundException(); };
    }
    @Schema(name = "DormitoryInventoryCreateRequest")
    public record CreateRequest(@NotBlank String code, @NotBlank String name, UUID parentId) { }
    @Schema(name = "DormitoryInventoryUpdateRequest")
    public record UpdateRequest(@NotBlank String code, @NotBlank String name, @NotNull InventoryStatus status,
                                @NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name = "DormitoryInventoryPage")
    public record PageResponse(List<InventoryItem> content, int page, int size, long totalElements, int totalPages) { }
}
