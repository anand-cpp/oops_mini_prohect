package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.Constants;
import com.academic.management.util.Validator;

import java.time.LocalDate;
import java.util.Objects;

/**
 * How many classes of one course a student has held versus attended.
 *
 * <p>The percentage is <em>derived</em>, never stored in a field the
 * caller can set: it is recomputed from the two counts. That is the
 * clearest example of encapsulation in the project - the invariant
 * {@code 0 <= attended <= held} is enforced on construction and on every
 * mutation, and the division-by-zero case is handled once, here.
 */
public class Attendance {

    private static final int MAX_ID_LENGTH = 20;

    private long attendanceId;
    private final String studentId;
    private final String courseId;
    private int classesHeld;
    private int classesAttended;
    private LocalDate recordedOn;

    /**
     * @param studentId       enrolled student
     * @param courseId        the course
     * @param classesHeld     total classes conducted; 0 is allowed
     * @param classesAttended classes the student attended, 0 to held
     * @param recordedOn      date the record was taken
     * @throws ValidationException if the counts are inconsistent
     */
    public Attendance(String studentId, String courseId, int classesHeld,
                      int classesAttended, LocalDate recordedOn) throws ValidationException {
        this.studentId = Validator.requireIdentifier("Student Id", studentId);
        this.courseId = Validator.requireIdentifier("Course Id", courseId);
        this.recordedOn = recordedOn == null ? LocalDate.now() : recordedOn;
        // Assign first, then validate as a unit so the error message can
        // refer to both values.
        this.classesHeld = classesHeld;
        this.classesAttended = classesAttended;
        validate();
    }

    private void validate() throws ValidationException {
        Validator.requireAttendanceCounts(classesHeld, classesAttended);
        if (recordedOn.isAfter(LocalDate.now())) {
            throw new ValidationException("Recorded On",
                    "The attendance date cannot be in the future.");
        }
    }

    // ------------------------------------------------------------------
    // Derived value - the calculation lives here, nowhere else
    // ------------------------------------------------------------------

    /**
     * Attendance percentage, {@code (attended / held) * 100}.
     *
     * <p>When no classes have been held the percentage is
     * {@link Constants#NO_ATTENDANCE_PERCENT} instead of
     * {@code NaN} or a {@link ArithmeticException}; the equivalent
     * {@code CASE WHEN classes_held = 0} guard exists in the generated
     * {@code attendance.percentage} column.
     */
    public double getAttendancePercentage() {
        return calculatePercentage(classesHeld, classesAttended);
    }

    /**
     * Pure function of the two counts, so the calculation is testable
     * without constructing an object.
     *
     * @throws IllegalArgumentException if the counts are inconsistent
     */
    public static double calculatePercentage(int held, int attended) {
        if (held < 0) {
            throw new IllegalArgumentException("Classes held cannot be negative: " + held);
        }
        if (attended < 0) {
            throw new IllegalArgumentException(
                    "Classes attended cannot be negative: " + attended);
        }
        if (attended > held) {
            throw new IllegalArgumentException(
                    "Classes attended (" + attended + ") cannot exceed held (" + held + ").");
        }
        if (held == 0) {
            return Constants.NO_ATTENDANCE_PERCENT;
        }
        return round2(attended * 100.0 / held);
    }

    /** Classes the student missed. */
    public int getClassesAbsent() {
        return classesHeld - classesAttended;
    }

    /** True when the student meets the required attendance threshold. */
    public boolean hasRegularAttendance() {
        return getAttendancePercentage() >= Constants.ATTENDANCE_REQUIRED_PERCENT;
    }

    /** Short verdict used by the UI, for example {@code "Regular"}. */
    public String getAttendanceStatus() {
        if (classesHeld == 0) {
            return "No classes held";
        }
        return hasRegularAttendance() ? "Regular" : "Irregular";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // Encapsulated state
    // ------------------------------------------------------------------

    public long getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(long attendanceId) {
        this.attendanceId = attendanceId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public int getClassesHeld() {
        return classesHeld;
    }

    public int getClassesAttended() {
        return classesAttended;
    }

    /** Updates both counts together, so the invariant is never left broken. */
    public void updateCounts(int held, int attended) throws ValidationException {
        int previousHeld = this.classesHeld;
        int previousAttended = this.classesAttended;
        this.classesHeld = held;
        this.classesAttended = attended;
        try {
            validate();
        } catch (ValidationException e) {
            this.classesHeld = previousHeld;
            this.classesAttended = previousAttended;
            throw e;
        }
    }

    public void setClassesHeld(int classesHeld) throws ValidationException {
        updateCounts(classesHeld, this.classesAttended);
    }

    public void setClassesAttended(int classesAttended) throws ValidationException {
        updateCounts(this.classesHeld, classesAttended);
    }

    public LocalDate getRecordedOn() {
        return recordedOn;
    }

    public void setRecordedOn(LocalDate recordedOn) throws ValidationException {
        if (recordedOn == null) {
            throw new ValidationException("Recorded On", "The attendance date is required.");
        }
        if (recordedOn.isAfter(LocalDate.now())) {
            throw new ValidationException("Recorded On",
                    "The attendance date cannot be in the future.");
        }
        this.recordedOn = recordedOn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Attendance attendance)) {
            return false;
        }
        if (attendanceId == 0 || attendance.attendanceId == 0) {
            return studentId.equalsIgnoreCase(attendance.studentId)
                    && courseId.equalsIgnoreCase(attendance.courseId);
        }
        return attendanceId == attendance.attendanceId;
    }

    @Override
    public int hashCode() {
        return attendanceId != 0
                ? Long.hashCode(attendanceId)
                : Objects.hash(studentId.toUpperCase(), courseId.toUpperCase());
    }

    @Override
    public String toString() {
        return studentId + " / " + courseId + ": " + classesAttended + "/" + classesHeld
                + " (" + getAttendancePercentage() + "%)";
    }
}
