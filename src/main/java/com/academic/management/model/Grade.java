package com.academic.management.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The letter grades awarded by the {@link com.academic.management.util.GradingPolicy}.
 *
 * <p>Each constant carries the lowest percentage that earns it, so this
 * enum is the single source of truth for the scale and the policy simply
 * walks it in descending order.
 */
public enum Grade {

    A_PLUS("A+", 90.0, 5.00),
    A("A", 80.0, 4.00),
    B_PLUS("B+", 70.0, 3.50),
    B("B", 60.0, 3.00),
    C("C", 50.0, 2.00),
    D("D", 40.0, 1.00),
    F("F", 0.0, 0.00);

    private static final double MAX_PERCENTAGE = 100.0;

    /** Highest band first, so the policy can stop at the first match. */
    private static final List<Grade> BY_THRESHOLD = Collections.unmodifiableList(
            Arrays.stream(values())
                    .sorted(Comparator.comparingDouble(Grade::getMinimumPercentage).reversed())
                    .toList());

    private final String code;
    private final double minimumPercentage;
    private final double gradePoint;

    Grade(String code, double minimumPercentage, double gradePoint) {
        this.code = code;
        this.minimumPercentage = minimumPercentage;
        this.gradePoint = gradePoint;
    }

    /** What is displayed and stored in the {@code results.grade} column. */
    public String getCode() {
        return code;
    }

    public double getMinimumPercentage() {
        return minimumPercentage;
    }

    public double getGradePoint() {
        return gradePoint;
    }

    /**
     * Highest percentage that still earns this grade, derived from the
     * band above it: 89.99 for A, 79.99 for B+, and so on. The top band
     * runs to 100.
     */
    public double getMaximumPercentage() {
        int index = BY_THRESHOLD.indexOf(this);
        if (index <= 0) {
            return MAX_PERCENTAGE;
        }
        // The band above us sets our ceiling; stay just below its floor.
        return round2(BY_THRESHOLD.get(index - 1).getMinimumPercentage() - 0.01);
    }

    /** Grade letters ordered from highest threshold to lowest. */
    public static List<Grade> orderedByThreshold() {
        return BY_THRESHOLD;
    }

    /** Reverse lookup from the stored code, for example {@code "B+"}. */
    public static Grade fromCode(String code) {
        if (code != null) {
            String trimmed = code.trim();
            for (Grade grade : values()) {
                if (grade.code.equalsIgnoreCase(trimmed)) {
                    return grade;
                }
            }
        }
        return F;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public String toString() {
        return code;
    }
}
