package com.campus.academic.domain;

/** Component weights are fixed per course group and snapshotted on every recorded grade. */
public enum GradeScheme {
    GENERAL(0.1, 0.4, 0.5, 0.0, false),
    MAJOR_PROJECT(0.1, 0.4, 0.0, 0.5, true),
    MAJOR_FINAL(0.1, 0.3, 0.6, 0.0, false);

    private final double attendanceWeight;
    private final double midtermWeight;
    private final double finalWeight;
    private final double projectWeight;
    private final boolean projectRequired;

    GradeScheme(double attendanceWeight, double midtermWeight, double finalWeight, double projectWeight, boolean projectRequired) {
        this.attendanceWeight = attendanceWeight;
        this.midtermWeight = midtermWeight;
        this.finalWeight = finalWeight;
        this.projectWeight = projectWeight;
        this.projectRequired = projectRequired;
    }

    public static GradeScheme of(CourseGroup group, boolean hasFinalProject) {
        if (group == CourseGroup.MAJOR || group == CourseGroup.SPECIALIZED)
            return hasFinalProject ? MAJOR_PROJECT : MAJOR_FINAL;
        return GENERAL;
    }

    public double attendanceWeight() { return attendanceWeight; }
    public double midtermWeight() { return midtermWeight; }
    public double finalWeight() { return finalWeight; }
    public double projectWeight() { return projectWeight; }
    public boolean projectRequired() { return projectRequired; }
}
