package com.campus.event.api;

import java.util.UUID;
import com.campus.event.application.EventCatalogService;
import com.campus.event.domain.CampusEvent;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/events") @SecurityRequirement(name="bearerAuth")
public class EventCatalogController {
    private final EventCatalogService events;
    public EventCatalogController(EventCatalogService events) { this.events=events; }
    @GetMapping("/{id}") public CampusEvent get(@PathVariable UUID id) { return events.get(id); }
    @GetMapping public AdminEventController.EventPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(defaultValue="startsAt,asc") String sort) {
        return AdminEventController.page(events,page,size,q,status,sort);
    }
}
