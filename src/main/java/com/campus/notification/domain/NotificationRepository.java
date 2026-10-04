package com.campus.notification.domain;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface NotificationRepository {
    NotificationTemplate createTemplate(NotificationTemplate value);
    Optional<NotificationTemplate> template(UUID id);
    NotificationTemplate lockTemplate(UUID id);
    NotificationTemplate updateTemplate(NotificationTemplate value, long expectedVersion);
    PageResult<NotificationTemplate> templates(NotificationSearch query);
    Notice createNotice(Notice value);
    Optional<Notice> notice(UUID id);
    Notice lockNotice(UUID id);
    Notice updateNotice(Notice value, long expectedVersion);
    PageResult<Notice> notices(NotificationSearch query);
    List<Notice> noticesByIds(Set<UUID> ids);
    NotificationDelivery createDelivery(NotificationDelivery value);
    Optional<NotificationDelivery> ownedDelivery(UUID id, UUID recipient);
    NotificationDelivery lockOwnedDelivery(UUID id, UUID recipient);
    NotificationDelivery updateDelivery(NotificationDelivery value, long expectedVersion);
    PageResult<NotificationDelivery> inbox(UUID recipient, NotificationSearch query);
}
