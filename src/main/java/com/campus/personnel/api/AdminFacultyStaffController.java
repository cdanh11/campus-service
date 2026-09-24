package com.campus.personnel.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController @RequestMapping("/api/v1/admin/faculty-staff")
public class AdminFacultyStaffController {
    private final FacultyStaffManagementService service;
    public AdminFacultyStaffController(FacultyStaffManagementService service) { this.service = service; }
    @PostMapping public ResponseEntity<Response> create(Authentication auth, @Valid @RequestBody Request request) {
        FacultyStaffMember member = service.create((UUID) auth.getPrincipal(), request.personnelNumber(), request.fullName(), request.email(), request.identityUserId(), request.personnelType(), request.organizationUnitId(), request.status());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{memberId}").buildAndExpand(member.id()).toUri()).body(response(member));
    }
    @GetMapping public PageResponse list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String q, @RequestParam(required=false) String status, @RequestParam(required=false) String personnelType, @RequestParam(defaultValue="personnelNumber,asc") String sort) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.trim().codePointCount(0, q.trim().length()) > 100)) throw new InvalidQueryParameterException();
        String[] parts = sort.split(",", -1); if (parts.length != 2 || !Set.of("personnelNumber", "fullName", "email", "personnelType", "status", "createdAt", "updatedAt").contains(parts[0]) || !(parts[1].equals("asc") || parts[1].equals("desc"))) throw new InvalidQueryParameterException();
        try { var result = service.search(new FacultyStaffSearch(page, size, q == null || q.trim().isEmpty() ? null : q.trim(), status == null ? null : PersonnelStatus.valueOf(status), personnelType == null ? null : PersonnelType.valueOf(personnelType), parts[0], parts[1].equals("asc"))); return new PageResponse(result.content().stream().map(AdminFacultyStaffController::response).toList(), page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size)); }
        catch (IllegalArgumentException exception) { throw new InvalidQueryParameterException(); }
    }
    @GetMapping("/{memberId}") public Response get(@PathVariable UUID memberId) { return response(service.get(memberId)); }
    @PutMapping("/{memberId}") public Response update(Authentication auth, @PathVariable UUID memberId, @Valid @RequestBody UpdateRequest request) { return response(service.update((UUID) auth.getPrincipal(), memberId, request.personnelNumber(), request.fullName(), request.email(), request.identityUserId(), request.personnelType(), request.organizationUnitId(), request.status(), request.expectedVersion())); }
    private static Response response(FacultyStaffMember member) { return new Response(member.id(), member.personnelNumber(), member.fullName(), member.email(), member.identityUserId(), member.personnelType().name(), member.organizationUnitId(), member.status().name(), member.rowVersion(), member.createdAt(), member.updatedAt()); }
    public record Request(@NotBlank String personnelNumber,@NotBlank String fullName,String email,UUID identityUserId,@NotNull PersonnelType personnelType,@NotNull UUID organizationUnitId,PersonnelStatus status) { }
    public record UpdateRequest(@NotBlank String personnelNumber,@NotBlank String fullName,String email,UUID identityUserId,@NotNull PersonnelType personnelType,@NotNull UUID organizationUnitId,@NotNull PersonnelStatus status,@NotNull @PositiveOrZero Long expectedVersion) { }
    public record Response(UUID id,String personnelNumber,String fullName,String email,UUID identityUserId,String personnelType,UUID organizationUnitId,String status,long rowVersion,Instant createdAt,Instant updatedAt) { }
    public record PageResponse(List<Response> content,int page,int size,long totalElements,int totalPages) { }
    public static final class InvalidQueryParameterException extends RuntimeException { }
}
