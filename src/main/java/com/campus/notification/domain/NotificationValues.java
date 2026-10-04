package com.campus.notification.domain;

import java.util.Locale;

public final class NotificationValues {
    private NotificationValues() { }
    public static String text(String value, int max) {
        String result = trim(value);
        if (result == null || result.codePointCount(0, result.length()) < 2 || result.codePointCount(0, result.length()) > max)
            throw new IllegalArgumentException("Invalid notification text");
        return result;
    }
    public static String code(String value) { return text(text(value, 32).toUpperCase(Locale.ROOT), 32); }
    public static String trim(String value) {
        if (value == null) return null;
        int start = 0, end = value.length();
        while (start < end && whitespace(value.charAt(start))) start++;
        while (end > start && whitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }
    private static boolean whitespace(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000b' || c == '\f'; }
    public static void version(long value) { if (value < 0) throw new IllegalArgumentException("Invalid version"); }
}
