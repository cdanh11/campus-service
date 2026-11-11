package com.campus.academic.api.me;

import java.util.UUID;
import com.campus.academic.application.RegistrationService;
import com.campus.academic.domain.Enrollment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/me/registration")
@SecurityRequirement(name = "bearerAuth")
public class OwnRegistrationController {
    private final RegistrationService registrations;

    public OwnRegistrationController(RegistrationService registrations) { this.registrations = registrations; }

    @GetMapping
    @Operation(summary = "Registration window, cohort slot and available sections of the signed-in student",
            description = "Cohorts enter in two-day slots ordered oldest first (K21 and earlier together). Each section carries a blocked reason when it cannot be chosen.")
    public RegistrationService.Registration registration(Authentication authentication) {
        return registrations.registration((UUID) authentication.getPrincipal());
    }

    @PostMapping
    @Operation(summary = "Register the signed-in student in a section",
            description = "Requires the term, offering and section to be open and the student's cohort slot to be current. Rejects duplicates, full sections, unmet prerequisites and timetable conflicts.")
    public ResponseEntity<Enrollment> register(Authentication authentication, @Valid @RequestBody RegisterRequest request) {
        var value = registrations.register((UUID) authentication.getPrincipal(), request.sectionId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(value.id()).toUri()).body(value);
    }

    @DeleteMapping("/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw the signed-in student's own enrollment",
            description = "Only enrollments created through self-service can be withdrawn here, and only while the student's registration slot is open.")
    public void withdraw(Authentication authentication, @PathVariable UUID enrollmentId) {
        registrations.withdraw((UUID) authentication.getPrincipal(), enrollmentId);
    }

    public record RegisterRequest(@NotNull UUID sectionId) { }
}
