package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.Constants;
import com.academic.management.util.GradingPolicy;
import com.academic.management.util.StandardGradingPolicy;
import com.academic.management.util.Validator;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The marks a student earned in one course, plus the grade derived from
 * them.
 *
 * <h2>Where the grading rule lives</h2>
 * The rule is <em>not</em> reimplemented here. This class holds the
 * marks and asks a {@link GradingPolicy} to interpret them. The policy
 * is injected, so the class works with any scale, and unit tests can
 * substitute a different one without touching the domain.
 */
public class Result {

    private static final int MAX_REMARK_LENGTH = 120;

    private long resultId;
    private final String studentId;
    private final String courseId;
    private double internalMarks;
    private double externalMarks;
    private LocalDate resultDate;
    private String remarks;

    private final GradingPolicy policy;

    /**
     * @param studentId     enrolled student
     * @param courseId      the course
     * @param internalMarks internal marks, 0 to {@link Constants#MAX_INTERNAL_MARKS}
     * @param externalMarks external marks, 0 to {@link Constants#MAX_EXTERNAL_MARKS}
     * @param resultDate    date the result was recorded
     * @param remarks       optional remarks
     * @param policy        the grading policy to use
     * @throws ValidationException if a mark is out of range
     */
    public Result(String studentId, String courseId, double internalMarks, double externalMarks,
                  LocalDate resultDate, String remarks, GradingPolicy policy)
            throws ValidationException {
        this.policy = policy == null ? StandardGradingPolicy.getInstance() : policy;
        this.studentId = Validator.requireIdentifier("Student Id", studentId);
        this.courseId = Validator.requireIdentifier("Course Id", courseId);
        this.resultDate = resultDate == null ? LocalDate.now() : resultDate;
        this.remarks = Validator.optionalText("Remarks", remarks, MAX_REMARK_LENGTH);
        this.internalMarks = internalMarks;
        this.externalMarks = externalMarks;
        validate();
    }

    public Result(String studentId, String courseId, double internalMarks,
                  double externalMarks, LocalDate resultDate, GradingPolicy policy)
            throws ValidationException {
        this(studentId, courseId, internalMarks, externalMarks, resultDate, null, policy);
    }

    public Result(String studentId, String courseId, double internalMarks,
                  double externalMarks, LocalDate resultDate) throws ValidationException {
        this(studentId, courseId, internalMarks, externalMarks, resultDate, null,
                StandardGradingPolicy.getInstance());
    }

    private void validate() throws ValidationException {
        if (internalMarks < 0 || internalMarks > Constants.MAX_INTERNAL_MARKS) {
            throw new ValidationException("Internal Marks", "Internal marks must be between 0 and "
                    + Validator.trimNumber(Constants.MAX_INTERNAL_MARKS) + ".");
        }
        if (externalMarks < 0 || externalMarks > Constants.MAX_EXTERNAL_MARKS) {
            throw new ValidationException("External Marks", "External marks must be between 0 and "
                    + Validator.trimNumber(Constants.MAX_EXTERNAL_MARKS) + ".");
        }
        if (resultDate.isAfter(LocalDate.now())) {
            throw new ValidationException("Result Date",
                    "The result date cannot be in the future.");
        }
    }

    // ------------------------------------------------------------------
    // Derived values - delegated to the injected policy
    // ------------------------------------------------------------------

    /** Internal plus external. A stored generated column mirrors this. */
    public double getTotalMarks() {
        return round2(internalMarks + externalMarks);
    }

    /** Total expressed as a percentage, per the policy. */
    public double getPercentage() {
        return policy.percentageFrom(getTotalMarks());
    }

    /** Letter grade, per the policy. */
    public Grade getGrade() {
        return policy.gradeFor(getPercentage());
    }

    /** Grade code such as {@code "B+"}, as stored in the database. */
    public String getGradeCode() {
        return getGrade().getCode();
    }

    /** Grade point on the 5.00 scale, used for the GPA. */
    public double getGradePoint() {
        return getGrade().getGradePoint();
    }

    /** True when the policy considers this a pass. */
    public boolean isPass() {
        return policy.isPass(getPercentage());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // Encapsulated state
    // ------------------------------------------------------------------

    public long getResultId() {
        return resultId;
    }

    public void setResultId(long resultId) {
        this.resultId = resultId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public double getInternalMarks() {
        return internalMarks;
    }

    public double getExternalMarks() {
        return externalMarks;
    }

    /** Both marks move together so the pair is never left half-applied. */
    public void updateMarks(double internal, double external) throws ValidationException {
        double previousInternal = this.internalMarks;
        double previousExternal = this.externalMarks;
        this.internalMarks = internal;
        this.externalMarks = external;
        try {
            validate();
        } catch (ValidationException e) {
            this.internalMarks = previousInternal;
            this.externalMarks = previousExternal;
            throw e;
        }
    }

    public void setInternalMarks(double internalMarks) throws ValidationException {
        updateMarks(internalMarks, this.externalMarks);
    }

    public void setExternalMarks(double externalMarks) throws ValidationException {
        updateMarks(this.internalMarks, externalMarks);
    }

    public LocalDate getResultDate() {
        return resultDate;
    }

    public void setResultDate(LocalDate resultDate) throws ValidationException {
        if (resultDate == null) {
            throw new ValidationException("Result Date", "The result date is required.");
        }
        if (resultDate.isAfter(LocalDate.now())) {
            throw new ValidationException("Result Date",
                    "The result date cannot be in the future.");
        }
        this.resultDate = resultDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) throws ValidationException {
        this.remarks = Validator.optionalText("Remarks", remarks, MAX_REMARK_LENGTH);
    }

    /** The policy this result is interpreted with. */
    public GradingPolicy getPolicy() {
        return policy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Result result)) {
            return false;
        }
        if (resultId == 0 || result.resultId == 0) {
            return studentId.equalsIgnoreCase(result.studentId)
                    && courseId.equalsIgnoreCase(result.courseId);
        }
        return resultId == result.resultId;
    }

    @Override
    public int hashCode() {
        return resultId != 0
                ? Long.hashCode(resultId)
                : Objects.hash(studentId.toUpperCase(), courseId.toUpperCase());
    }

    @Override
    public String toString() {
        return studentId + " / " + courseId + ": " + getTotalMarks() + "/"
                + Validator.trimNumber(Constants.TOTAL_MARKS) + " (" + getPercentage()
                + "%, " + getGradeCode() + ")";
    }
}
