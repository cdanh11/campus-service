package com.campus.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class FinanceValues {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999999");
    private FinanceValues() { }
    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("Invalid VND amount");
        try { return value.setScale(0, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException failure) { throw new IllegalArgumentException("VND requires integral amount"); }
    }
    public static String code(String value) { return text(text(value, 32).toUpperCase(Locale.ROOT), 32); }
    public static String text(String value, int max) {
        String result = trim(value);
        if (result == null || result.codePointCount(0, result.length()) < 2 || result.codePointCount(0, result.length()) > max)
            throw new IllegalArgumentException("Invalid Finance text");
        return result;
    }
    public static String trim(String value) {
        if (value == null) return null;
        int start = 0, end = value.length();
        while (start < end && whitespace(value.charAt(start))) start++;
        while (end > start && whitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }
    private static boolean whitespace(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000b' || c == '\f'; }
}
