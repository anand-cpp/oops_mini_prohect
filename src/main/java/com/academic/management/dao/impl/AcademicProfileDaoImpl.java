package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.AcademicProfileDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.RecordNotFoundException;
import com.academic.management.model.AcademicProfile;
import com.academic.management.model.CoursePerformance;
import com.academic.management.model.Student;
import com.academic.management.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Builds the read-only academic profile aggregate.
 *
 * <h2>Where the work happens</h2>
 * The per-course rows come from {@code v_student_performance}, which
 * already joins students, courses, faculty, enrollments, attendance and
 * results in the database. That is deliberate: the naive alternative -
 * loading the student, then looping and issuing one query per course -
 * would be six round trips for a four-course student and would grow with
 * the size of the record. The view does it in one.
 *
 * <p>The remaining figures (GPA, credit total, attendance roll-up) are
 * <em>not</em> computed in SQL. They are derived by
 * {@link AcademicProfile} from these rows, so the profile screen, the
 * report and any test all use exactly the same arithmetic.
 */
public class AcademicProfileDaoImpl extends AbstractDao implements AcademicProfileDao {

    private static final String VIEW = "v_student_performance";

    private static final String STUDENT_SQL =
            "SELECT * FROM students WHERE student_id = ?";

    private static final String COURSE_ROWS_SQL =
            "SELECT course_id, course_code, course_name, credits, course_semester, "
                    + "faculty_name, enrollment_status, classes_held, classes_attended, "
                    + "result_percentage, grade, grade_point, is_pass "
                    + "FROM " + VIEW + " WHERE student_id = ? "
                    + "ORDER BY course_semester, course_code";

    public AcademicProfileDaoImpl(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public AcademicProfile buildProfile(String studentId) throws AppException {
        Student student = findStudent(studentId)
                .orElseThrow(() -> new RecordNotFoundException(
                        "Student",
                        "No student exists with id '" + studentId + "'."));
        return new AcademicProfile(student, findCoursePerformances(studentId));
    }

    @Override
    public List<CoursePerformance> findCoursePerformances(String studentId) throws AppException {
        List<CoursePerformance> rows = new java.util.ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(COURSE_ROWS_SQL)) {
            statement.setString(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CoursePerformance(
                            rs.getString("course_id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getDouble("credits"),
                            rs.getInt("course_semester"),
                            rs.getString("faculty_name"),
                            rs.getString("enrollment_status"),
                            rs.getInt("classes_held"),
                            rs.getInt("classes_attended"),
                            readNullableDouble(rs, "result_percentage"),
                            rs.getString("grade"),
                            readNullableDouble(rs, "grade_point"),
                            readNullableBoolean(rs, "is_pass")));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Could not build the academic profile for " + studentId + ".", e);
        }
        return rows;
    }

    /**
     * Reads the student row through the {@code students} table directly.
     * Reusing {@code StudentDaoImpl} would be tidier, but this class only
     * needs the identity fields the profile header shows; a narrow query
     * avoids a circular dependency between the two DAOs.
     */
    @Override
    public Optional<Student> findStudent(String studentId) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(STUDENT_SQL)) {
            statement.setString(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(StudentDaoImpl.mapStudentRow(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Could not read student " + studentId + ".", e);
        } catch (com.academic.management.exception.ValidationException e) {
            throw invalidRow("students", studentId, e);
        }
    }

    /**
     * One query for every headline number on the dashboard. Each figure is
     * a scalar subquery so the whole summary costs a single round trip,
     * and the averages are guarded with {@code NULLIF} so a division by
     * zero yields {@code NULL} - which becomes 0 - instead of an error.
     */
    @Override
    public DashboardSummary loadDashboardSummary() throws AppException {
        String sql =
                "SELECT (SELECT COUNT(*) FROM students)  AS student_count, "
                        + "(SELECT COUNT(*) FROM faculty)  AS faculty_count, "
                        + "(SELECT COUNT(*) FROM courses)  AS course_count, "
                        + "(SELECT COUNT(*) FROM enrollments) AS enrollment_count, "
                        + "(SELECT COUNT(*) FROM attendance) AS attendance_count, "
                        + "(SELECT COUNT(*) FROM results) AS result_count, "
                        + "(SELECT COALESCE(ROUND(AVG(percentage), 2), 0) FROM results) "
                        + "    AS avg_result_percent, "
                        + "(SELECT COALESCE(ROUND(AVG(percentage), 2), 0) FROM attendance "
                        + " WHERE classes_held > 0) AS avg_attendance_percent";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                // A single-row aggregate can only be empty if the server
                // answered with no row set at all, which means the query
                // itself is wrong - not that the database is empty.
                throw new DatabaseAccessException(
                        "The dashboard query returned no row.",
                        "Expected exactly one aggregate row from: " + sql, null);
            }
            return new DashboardSummary(
                    rs.getInt("student_count"),
                    rs.getInt("faculty_count"),
                    rs.getInt("course_count"),
                    rs.getInt("enrollment_count"),
                    rs.getInt("attendance_count"),
                    rs.getInt("result_count"),
                    rs.getDouble("avg_result_percent"),
                    rs.getDouble("avg_attendance_percent"));
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not load the dashboard summary.", e);
        }
    }
}
