package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.Validator;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The link between a {@link Student} and a {@link Course}.
 *
 * <p>This is the association object for the many-to-many relationship
 * "a student takes many courses, a course has many students". Holding
 * the extra attributes (enrolment date, semester, status) here avoids
 * duplicating them on either side, which is the whole point of an
 * association class.
 */
public class Enrollment {

    public enum Status {
        ACTIVE("Active"),
        COMPLETED("Completed"),
        WITHDRAWN("Withdrawn");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** Lenient parse so an unexpected stored value cannot break a listing. */
        public static Status fromLabel(String value) {
            if (value != null) {
                String trimmed = value.trim();
                for (Status status : values()) {
                    if (status.name().equalsIgnoreCase(trimmed)) {
                        return status;
                    }
                }
            }
            return ACTIVE;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final int MAX_ID_LENGTH = 20;

    /** Populated by the database; 0 for a not-yet-persisted enrolment. */
    private long enrollmentId;
    private final String studentId;
    private final String courseId;
    private int semester;
    private LocalDate enrollmentDate;
    private Status status;

    public Enrollment(String studentId, String courseId, int semester,
                      LocalDate enrollmentDate, Status status) throws ValidationException {
        this.studentId = Validator.requireIdentifier("Student Id", studentId);
        this.courseId = Validator.requireIdentifier("Course Id", courseId);
        this.semester = semester;
        this.enrollmentDate = enrollmentDate == null ? LocalDate.now() : enrollmentDate;
        this.status = status == null ? Status.ACTIVE : status;
        validate();
    }

    public Enrollment(String studentId, String courseId, int semester, LocalDate enrollmentDate)
            throws ValidationException {
        this(studentId, courseId, semester, enrollmentDate, Status.ACTIVE);
    }

    private void validate() throws ValidationException {
        if (semester < com.academic.management.util.Constants.MIN_SEMESTER
                || semester > com.academic.management.util.Constants.MAX_SEMESTER) {
            throw new ValidationException("Semester", "Semester must be between "
                    + com.academic.management.util.Constants.MIN_SEMESTER + " and "
                    + com.academic.management.util.Constants.MAX_SEMESTER + ".");
        }
        if (enrollmentDate.isAfter(LocalDate.now())) {
            throw new ValidationException("Enrollment Date",
                    "Enrollment date cannot be in the future.");
        }
        if (studentId.equalsIgnoreCase(courseId)) {
            throw new ValidationException("Student Id",
                    "A student and a course cannot share the same identifier.");
        }
    }

    // ------------------------------------------------------------------
    // Encapsulated state
    // ------------------------------------------------------------------

    public long getEnrollmentId() {
        return enrollmentId;
    }

    /** Used by the DAO when reading a row back from the database. */
    public void setEnrollmentId(long enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) throws ValidationException {
        int previous = this.semester;
        this.semester = semester;
        try {
            validate();
        } catch (ValidationException e) {
            this.semester = previous;
            throw e;
        }
    }

    public LocalDate getEnrollmentDate() {
        return enrollmentDate;
    }

    public void setEnrollmentDate(LocalDate enrollmentDate) throws ValidationException {
        if (enrollmentDate == null) {
            throw new ValidationException("Enrollment Date", "Enrollment date is required.");
        }
        this.enrollmentDate = enrollmentDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status == null ? Status.ACTIVE : status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Enrollment enrollment)) {
            return false;
        }
        // Before the row has a surrogate key, the natural key identifies it.
        if (enrollmentId == 0 || enrollment.enrollmentId == 0) {
            return studentId.equalsIgnoreCase(enrollment.studentId)
                    && courseId.equalsIgnoreCase(enrollment.courseId);
        }
        return enrollmentId == enrollment.enrollmentId;
    }

    @Override
    public int hashCode() {
        return enrollmentId != 0
                ? Long.hashCode(enrollmentId)
                : Objects.hash(studentId.toUpperCase(), courseId.toUpperCase());
    }

    @Override
    public String toString() {
        return "Enrollment #" + enrollmentId + ": " + studentId + " -> " + courseId
                + " (semester " + semester + ", " + status.getLabel() + ")";
    }
}
