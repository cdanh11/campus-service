package com.campus.student.api;

import java.util.UUID;
import com.campus.student.application.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "bearerAuth")
public class OwnStudentController {
    private final StudentProfileService service;

    public OwnStudentController(StudentProfileService service) {
        this.service = service;
    }

    @GetMapping("/student") @Operation(summary = "Profile of the student linked to the signed-in account")
    public StudentProfileService.StudentProfile profile(Authentication authentication) {
        return service.profile((UUID) authentication.getPrincipal());
    }

    @PutMapping("/student") @Operation(summary = "Update the contact fields the student owns")
    public StudentProfileService.StudentProfile update(Authentication authentication, @Valid @RequestBody UpdateRequest body) {
        return service.updateContact((UUID) authentication.getPrincipal(), body.email(), body.phone(), body.address(), body.expectedVersion());    }

    @Schema(name = "StudentContactUpdateRequest")
    public record UpdateRequest(@Email @Size(max = 320) String email, @Size(max = 32) String phone,
                                @Size(max = 500) String address, @NotNull @PositiveOrZero Long expectedVersion) {
        public UpdateRequest {
            email = email == null ? null : email.trim();
            phone = phone == null ? null : phone.trim();
            address = address == null ? null : address.trim();
        }
    }
}
