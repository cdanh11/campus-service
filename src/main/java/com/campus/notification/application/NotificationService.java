package com.campus.notification.application;

import java.time.Clock;
import java.util.*;
import com.campus.identity.application.IdentityUserDirectory;
import com.campus.notification.domain.*;
import com.campus.shared.application.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class NotificationService {
    private final NotificationRepository repository;
    private final IdentityUserDirectory accounts;
    private final NotificationAudit audit;
    private final Clock clock;
    public NotificationService(NotificationRepository repository, IdentityUserDirectory accounts, NotificationAudit audit, Clock clock) {
        this.repository = repository; this.accounts = accounts; this.audit = audit; this.clock = clock;
    }
    public NotificationTemplate createTemplate(UUID actor, String code, String name, String title, String body) {
        actor(actor); var now = clock.instant();
        var saved = repository.createTemplate(new NotificationTemplate(UUID.randomUUID(), code, name, title, body,
                NotificationTemplate.Status.ACTIVE, 0, now, now));
        event(actor, NotificationAudit.Resource.TEMPLATE, saved.id(), "CREATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public NotificationTemplate updateTemplate(UUID actor, UUID id, String code, String name, String title, String body,
                                               NotificationTemplate.Status status, long expectedVersion) {
        actor(actor); var old = repository.lockTemplate(id); version(old.rowVersion(), expectedVersion);
        var saved = repository.updateTemplate(new NotificationTemplate(id, code, name, title, body, status, old.rowVersion(),
                old.createdAt(), clock.instant()), expectedVersion);
        event(actor, NotificationAudit.Resource.TEMPLATE, id, "UPDATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public Notice createNotice(UUID actor, UUID templateId) {
        actor(actor); if (templateId == null) throw new IllegalArgumentException("Template required");
        var template = repository.lockTemplate(templateId);
        if (template.status() != NotificationTemplate.Status.ACTIVE) throw new ReferenceUnavailableException();
        var now = clock.instant();
        var saved = repository.createNotice(new Notice(UUID.randomUUID(), templateId, template.title(), template.body(), Notice.Status.DRAFT, 0, null, now, now));
        event(actor, NotificationAudit.Resource.NOTICE, saved.id(), "CREATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public Notice editNotice(UUID actor, UUID id, String title, String body, long expectedVersion) {
        actor(actor); var old = repository.lockNotice(id); version(old.rowVersion(), expectedVersion); draft(old);
        var saved = repository.updateNotice(old.edit(title, body, clock.instant()), expectedVersion);
        event(actor, NotificationAudit.Resource.NOTICE, id, "UPDATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public Notice publish(UUID actor, UUID id, List<UUID> recipients, long expectedVersion) {
        actor(actor); NotificationValues.version(expectedVersion);
        if (recipients == null || recipients.isEmpty() || recipients.size() > 100 || recipients.stream().anyMatch(Objects::isNull)
                || new HashSet<>(recipients).size() != recipients.size()) throw new IllegalArgumentException("Invalid recipient batch");
        var old = repository.lockNotice(id); version(old.rowVersion(), expectedVersion); draft(old);
        for (UUID recipient : recipients) if (!accounts.isActive(recipient)) throw new ReferenceUnavailableException();
        var now = clock.instant();
        var saved = repository.updateNotice(old.publish(now), expectedVersion);
        for (UUID recipient : recipients) repository.createDelivery(new NotificationDelivery(UUID.randomUUID(), id, recipient,
                NotificationDelivery.Status.UNREAD, 0, saved.publishedAt(), null, saved.publishedAt(), saved.publishedAt()));
        event(actor, NotificationAudit.Resource.NOTICE, id, "PUBLISHED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public NotificationDelivery markRead(UUID actor, UUID id, long expectedVersion) {
        actor(actor); NotificationValues.version(expectedVersion);
        var old = repository.lockOwnedDelivery(id, actor);
        if (old.status() == NotificationDelivery.Status.READ) return old;
        version(old.rowVersion(), expectedVersion);
        var saved = repository.updateDelivery(old.read(clock.instant()), expectedVersion);
        event(actor, NotificationAudit.Resource.DELIVERY, id, "READ", saved.rowVersion(), saved.status().name()); return saved;
    }
    @Transactional(readOnly = true) public NotificationTemplate template(UUID id) { return repository.template(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public Notice notice(UUID id) { return repository.notice(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public InboxItem delivery(UUID actor, UUID id) {
        actor(actor); var delivery = repository.ownedDelivery(id, actor).orElseThrow(NotFoundException::new);
        var notice = notice(delivery.noticeId()); return new InboxItem(delivery, notice.title(), notice.body());
    }
    @Transactional(readOnly = true) public PageResult<NotificationTemplate> templates(NotificationSearch query) { query.validate(NotificationSearch.Kind.TEMPLATE); return repository.templates(query); }
    @Transactional(readOnly = true) public PageResult<Notice> notices(NotificationSearch query) { query.validate(NotificationSearch.Kind.NOTICE); return repository.notices(query); }
    @Transactional(readOnly = true) public PageResult<InboxItem> inbox(UUID actor, NotificationSearch query) {
        actor(actor); query.validate(NotificationSearch.Kind.INBOX);
        var page = repository.inbox(actor, query);
        var ids = new HashSet<UUID>(); for (var delivery : page.content()) ids.add(delivery.noticeId());
        var content = new HashMap<UUID, Notice>();
        if (!ids.isEmpty()) for (var notice : repository.noticesByIds(ids)) content.put(notice.id(), notice);
        return new PageResult<>(page.content().stream().map(delivery -> {
            var notice = content.get(delivery.noticeId()); return new InboxItem(delivery, notice.title(), notice.body());
        }).toList(), page.totalElements());
    }
    private void event(UUID actor, NotificationAudit.Resource resource, UUID target, String action, long version, String status) { audit.record(actor, resource, target, action, version, status, clock.instant()); }
    private void actor(UUID actor) { if (actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    private void version(long actual, long expected) { NotificationValues.version(expected); if (actual != expected) throw new StaleVersionException(); }
    private void draft(Notice notice) { if (notice.status() != Notice.Status.DRAFT) throw new InvalidStateException(); }
    public static final class NotFoundException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class ReferenceUnavailableException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
}
