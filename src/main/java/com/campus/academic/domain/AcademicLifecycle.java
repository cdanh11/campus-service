package com.campus.academic.domain;

public final class AcademicLifecycle {
    private AcademicLifecycle() { }

    public static void term(AcademicTermStatus from, AcademicTermStatus to) {
        if (from == to || from == AcademicTermStatus.PLANNED && (to == AcademicTermStatus.ACTIVE || to == AcademicTermStatus.CANCELLED)
                || from == AcademicTermStatus.ACTIVE && to == AcademicTermStatus.CLOSED) return;
        throw new InvalidTransitionException();
    }

    public static void delivery(AcademicDeliveryStatus from, AcademicDeliveryStatus to) {
        if (from == to || from == AcademicDeliveryStatus.DRAFT && (to == AcademicDeliveryStatus.OPEN || to == AcademicDeliveryStatus.CANCELLED)
                || from == AcademicDeliveryStatus.OPEN && to == AcademicDeliveryStatus.CLOSED) return;
        throw new InvalidTransitionException();
    }

    public static final class InvalidTransitionException extends RuntimeException { }
}
