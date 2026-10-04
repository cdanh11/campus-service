package com.campus.event.api;

import java.util.*;
import com.campus.event.application.EventRegistrationService;
import com.campus.event.domain.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin") @SecurityRequirement(name="bearerAuth")
public class AdminEventRegistrationController {
    private final EventRegistrationService registrations;
    public AdminEventRegistrationController(EventRegistrationService registrations) { this.registrations=registrations; }
    @PostMapping("/events/{eventId}/registrations") public ResponseEntity<EventRegistration> register(Authentication auth,@PathVariable UUID eventId,@Valid @RequestBody CreateRequest body) {
        var saved=registrations.register((UUID)auth.getPrincipal(),eventId,body.studentId());
        return ResponseEntity.created(java.net.URI.create("/api/v1/admin/event-registrations/"+saved.id())).body(saved);
    }
    @GetMapping("/event-registrations/{id}") public EventRegistration get(@PathVariable UUID id) { return registrations.get(id); }
    @PutMapping("/event-registrations/{id}") public EventRegistration change(Authentication auth,@PathVariable UUID id,@Valid @RequestBody ChangeRequest body) {
        return registrations.change((UUID)auth.getPrincipal(),id,body.action(),body.expectedVersion());
    }
    @GetMapping("/event-registrations") public RegistrationPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) UUID eventId,@RequestParam(required=false) UUID studentId,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="registeredAt,desc") String sort) {
        var result=registrations.search(search(page,size,eventId,studentId,status,sort));
        return new RegistrationPage(result.content(),page,size,result.totalElements(),(int)Math.ceil((double)result.totalElements()/size));
    }
    static RegistrationSearch search(int page,int size,UUID event,UUID student,String status,String sort) {
        try {
            var parts=sort.split(",",-1); if(parts.length!=2 || !Set.of("asc","desc").contains(parts[1])) throw new IllegalArgumentException();
            return new RegistrationSearch(page,size,event,student,status==null?null:EventRegistration.Status.valueOf(status),parts[0],parts[1].equals("asc"));
        } catch(IllegalArgumentException failure) { throw new AdminEventController.InvalidQueryException(); }
    }
    @Schema(name="EventRegistrationCreate") public record CreateRequest(@NotNull UUID studentId) { }
    @Schema(name="EventRegistrationChange") public record ChangeRequest(@NotNull EventRegistrationService.Action action,
            @NotNull @PositiveOrZero @JsonDeserialize(using=EventVersionDeserializer.class) Long expectedVersion) { }
    @Schema(name="EventRegistrationPage") public record RegistrationPage(List<EventRegistration> content,int page,int size,long totalElements,int totalPages) { }
}
