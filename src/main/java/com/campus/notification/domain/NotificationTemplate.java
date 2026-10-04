package com.campus.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationTemplate(UUID id, String code, String name, String title, String body, Status status,
                                   long rowVersion, Instant createdAt, Instant updatedAt) {
    public enum Status { ACTIVE, INACTIVE }
    public NotificationTemplate {
        Objects.requireNonNull(id); Objects.requireNonNull(status); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        code = NotificationValues.code(code); name = NotificationValues.text(name, 160);
        title = NotificationValues.text(title, 160); body = NotificationValues.text(body, 4000); NotificationValues.version(rowVersion);
    }
}
