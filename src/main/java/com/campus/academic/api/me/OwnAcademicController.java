package com.campus.academic.api.me;

import java.util.UUID;
import com.campus.academic.application.StudentAcademicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "bearerAuth")
public class OwnAcademicController {
    private final StudentAcademicService service;
    public OwnAcademicController(StudentAcademicService service) { this.service = service; }

    @GetMapping("/timetable") @Operation(summary = "Weekly timetable of the signed-in student")
    public StudentAcademicService.Timetable timetable(Authentication authentication, @RequestParam(required = false) UUID termId) {
        return service.timetable((UUID) authentication.getPrincipal(), termId);
    }

    @GetMapping("/enrollments") @Operation(summary = "Enrollment history of the signed-in student")
    public StudentAcademicService.Enrollments enrollments(Authentication authentication) {
        return service.enrollments((UUID) authentication.getPrincipal());
    }
}
