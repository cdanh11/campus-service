package com.campus.event.api;

import java.util.UUID;
import com.campus.event.application.EventRegistrationService;
import com.campus.event.domain.EventRegistration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") @SecurityRequirement(name="bearerAuth")
public class OwnEventRegistrationController {
    private final EventRegistrationService registrations;
    public OwnEventRegistrationController(EventRegistrationService registrations) { this.registrations=registrations; }
    @PostMapping("/events/{eventId}/registrations") public ResponseEntity<EventRegistration> register(Authentication auth,@PathVariable UUID eventId) {
        var saved=registrations.registerOwn((UUID)auth.getPrincipal(),eventId);
        return ResponseEntity.created(java.net.URI.create("/api/v1/event-registrations/"+saved.id())).body(saved);
    }
    @GetMapping("/event-registrations/{id}") public EventRegistration own(Authentication auth,@PathVariable UUID id) { return registrations.own((UUID)auth.getPrincipal(),id); }
    @PutMapping("/event-registrations/{id}") public EventRegistration change(Authentication auth,@PathVariable UUID id,@Valid @RequestBody AdminEventRegistrationController.ChangeRequest body) {
        return registrations.changeOwn((UUID)auth.getPrincipal(),id,body.action(),body.expectedVersion());
    }
    @GetMapping("/event-registrations") public AdminEventRegistrationController.RegistrationPage list(Authentication auth,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) UUID eventId,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="registeredAt,desc") String sort) {
        var result=registrations.ownSearch((UUID)auth.getPrincipal(),AdminEventRegistrationController.search(page,size,eventId,null,status,sort));
        return new AdminEventRegistrationController.RegistrationPage(result.content(),page,size,result.totalElements(),(int)Math.ceil((double)result.totalElements()/size));
    }
}
