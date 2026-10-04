package com.campus.notification.api;

import java.util.*;
import com.campus.notification.application.NotificationService;
import com.campus.notification.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController @SecurityRequirement(name="bearerAuth")
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {
    private final NotificationService service;
    public AdminNotificationController(NotificationService service) { this.service=service; }
    @PostMapping("/templates") public ResponseEntity<NotificationTemplate> createTemplate(Authentication auth,@Valid @RequestBody TemplateCreate body) {
        var saved=service.createTemplate(actor(auth),body.code(),body.name(),body.title(),body.body());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri()).body(saved);
    }
    @GetMapping("/templates/{id}") public NotificationTemplate template(@PathVariable UUID id) { return service.template(id); }
    @PutMapping("/templates/{id}") public NotificationTemplate updateTemplate(Authentication auth,@PathVariable UUID id,@Valid @RequestBody TemplateUpdate body) {
        return service.updateTemplate(actor(auth),id,body.code(),body.name(),body.title(),body.body(),body.status(),body.expectedVersion());
    }
    @GetMapping("/templates") public TemplatePage templates(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(defaultValue="code,asc") String sort) {
        var result=service.templates(search(page,size,q,status,sort,NotificationSearch.Kind.TEMPLATE));
        return new TemplatePage(result.content(),page,size,result.totalElements(),pages(result.totalElements(),size));
    }
    @PostMapping("/notices") public ResponseEntity<Notice> createNotice(Authentication auth,@Valid @RequestBody NoticeCreate body) {
        var saved=service.createNotice(actor(auth),body.templateId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri()).body(saved);
    }
    @GetMapping("/notices/{id}") public Notice notice(@PathVariable UUID id) { return service.notice(id); }
    @PutMapping("/notices/{id}") public Notice edit(Authentication auth,@PathVariable UUID id,@Valid @RequestBody NoticeEdit body) {
        return service.editNotice(actor(auth),id,body.title(),body.body(),body.expectedVersion());
    }
    @PostMapping("/notices/{id}/publish") public Notice publish(Authentication auth,@PathVariable UUID id,@Valid @RequestBody NoticePublish body) {
        return service.publish(actor(auth),id,body.recipientIds(),body.expectedVersion());
    }
    @GetMapping("/notices") public NoticePage notices(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(defaultValue="createdAt,desc") String sort) {
        var result=service.notices(search(page,size,q,status,sort,NotificationSearch.Kind.NOTICE));
        return new NoticePage(result.content(),page,size,result.totalElements(),pages(result.totalElements(),size));
    }
    static UUID actor(Authentication auth) { return (UUID)auth.getPrincipal(); }
    static int pages(long total,int size) { return (int)Math.ceil((double)total/size); }
    static NotificationSearch search(int page,int size,String q,String status,String sort,NotificationSearch.Kind kind) {
        try {
            var parts=sort.split(",",-1); if(parts.length!=2 || !Set.of("asc","desc").contains(parts[1])) throw new IllegalArgumentException();
            var search=new NotificationSearch(page,size,q,status,parts[0],parts[1].equals("asc")); search.validate(kind); return search;
        } catch (IllegalArgumentException failure) { throw new InvalidQueryException(); }
    }
    public static final class InvalidQueryException extends RuntimeException { }
    @Schema(name="NotificationTemplateCreate") public record TemplateCreate(@NotBlank String code,@NotBlank String name,@NotBlank String title,@NotBlank String body) { }
    @Schema(name="NotificationTemplateUpdate") public record TemplateUpdate(@NotBlank String code,@NotBlank String name,@NotBlank String title,
            @NotBlank String body,@NotNull NotificationTemplate.Status status,@NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name="NotificationNoticeCreate") public record NoticeCreate(@NotNull UUID templateId) { }
    @Schema(name="NotificationNoticeEdit") public record NoticeEdit(@NotBlank String title,@NotBlank String body,@NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name="NotificationNoticePublish") public record NoticePublish(@NotNull @Size(min=1,max=100) List<@NotNull UUID> recipientIds,@NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name="NotificationTemplatePage") public record TemplatePage(List<NotificationTemplate> content,int page,int size,long totalElements,int totalPages) { }
    @Schema(name="NotificationNoticePage") public record NoticePage(List<Notice> content,int page,int size,long totalElements,int totalPages) { }
}
