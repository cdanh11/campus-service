package com.campus.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationDelivery(UUID id, UUID noticeId, UUID recipientId, Status status, long rowVersion,
                                   Instant deliveredAt, Instant readAt, Instant createdAt, Instant updatedAt) {
    public enum Status { UNREAD, READ }
    public NotificationDelivery {
        Objects.requireNonNull(id); Objects.requireNonNull(noticeId); Objects.requireNonNull(recipientId); Objects.requireNonNull(status);
        Objects.requireNonNull(deliveredAt); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt); NotificationValues.version(rowVersion);
        if ((status == Status.READ) != (readAt != null) || readAt != null && readAt.isBefore(deliveredAt))
            throw new IllegalArgumentException("Invalid delivery shape");
    }
    public NotificationDelivery read(Instant now) {
        if (status == Status.READ) return this;
        return new NotificationDelivery(id, noticeId, recipientId, Status.READ, rowVersion, deliveredAt, now, createdAt, now);
    }
}
