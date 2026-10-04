package com.campus.notification.domain;

import java.util.Set;

public record NotificationSearch(int page, int size, String query, String status, String sortField, boolean ascending) {
    public enum Kind { TEMPLATE, NOTICE, INBOX }
    public NotificationSearch {
        query = NotificationValues.trim(query); if (query != null && query.isEmpty()) query = null;
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE || sortField == null
                || query != null && query.codePointCount(0, query.length()) > 100) throw new IllegalArgumentException("Invalid query");
    }
    public void validate(Kind kind) {
        Set<String> fields = switch (kind) {
            case TEMPLATE -> Set.of("code", "name", "status", "createdAt", "updatedAt");
            case NOTICE -> Set.of("title", "status", "createdAt", "updatedAt");
            case INBOX -> Set.of("status", "deliveredAt", "createdAt", "updatedAt");
        };
        if (!fields.contains(sortField) || kind == Kind.INBOX && query != null) throw new IllegalArgumentException("Invalid query");
        if (status != null) switch (kind) {
            case TEMPLATE -> NotificationTemplate.Status.valueOf(status);
            case NOTICE -> Notice.Status.valueOf(status);
            case INBOX -> NotificationDelivery.Status.valueOf(status);
        }
    }
}
