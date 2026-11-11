package com.campus.academic.api.me;

import java.util.UUID;
import com.campus.academic.application.StudentAcademicService;
import com.campus.academic.application.StudentRecordService;
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
    private final StudentRecordService records;
    public OwnAcademicController(StudentAcademicService service, StudentRecordService records) {
        this.service = service; this.records = records;
    }

    @GetMapping("/timetable") @Operation(summary = "Weekly timetable of the signed-in student")
    public StudentAcademicService.Timetable timetable(Authentication authentication, @RequestParam(required = false) UUID termId) {
        return service.timetable((UUID) authentication.getPrincipal(), termId);
    }

    @GetMapping("/enrollments") @Operation(summary = "Enrollment history of the signed-in student")
    public StudentAcademicService.Enrollments enrollments(Authentication authentication) {
        return service.enrollments((UUID) authentication.getPrincipal());
    }

    @GetMapping("/transcript") @Operation(summary = "Transcript, term GPAs and cumulative GPA of the signed-in student")
    public StudentRecordService.Transcript transcript(Authentication authentication) {
        return records.transcript((UUID) authentication.getPrincipal());
    }

    @GetMapping("/curriculum") @Operation(summary = "Curriculum flow of the signed-in student's program")
    public StudentRecordService.Curriculum curriculum(Authentication authentication) {
        return records.curriculum((UUID) authentication.getPrincipal());
    }
}
