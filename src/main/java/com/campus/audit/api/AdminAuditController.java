package com.campus.audit.api;

import java.time.Instant;
import java.util.*;
import com.campus.audit.application.AuditViewingService;
import com.campus.shared.application.audit.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audits")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditController {
    private final AuditViewingService audits;
    public AdminAuditController(AuditViewingService audits) { this.audits = audits; }
    @GetMapping("/{source}/{id}") public AuditView get(@PathVariable AuditSource source, @PathVariable UUID id) { return audits.get(source, id); }
    @GetMapping("/{source}") public AuditPage list(@PathVariable AuditSource source,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID targetId, @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String resource, @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant until,
            @RequestParam(defaultValue = "occurredAt,desc") String sort) {
        if (!Set.of("occurredAt,asc", "occurredAt,desc").contains(sort)) throw new IllegalArgumentException("Invalid audit sort");
        var result = audits.search(source, new AuditSearch(page, size, targetId, actorId, resource, action, from, until, sort.endsWith("asc")));
        return new AuditPage(result.content(), page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }
    @Schema(name = "AuditViewingPage") public record AuditPage(List<AuditView> content, int page, int size, long totalElements, int totalPages) { }
}
