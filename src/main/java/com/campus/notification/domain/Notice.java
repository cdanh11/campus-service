package com.campus.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notice(UUID id, UUID templateId, String title, String body, Status status, long rowVersion,
                     Instant publishedAt, Instant createdAt, Instant updatedAt) {
    public enum Status { DRAFT, PUBLISHED }
    public Notice {
        Objects.requireNonNull(id); Objects.requireNonNull(templateId); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt); NotificationValues.version(rowVersion);
        title = NotificationValues.text(title, 160); body = NotificationValues.text(body, 4000);
        if ((status == Status.PUBLISHED) != (publishedAt != null)) throw new IllegalArgumentException("Invalid publish shape");
    }
    public Notice edit(String title, String body, Instant now) {
        if (status != Status.DRAFT) throw new IllegalStateException("Notice is immutable after publication");
        return new Notice(id, templateId, title, body, status, rowVersion, null, createdAt, now);
    }
    public Notice publish(Instant now) {
        if (status != Status.DRAFT) throw new IllegalStateException("Already published");
        return new Notice(id, templateId, title, body, Status.PUBLISHED, rowVersion, now, createdAt, now);
    }
}
