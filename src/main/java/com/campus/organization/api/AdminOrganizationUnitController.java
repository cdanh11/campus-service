package com.campus.organization.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.OrganizationUnit;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.organization.domain.OrganizationUnitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/organization-units")
public class AdminOrganizationUnitController {
    private final OrganizationUnitManagementService service;
    public AdminOrganizationUnitController(OrganizationUnitManagementService service) { this.service = service; }
    @PostMapping public ResponseEntity<Response> create(Authentication authentication, @Valid @RequestBody Request request) {
        OrganizationUnit unit = service.create((UUID) authentication.getPrincipal(), request.code(), request.name(), request.unitType(), request.status());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{unitId}").buildAndExpand(unit.id()).toUri()).body(response(unit));
    }
    @GetMapping public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "code,asc") String sort) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.trim().codePointCount(0, q.trim().length()) > 100)) throw new InvalidQueryParameterException();
        String[] parts = sort.split(",", -1); if (parts.length != 2 || !Set.of("code", "name", "status", "createdAt", "updatedAt").contains(parts[0]) || !(parts[1].equals("asc") || parts[1].equals("desc"))) throw new InvalidQueryParameterException();
        OrganizationUnitStatus requestedStatus; try { requestedStatus = status == null ? null : OrganizationUnitStatus.valueOf(status); } catch (IllegalArgumentException exception) { throw new InvalidQueryParameterException(); }
        var result = service.search(new com.campus.organization.domain.OrganizationUnitSearch(page, size, q == null || q.trim().isEmpty() ? null : q.trim(), requestedStatus, parts[0], parts[1].equals("asc")));
        return new PageResponse(result.content().stream().map(AdminOrganizationUnitController::response).toList(), page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }
    @GetMapping("/{unitId}") public Response get(@PathVariable UUID unitId) { return response(service.get(unitId)); }
    @PutMapping("/{unitId}") public Response update(Authentication authentication, @PathVariable UUID unitId, @Valid @RequestBody UpdateRequest request) { return response(service.update((UUID) authentication.getPrincipal(), unitId, request.code(), request.name(), request.unitType(), request.status(), request.expectedVersion())); }
    private static Response response(OrganizationUnit unit) { return new Response(unit.id(), unit.code(), unit.name(), unit.unitType().name(), unit.status().name(), unit.rowVersion(), unit.createdAt(), unit.updatedAt()); }
    public record Request(@NotBlank String code, @NotBlank String name, @NotNull OrganizationUnitType unitType, OrganizationUnitStatus status) { }
    public record UpdateRequest(@NotBlank String code, @NotBlank String name, @NotNull OrganizationUnitType unitType, @NotNull OrganizationUnitStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Response(UUID id, String code, String name, String unitType, String status, long rowVersion, Instant createdAt, Instant updatedAt) { }
    public record PageResponse(List<Response> content, int page, int size, long totalElements, int totalPages) { }
    public static final class InvalidQueryParameterException extends RuntimeException { }
}
