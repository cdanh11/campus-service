package com.campus.student.api;

import java.util.UUID;
import com.campus.student.application.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "bearerAuth")
public class OwnStudentController {
    private final StudentProfileService service;
    public OwnStudentController(StudentProfileService service) { this.service = service; }

    @GetMapping("/student") @Operation(summary = "Profile of the student linked to the signed-in account")
    public StudentProfileService.StudentProfile profile(Authentication authentication) {
        return service.profile((UUID) authentication.getPrincipal());
    }
}
