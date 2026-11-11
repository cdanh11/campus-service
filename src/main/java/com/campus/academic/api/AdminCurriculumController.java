package com.campus.academic.api;

import java.util.*;
import com.campus.academic.application.AcademicAdministrationService;
import com.campus.academic.application.CurriculumService;
import com.campus.academic.domain.CurriculumCourse;
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
@RequestMapping("/api/v1/admin/academic/curriculum")
public class AdminCurriculumController {
    private final CurriculumService service;
    private final AcademicAdministrationService administration;

    public AdminCurriculumController(CurriculumService service, AcademicAdministrationService administration) {
        this.service = service;
        this.administration = administration;
    }

    @PostMapping
    @Operation(summary = "Add a course to a program curriculum (administrative permission)",
            description = "A program can list each course once. Semester index is 1–12; the prerequisite must be an existing course.")
    public ResponseEntity<CurriculumCourse> create(Authentication authentication, @Valid @RequestBody Request request) {
        var value = administration.createCurriculum((UUID) authentication.getPrincipal(), request.programId(),
                request.courseId(), request.semesterIndex(), request.prerequisiteCourseId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(value.id()).toUri()).body(value);
    }

    @GetMapping
    public PageResponse list(@RequestParam UUID programId, @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "100") int size) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidQueryException();
        var values = service.views(programId);
        int from = Math.min(page * size, values.size());
        int to = Math.min(from + size, values.size());
        return new PageResponse(values.subList(from, to), page, size, values.size(),
                (int) Math.ceil((double) values.size() / size));
    }

    @GetMapping("/{id}")
    public CurriculumCourse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Update a curriculum entry (administrative permission)", description = "Requires expectedVersion; stale versions return 409.")
    public CurriculumCourse update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return administration.updateCurriculum((UUID) authentication.getPrincipal(), id, request.semesterIndex(),
                request.prerequisiteCourseId(), request.expectedVersion());
    }

    public record Request(@NotNull UUID programId, @NotNull UUID courseId, @NotNull @Min(1) @Max(12) Integer semesterIndex,
                          UUID prerequisiteCourseId) { }
    public record UpdateRequest(@NotNull @Min(1) @Max(12) Integer semesterIndex, UUID prerequisiteCourseId,
                                @NotNull @PositiveOrZero Long expectedVersion) { }
    public record PageResponse(List<CurriculumService.EntryView> content, int page, int size, long totalElements,
                               int totalPages) { }
    public static final class InvalidQueryException extends RuntimeException { }
}
