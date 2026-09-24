package com.campus.organization.api;

import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/organization-units")
public class AdminOrganizationUnitController {
    private final OrganizationUnitManagementService service;
    public AdminOrganizationUnitController(OrganizationUnitManagementService service) { this.service = service; }
    @PostMapping public ResponseEntity<Response> create(@Valid @RequestBody Request request) {
        OrganizationUnit unit = service.create(request.code(), request.name(), request.unitType(), request.status());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{unitId}").buildAndExpand(unit.id()).toUri()).body(response(unit));
    }
    @GetMapping public List<Response> list() { return service.list().stream().map(AdminOrganizationUnitController::response).toList(); }
    @GetMapping("/{unitId}") public Response get(@PathVariable UUID unitId) { return response(service.get(unitId)); }
    @PutMapping("/{unitId}") public Response update(@PathVariable UUID unitId, @Valid @RequestBody UpdateRequest request) { return response(service.update(unitId, request.code(), request.name(), request.unitType(), request.status(), request.expectedVersion())); }
    private static Response response(OrganizationUnit unit) { return new Response(unit.id(), unit.code(), unit.name(), unit.unitType().name(), unit.status().name(), unit.rowVersion(), unit.createdAt(), unit.updatedAt()); }
    public record Request(@NotBlank String code, @NotBlank String name, @NotNull OrganizationUnitType unitType, OrganizationUnitStatus status) { }
    public record UpdateRequest(@NotBlank String code, @NotBlank String name, @NotNull OrganizationUnitType unitType, @NotNull OrganizationUnitStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Response(UUID id, String code, String name, String unitType, String status, long rowVersion, Instant createdAt, Instant updatedAt) { }
}
