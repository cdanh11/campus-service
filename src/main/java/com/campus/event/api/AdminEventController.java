package com.campus.event.api;

import java.time.Instant;
import java.util.*;
import com.campus.event.application.EventCatalogService;
import com.campus.event.domain.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController @RequestMapping("/api/v1/admin/events") @SecurityRequirement(name="bearerAuth")
public class AdminEventController {
    private final EventCatalogService events;
    public AdminEventController(EventCatalogService events) { this.events=events; }
    @PostMapping public ResponseEntity<CampusEvent> create(Authentication auth,@Valid @RequestBody CreateRequest body) {
        var saved=events.create((UUID)auth.getPrincipal(),body.code(),body.title(),body.description(),body.startsAt(),body.endsAt(),body.capacity());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri()).body(saved);
    }
    @GetMapping("/{id}") public CampusEvent get(@PathVariable UUID id) { return events.get(id); }
    @PutMapping("/{id}") public CampusEvent update(Authentication auth,@PathVariable UUID id,@Valid @RequestBody UpdateRequest body) {
        return events.update((UUID)auth.getPrincipal(),id,body.code(),body.title(),body.description(),body.startsAt(),body.endsAt(),body.capacity(),body.status(),body.expectedVersion());
    }
    @GetMapping public EventPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(defaultValue="startsAt,asc") String sort) {
        return page(events,page,size,q,status,sort);
    }
    static EventPage page(EventCatalogService events,int page,int size,String q,String status,String sort) {
        EventSearch search;
        try {
            var parts=sort.split(",",-1);
            if (parts.length!=2 || !Set.of("asc","desc").contains(parts[1])) throw new IllegalArgumentException();
            search=new EventSearch(page,size,q,status==null?null:CampusEvent.Status.valueOf(status),parts[0],parts[1].equals("asc"));
        } catch (IllegalArgumentException failure) { throw new InvalidQueryException(); }
        var result=events.search(search);
        return new EventPage(result.content(),page,size,result.totalElements(),(int)Math.ceil((double)result.totalElements()/size));
    }
    public static final class InvalidQueryException extends RuntimeException { }
    @Schema(name="CampusEventCreate") public record CreateRequest(@NotBlank String code,@NotBlank String title,@NotBlank String description,
            @NotNull Instant startsAt,@NotNull Instant endsAt,@NotNull @Positive @JsonDeserialize(using=EventCapacityDeserializer.class) Integer capacity) { }
    @Schema(name="CampusEventUpdate") public record UpdateRequest(@NotBlank String code,@NotBlank String title,@NotBlank String description,
            @NotNull Instant startsAt,@NotNull Instant endsAt,@NotNull @Positive @JsonDeserialize(using=EventCapacityDeserializer.class) Integer capacity,
            @NotNull CampusEvent.Status status,@NotNull @PositiveOrZero @JsonDeserialize(using=EventVersionDeserializer.class) Long expectedVersion) { }
    @Schema(name="CampusEventPage") public record EventPage(List<CampusEvent> content,int page,int size,long totalElements,int totalPages) { }
}
