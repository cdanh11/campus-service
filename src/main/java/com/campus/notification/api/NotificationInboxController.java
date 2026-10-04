package com.campus.notification.api;

import java.util.*;
import com.campus.notification.application.NotificationService;
import com.campus.notification.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @SecurityRequirement(name="bearerAuth")
@RequestMapping("/api/v1/notifications")
public class NotificationInboxController {
    private final NotificationService service;
    public NotificationInboxController(NotificationService service) { this.service=service; }
    @GetMapping public InboxPage inbox(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="deliveredAt,desc") String sort) {
        var query=AdminNotificationController.search(page,size,null,status,sort,NotificationSearch.Kind.INBOX);
        var result=service.inbox(AdminNotificationController.actor(auth),query);
        return new InboxPage(result.content(),page,size,result.totalElements(),AdminNotificationController.pages(result.totalElements(),size));
    }
    @GetMapping("/{id}") public InboxItem delivery(Authentication auth,@PathVariable UUID id) { return service.delivery(AdminNotificationController.actor(auth),id); }
    @PutMapping("/{id}/read") public NotificationDelivery read(Authentication auth,@PathVariable UUID id,@Valid @RequestBody ReadRequest body) {
        return service.markRead(AdminNotificationController.actor(auth),id,body.expectedVersion());
    }
    @Schema(name="NotificationReadRequest") public record ReadRequest(@NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name="NotificationInboxPage") public record InboxPage(List<InboxItem> content,int page,int size,long totalElements,int totalPages) { }
}
