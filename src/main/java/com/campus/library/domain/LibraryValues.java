package com.campus.library.domain;

import java.util.Locale;

public final class LibraryValues {
    private LibraryValues() { }
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
        var result = trim(value);
        if (result == null || result.codePointCount(0, result.length()) < 2
                || result.codePointCount(0, result.length()) > maximum)
            throw new IllegalArgumentException("Invalid library text");
        return result;
    }
    public static String code(String value) { return text(text(value, 32).toUpperCase(Locale.ROOT), 32); }
    public static void version(long value) {
        if (value < 0) throw new IllegalArgumentException("Invalid library version");
    }
}
