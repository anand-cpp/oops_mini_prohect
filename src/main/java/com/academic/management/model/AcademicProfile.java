package com.academic.management.model;

import com.academic.management.util.Constants;
import com.academic.management.util.StandardGradingPolicy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A student's complete academic standing, assembled from the database at
 * the moment the profile is opened.
 *
 * <p>Every figure here is <em>calculated</em> from the rows returned by
 * the DAO - nothing is hard-coded and nothing is cached between runs, so
 * the profile always reflects what is actually stored in MySQL.
 *
 * <h2>Why this is an object rather than a bag of strings</h2>
 * The GPA, the credit total and the "is the student in good standing"
 * verdict are all derived once, consistently, from the same per-course
 * list. Both the Swing screen and any future report therefore show the
 * same numbers.
 */
public class AcademicProfile {

    private final Student student;
    private final List<CoursePerformance> courses;
    private final LocalDate generatedAt;

    public AcademicProfile(Student student, List<CoursePerformance> courses) {
        this.student = Objects.requireNonNull(student, "student is required");
        this.courses = Collections.unmodifiableList(
                new java.util.ArrayList<>(Objects.requireNonNull(courses, "courses is required")));
        this.generatedAt = LocalDate.now();
    }

    public Student getStudent() {
        return student;
    }

    public List<CoursePerformance> getCourses() {
        return courses;
    }

    public LocalDate getGeneratedAt() {
        return generatedAt;
    }

    // ------------------------------------------------------------------
    // Aggregates
    // ------------------------------------------------------------------

    public int getCourseCount() {
        return courses.size();
    }

    public int getCoursesWithResult() {
        return (int) courses.stream().filter(CoursePerformance::hasResult).count();
    }

    public int getCoursesWithAttendance() {
        return (int) courses.stream().filter(CoursePerformance::hasAttendance).count();
    }

    /** Sum of the credits of every enrolled course. */
    public double getTotalCredits() {
        return round2(courses.stream().mapToDouble(CoursePerformance::getCredits).sum());
    }

    /**
     * Simple average of the published course percentages, or 0 when no
     * results exist yet.
     */
    public double getOverallPercentage() {
        double[] taken = courses.stream()
                .filter(CoursePerformance::hasResult)
                .mapToDouble(CoursePerformance::getResultPercentage)
                .toArray();
        if (taken.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (double value : taken) {
            sum += value;
        }
        return round2(sum / taken.length);
    }

    /**
     * Credits-weighted GPA on the 5.00 scale: each course's grade point
     * weighted by its credits.
     *
     * <p>Weighting by credits matters - a 4-credit course must count for
     * more than a 1-credit one. Returns 0 when nothing has been graded.
     */
    public double getGpa() {
        double weightedSum = 0;
        double creditSum = 0;
        for (CoursePerformance course : courses) {
            if (course.hasResult() && course.getGradePoint() != null) {
                weightedSum += course.getGradePoint() * course.getCredits();
                creditSum += course.getCredits();
            }
        }
        return creditSum == 0 ? 0.0 : round2(weightedSum / creditSum);
    }

    /** Overall letter grade, derived from {@link #getOverallPercentage()}. */
    public Grade getOverallGrade() {
        return StandardGradingPolicy.getInstance().gradeFor(getOverallPercentage());
    }

    /**
     * Attendance across all courses combined: total attended divided by
     * total held, so a student is not penalised by weighting each course
     * equally regardless of its size.
     */
    public double getOverallAttendancePercentage() {
        int held = courses.stream().mapToInt(CoursePerformance::getClassesHeld).sum();
        int attended = courses.stream().mapToInt(CoursePerformance::getClassesAttended).sum();
        if (held == 0) {
            return Constants.NO_ATTENDANCE_PERCENT;
        }
        return round2(attended * 100.0 / held);
    }

    public long getPassedCourseCount() {
        return courses.stream()
                .filter(CoursePerformance::hasResult)
                .filter(course -> Boolean.TRUE.equals(course.getPassed()))
                .count();
    }

    public long getFailedCourseCount() {
        return getCoursesWithResult() - getPassedCourseCount();
    }

    /**
     * True when every recorded figure clears its threshold: no failed
     * course, overall percentage at or above the pass mark <em>and</em>
     * attendance at or above {@link Constants#ATTENDANCE_REQUIRED_PERCENT}.
     *
     * <p>The failed-course condition is not redundant with the overall
     * average. A student can average well above the pass mark while
     * having failed one course - for example 90% and 25% average to
     * 57.5%, which clears 40% comfortably. Reporting such a student as
     * "in good standing" would contradict
     * {@link #getStandingSummary()}, which tells them a course needs
     * re-examination, so the check includes it explicitly.
     */
    public boolean isInGoodStanding() {
        return hasAnyResult()
                && getFailedCourseCount() == 0
                && getOverallPercentage() >= Constants.PASS_PERCENTAGE
                && getOverallAttendancePercentage() >= Constants.ATTENDANCE_REQUIRED_PERCENT;
    }

    public boolean hasAnyResult() {
        return getCoursesWithResult() > 0;
    }

    /** One-line verdict for the profile header. */
    public String getStandingSummary() {
        if (!hasAnyResult()) {
            return "No results published yet.";
        }
        if (getFailedCourseCount() > 0) {
            return getFailedCourseCount() + " course(s) need re-examination.";
        }
        if (getOverallAttendancePercentage() < Constants.ATTENDANCE_REQUIRED_PERCENT) {
            return "Attendance below the required "
                    + (int) Constants.ATTENDANCE_REQUIRED_PERCENT + "%.";
        }
        return "In good standing.";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public String toString() {
        return "AcademicProfile[" + student.getId() + ", " + getCourseCount()
                + " courses, " + getOverallPercentage() + "%, GPA " + getGpa() + "]";
    }
}
