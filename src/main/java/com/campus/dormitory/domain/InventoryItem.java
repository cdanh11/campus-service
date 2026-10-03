package com.campus.dormitory.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record InventoryItem(UUID id, InventoryKind kind, UUID parentId, String code, String name,
                            InventoryStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public InventoryItem {
        if (id == null || kind == null || status == null || createdAt == null || updatedAt == null || rowVersion < 0
                || (kind == InventoryKind.BUILDING ? parentId != null : parentId == null)) {
            throw new IllegalArgumentException("Invalid inventory item");
        }
        code = text(text(code, 32).toUpperCase(Locale.ROOT), 32);
        name = text(name, 160);
    }

    public static String trim(String value) {
        if (value == null) return null;
        int start = 0, end = value.length();
        while (start < end && whitespace(value.charAt(start))) start++;
        while (end > start && whitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static String text(String value, int max) {
        String result = trim(value);
        if (result == null || result.codePointCount(0, result.length()) < 2 || result.codePointCount(0, result.length()) > max) {
            throw new IllegalArgumentException("Invalid inventory text");
        }
        return result;
    }

    private static boolean whitespace(char value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\u000b' || value == '\f';
    }
}
