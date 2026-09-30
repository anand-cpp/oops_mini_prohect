package com.academic.management.util;

import com.academic.management.model.Grade;

/**
 * The project's single grading policy: a ten-point-style letter scale.
 *
 * <pre>
 *   90 - 100  A+   5.00
 *   80 -  89  A    4.00
 *   70 -  79  B+   3.50
 *   60 -  69  B    3.00
 *   50 -  59  C    2.00
 *   40 -  49  D    1.00
 *    0 -  39  F    0.00
 * </pre>
 *
 * <p>The thresholds live in {@link Grade}, and this class is the only
 * place that walks them. Keeping the arithmetic here is what stops the
 * same table from being retyped in a Swing dialog, a DAO and a report.
 */
public final class StandardGradingPolicy implements GradingPolicy {

    /** The one instance; the policy is stateless. */
    private static final StandardGradingPolicy INSTANCE = new StandardGradingPolicy();

    private StandardGradingPolicy() {
        // singleton
    }

    public static StandardGradingPolicy getInstance() {
        return INSTANCE;
    }

    @Override
    public Grade gradeFor(double percentage) {
        if (Double.isNaN(percentage) || Double.isInfinite(percentage)) {
            throw new IllegalArgumentException("Percentage must be a real number.");
        }
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException(
                    "Percentage must be between 0 and 100 but was " + percentage + ".");
        }
        for (Grade grade : Grade.orderedByThreshold()) {
            if (percentage >= grade.getMinimumPercentage()) {
                return grade;
            }
        }
        // Unreachable: Grade.F starts at 0.
        return Grade.F;
    }

    @Override
    public double percentageFrom(double marks) {
        if (Double.isNaN(marks) || marks < 0) {
            throw new IllegalArgumentException("Marks must be zero or greater.");
        }
        if (Constants.TOTAL_MARKS <= 0) {
            throw new IllegalStateException("Total marks must be greater than zero.");
        }
        return round2(marks * 100.0 / Constants.TOTAL_MARKS);
    }

    @Override
    public boolean isPass(double percentage) {
        return percentage >= getPassPercentage();
    }

    @Override
    public double getPassPercentage() {
        return Constants.PASS_PERCENTAGE;
    }

    @Override
    public String getDescription() {
        return "A+ >=90, A >=80, B+ >=70, B >=60, C >=50, D >=40, F <40. "
                + "Pass mark " + (int) getPassPercentage() + "%.";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
