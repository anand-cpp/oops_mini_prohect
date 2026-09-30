package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.Constants;
import com.academic.management.util.Validator;

import java.util.Objects;

/**
 * A course offered by a department, taught by one faculty member.
 *
 * <p>A course does not extend {@link Person} - it is not a person - but
 * it keeps the same encapsulation discipline: private state, validated
 * construction and controlled mutation.
 */
public class Course {

    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_CODE_LENGTH = 20;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_DEPARTMENT_LENGTH = 80;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final String courseId;
    private String courseCode;
    private String courseName;
    private double credits;
    private String facultyId;
    private String department;
    private int semester;
    private String description;

    /**
     * @param courseId     internal identifier, for example {@code CRS001}
     * @param courseCode   unique public code, for example {@code CS301}
     * @param courseName   display name
     * @param credits      credit weight, 0 (exclusive) to 10
     * @param facultyId    teaching faculty member, or {@code null} if unassigned
     * @param department   owning department
     * @param semester     semester in which it is offered, 1 to 10
     * @param description  optional description
     * @throws ValidationException if any value breaks a validation rule
     */
    public Course(String courseId, String courseCode, String courseName, double credits,
                  String facultyId, String department, int semester, String description)
            throws ValidationException {
        this.courseId = Validator.requireIdentifier("Course Id", courseId);
        this.courseCode = Validator.requireCourseCode("Course Code", courseCode);
        this.courseName = Validator.requireText("Course Name", courseName, MAX_NAME_LENGTH);
        this.credits = credits;
        this.facultyId = Validator.optionalText("Faculty Id", facultyId, MAX_ID_LENGTH);
        this.department = Validator.requireText("Department", department, MAX_DEPARTMENT_LENGTH);
        this.semester = semester;
        this.description = Validator.optionalText("Description", description,
                MAX_DESCRIPTION_LENGTH);
        validate();
    }

    /** Convenience constructor without the optional description. */
    public Course(String courseId, String courseCode, String courseName, double credits,
                  String facultyId, String department, int semester)
            throws ValidationException {
        this(courseId, courseCode, courseName, credits, facultyId, department, semester, null);
    }

    private void validate() throws ValidationException {
        if (credits <= 0 || credits > Constants.MAX_CREDITS) {
            throw new ValidationException("Credits", "Credits must be between 0 and "
                    + Constants.MAX_CREDITS + ".");
        }
        if (semester < Constants.MIN_SEMESTER || semester > Constants.MAX_SEMESTER) {
            throw new ValidationException("Semester", "Semester must be between "
                    + Constants.MIN_SEMESTER + " and " + Constants.MAX_SEMESTER + ".");
        }
    }

    // ------------------------------------------------------------------
    // Encapsulated state
    // ------------------------------------------------------------------

    /** The primary key; immutable. */
    public String getCourseId() {
        return courseId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) throws ValidationException {
        this.courseCode = Validator.requireCourseCode("Course Code", courseCode);
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) throws ValidationException {
        this.courseName = Validator.requireText("Course Name", courseName, MAX_NAME_LENGTH);
    }

    public double getCredits() {
        return credits;
    }

    public void setCredits(double credits) throws ValidationException {
        double previous = this.credits;
        this.credits = credits;
        try {
            validate();
        } catch (ValidationException e) {
            this.credits = previous;
            throw e;
        }
    }

    public String getFacultyId() {
        return facultyId;
    }

    /** Assigning or clearing the teaching faculty member. */
    public void setFacultyId(String facultyId) throws ValidationException {
        this.facultyId = Validator.optionalText("Faculty Id", facultyId, MAX_ID_LENGTH);
    }

    public boolean hasFaculty() {
        return facultyId != null && !facultyId.isBlank();
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) throws ValidationException {
        this.department = Validator.requireText("Department", department, MAX_DEPARTMENT_LENGTH);
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) throws ValidationException {
        this.description = Validator.optionalText("Description", description,
                MAX_DESCRIPTION_LENGTH);
    }

    /** Display form used in tables and dropdowns. */
    public String getDisplayName() {
        return courseCode + " - " + courseName;
    }

    public static String suggestId(int highestExistingNumber) {
        return String.format("CRS%03d", highestExistingNumber + 1);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Course course && courseId.equals(course.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
