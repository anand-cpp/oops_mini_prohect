package com.academic.management.util;

import com.academic.management.model.Grade;

/**
 * Turns marks into a grade.
 *
 * <p>This interface exists so the grading rules are declared in one
 * place and the service layer depends on the abstraction rather than on
 * a concrete formula. Swapping in a curve, a GPA scale used by a
 * different university, or a mock policy in a test only requires a new
 * implementation - no service code changes.
 */
public interface GradingPolicy {

    /**
     * Maps a percentage to a grade.
     *
     * @param percentage percentage between 0 and 100
     * @return the matching grade; never {@code null}
     * @throws IllegalArgumentException if {@code percentage} is outside
     *         0..100 or is not a number
     */
    Grade gradeFor(double percentage);

    /** Converts marks (out of {@link Constants#TOTAL_MARKS}) to a percentage. */
    double percentageFrom(double marks);

    /**
     * True when a percentage counts as a pass, using the policy's own
     * pass mark rather than a hard-coded comparison at the call site.
     */
    boolean isPass(double percentage);

    /** The minimum percentage that counts as a pass. */
    double getPassPercentage();

    /** Short description of the scale, shown in the UI. */
    String getDescription();
}
