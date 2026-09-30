package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.AcademicProfile;
import com.academic.management.model.CoursePerformance;
import com.academic.management.model.Student;

import java.util.List;

/**
 * Read side of the academic profile.
 *
 * <p>Kept separate from {@link StudentDao} because it answers a different
 * question: not "what is this student row" but "assemble everything the
 * institution knows about this student's studies". That aggregate spans
 * five tables, and the
 * {@code v_student_performance} view does the joining in one round trip
 * instead of the N+1 pattern a naive implementation would produce.
 */
public interface AcademicProfileDao {

    /**
     * Builds the complete academic profile for a student.
     *
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if the student does not exist
     */
    AcademicProfile buildProfile(String studentId) throws AppException;

    /**
     * The per-course rows behind the profile, already joined. Exposed
     * separately so the profile screen can populate its table without
     * rebuilding the whole aggregate.
     */
    List<CoursePerformance> findCoursePerformances(String studentId) throws AppException;

    /** The student record itself, or empty when the id is unknown. */
    java.util.Optional<Student> findStudent(String studentId) throws AppException;

    /**
     * Headline figures for the dashboard, computed in SQL so the home
     * screen needs a single query.
     */
    DashboardSummary loadDashboardSummary() throws AppException;

    /**
     * Aggregate counts and averages for the dashboard.
     *
     * @param studentCount           number of students
     * @param facultyCount           number of faculty members
     * @param courseCount            number of courses
     * @param enrollmentCount        number of enrolments
     * @param attendanceRecords      number of attendance rows
     * @param resultRecords          number of result rows
     * @param averageResultPercent   mean result percentage across all
     *                               published results, 0 when none
     * @param averageAttendancePercent mean attendance percentage across all
     *                               records that have classes held, 0 when none
     */
    public record DashboardSummary(int studentCount, int facultyCount, int courseCount,
                                  int enrollmentCount, int attendanceRecords,
                                  int resultRecords, double averageResultPercent,
                                  double averageAttendancePercent) {
    }
}
