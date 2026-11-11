package com.campus.academic.domain;

public enum GradeClassification {
    XUAT_SAC, GIOI, KHA, TRUNG_BINH, YEU, KEM;

    public static GradeClassification of(double gpa) {
        double value = GradeScale.round(gpa);
        if (value >= 3.6) return XUAT_SAC;
        if (value >= 3.2) return GIOI;
        if (value >= 2.5) return KHA;
        if (value >= 2.0) return TRUNG_BINH;
        if (value >= 1.0) return YEU;
        return KEM;
    }
}
