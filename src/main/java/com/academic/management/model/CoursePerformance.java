package com.academic.management.model;

/**
 * One row of a student's academic history: the course, who teaches it,
 * when the student enrolled, and the attendance and result figures that
 * belong to that pairing.
 *
 * <p>This is a read-only projection assembled by
 * {@code AcademicProfileDAO} from the {@code v_student_performance} view.
 * It exists so the profile screen does not have to join five tables in
 * the UI, and so the aggregation code has a single, stable shape to work
 * with.
 */
public class CoursePerformance {

    private final String courseId;
    private final String courseCode;
    private final String courseName;
    private final double credits;
    private final int courseSemester;
    private final String facultyName;
    private final String enrollmentStatus;
    private final int classesHeld;
    private final int classesAttended;
    private final Double resultPercentage;
    private final String grade;
    private final Double gradePoint;
    private final Boolean passed;

    public CoursePerformance(String courseId, String courseCode, String courseName,
                             double credits, int courseSemester, String facultyName,
                             String enrollmentStatus, int classesHeld, int classesAttended,
                             Double resultPercentage, String grade, Double gradePoint,
                             Boolean passed) {
        this.courseId = courseId;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.courseSemester = courseSemester;
        this.facultyName = facultyName;
        this.enrollmentStatus = enrollmentStatus;
        this.classesHeld = classesHeld;
        this.classesAttended = classesAttended;
        this.resultPercentage = resultPercentage;
        this.grade = grade;
        this.gradePoint = gradePoint;
        this.passed = passed;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getCourseDisplayName() {
        return courseCode + " - " + courseName;
    }

    public double getCredits() {
        return credits;
    }

    public int getCourseSemester() {
        return courseSemester;
    }

    /** {@code null} when no faculty member is assigned to the course. */
    public String getFacultyName() {
        return facultyName;
    }

    public String getEnrollmentStatus() {
        return enrollmentStatus;
    }

    public int getClassesHeld() {
        return classesHeld;
    }

    public int getClassesAttended() {
        return classesAttended;
    }

    /**
     * Attendance percentage, or {@code null} when no attendance has been
     * recorded for this course.
     */
    public Double getAttendancePercentage() {
        if (classesHeld <= 0) {
            return null;
        }
        return Math.round(classesAttended * 10000.0 / classesHeld) / 100.0;
    }

    /** {@code null} until a result has been published for this course. */
    public Double getResultPercentage() {
        return resultPercentage;
    }

    /** {@code null} until a result has been published. */
    public String getGrade() {
        return grade;
    }

    public Double getGradePoint() {
        return gradePoint;
    }

    public Boolean getPassed() {
        return passed;
    }

    public boolean hasResult() {
        return resultPercentage != null;
    }

    public boolean hasAttendance() {
        return classesHeld > 0;
    }

    @Override
    public String toString() {
        return getCourseDisplayName();
    }
}
