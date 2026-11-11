package com.campus.academic.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ten-point conversion and four-point GPA scale shared by recorded grades and projections. */
public final class GradeScale {
    private GradeScale() { }

    public static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static String letter(double total) {
        double value = band(total);
        if (value >= 8.5) return "A";
        if (value >= 7.0) return "B";
        if (value >= 5.5) return "C";
        if (value > 4.0) return "D";
        return "F";
    }

    public static double point(double total) {
        double value = band(total);
        if (value >= 8.5) return 4.0;
        if (value >= 7.0) return 3.0;
        if (value >= 5.5) return 2.0;
        if (value > 4.0) return 1.0;
        return 0.0;
    }

    public static boolean passed(double total) {
        return band(total) > 4.0;
    }

    /** Bands are resolved at one decimal, so 8.44 stays a B while 8.45 rounds up to an A. */
    private static double band(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
