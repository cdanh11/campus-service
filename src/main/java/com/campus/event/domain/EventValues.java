package com.campus.event.domain;

import java.util.Locale;

/** Normalization local to Event; exactly the six approved boundary whitespace characters. */
public final class EventValues {
    private EventValues() { }
    public static String trim(String value) {
        if (value == null) return null;
        int first = 0, last = value.length();
        while (first < last && whitespace(value.charAt(first))) first++;
        while (last > first && whitespace(value.charAt(last - 1))) last--;
        return value.substring(first, last);
    }
    private static boolean whitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000b' || c == '\f';
    }
    public static String text(String value, int maximum) {
        var normalized = trim(value);
        if (normalized == null || normalized.codePointCount(0, normalized.length()) < 2
                || normalized.codePointCount(0, normalized.length()) > maximum)
            throw new IllegalArgumentException("Invalid event text");
        return normalized;
    }
    public static String code(String value) { return text(text(value, 32).toUpperCase(Locale.ROOT), 32); }
    public static void version(long version) {
        if (version < 0) throw new IllegalArgumentException("Invalid event version");
    }
}
