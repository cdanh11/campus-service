package com.campus.academic.api;

import java.time.Instant;
import java.util.*;
import com.campus.academic.application.AcademicAdministrationService;
import com.campus.academic.application.AcademicGradeService;
import com.campus.academic.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/academic")
public class AdminAcademicGradeController {
    private final AcademicGradeService service;
    private final AcademicAdministrationService administration;

    public AdminAcademicGradeController(AcademicGradeService service, AcademicAdministrationService administration) {
        this.service = service;
        this.administration = administration;
    }

    @PostMapping("/enrollments/{enrollmentId}/grade")
    @Operation(summary = "Record a grade for an enrollment (administrative permission)",
            description = "Weights are derived from the course group and final-project flag. Scores are on the ten-point scale; a duplicate grade returns 409.")
    public ResponseEntity<Response> create(Authentication authentication, @PathVariable UUID enrollmentId,
                                           @Valid @RequestBody Request request) {
        var value = administration.createGrade((UUID) authentication.getPrincipal(), enrollmentId,
                new AcademicGradeService.GradeScores(request.attendanceScore(), request.midtermScore(),
                        request.finalScore(), request.projectScore()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/admin/academic/grades/{id}").buildAndExpand(value.id()).toUri()).body(out(value));
    }

    @PutMapping("/enrollments/{enrollmentId}/grade")
    @Operation(summary = "Correct a recorded grade (administrative permission)", description = "Requires expectedVersion from the last read; stale versions return 409.")
    public Response update(Authentication authentication, @PathVariable UUID enrollmentId,
                           @Valid @RequestBody UpdateRequest request) {
        return out(administration.updateGrade((UUID) authentication.getPrincipal(), enrollmentId,
                new AcademicGradeService.GradeScores(request.attendanceScore(), request.midtermScore(),
                        request.finalScore(), request.projectScore()), request.expectedVersion()));
    }

    @GetMapping("/grades")
    public PageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) UUID enrollmentId,
                             @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        var parts = sort.split(",", -1);
        if (parts.length != 2 || !Set.of("asc", "desc").contains(parts[1])) throw new InvalidQueryException();
        GradeSearch query;
        try { query = new GradeSearch(page, size, enrollmentId, parts[0], parts[1].equals("asc")); }
        catch (IllegalArgumentException exception) { throw new InvalidQueryException(); }
        var result = service.search(query);
        return new PageResponse(result.content().stream().map(AdminAcademicGradeController::out).toList(),
                page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }

    @GetMapping("/grades/{id}")
    public Response get(@PathVariable UUID id) { return out(service.get(id)); }

    @GetMapping("/enrollments/{enrollmentId}/grade")
    public Response byEnrollment(@PathVariable UUID enrollmentId) { return out(service.byEnrollment(enrollmentId)); }

    private static Response out(AcademicGrade value) {
        return new Response(value.id(), value.enrollmentId(), value.scheme().name(), value.attendanceScore(),
                value.midtermScore(), value.finalScore(), value.projectScore(), value.totalScore(), value.letterGrade(),
                value.gradePoint(), value.passed(), value.recordedBy(), value.rowVersion(), value.createdAt(), value.updatedAt());
    }

    public record Request(@NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double attendanceScore,
                          @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double midtermScore,
                          @DecimalMin("0.0") @DecimalMax("10.0") Double finalScore,
                          @DecimalMin("0.0") @DecimalMax("10.0") Double projectScore) { }
    public record UpdateRequest(@NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double attendanceScore,
                                @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double midtermScore,
                                @DecimalMin("0.0") @DecimalMax("10.0") Double finalScore,
                                @DecimalMin("0.0") @DecimalMax("10.0") Double projectScore,
                                @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Response(UUID id, UUID enrollmentId, String scheme, double attendanceScore, double midtermScore,
                           Double finalScore, Double projectScore, double totalScore, String letterGrade, double gradePoint,
                           boolean passed, UUID recordedBy, long rowVersion, Instant createdAt, Instant updatedAt) { }
    public record PageResponse(List<Response> content, int page, int size, long totalElements, int totalPages) { }
    public static final class InvalidQueryException extends RuntimeException { }
}
