package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.AttendanceDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.ValidationException;
import com.academic.management.model.Attendance;
import com.academic.management.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link AttendanceDao}.
 *
 * <p>Because {@code UNIQUE(student_id, course_id)} allows only one row per
 * pair, {@link #upsert(Attendance)} uses MySQL's
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} so recording attendance
 * twice updates the same row rather than failing.
 *
 * <p>Note what is <em>not</em> written here: the {@code percentage}
 * column. It is a {@code STORED GENERATED} column, so MySQL computes it
 * with the same formula and the same {@code classes_held = 0} guard that
 * {@link Attendance#getAttendancePercentage()} uses in Java. The two can
 * therefore never drift apart.
 */
public class AttendanceDaoImpl extends AbstractDao implements AttendanceDao {

    private static final String TABLE = "attendance";

    private static final String COLUMNS =
            "attendance_id, student_id, course_id, classes_held, classes_attended, "
                    + "percentage, recorded_on";

    private static final String UPSERT_SQL =
            "INSERT INTO " + TABLE
                    + " (student_id, course_id, classes_held, classes_attended, recorded_on) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "classes_held = VALUES(classes_held), "
                    + "classes_attended = VALUES(classes_attended), "
                    + "recorded_on = VALUES(recorded_on)";

    private static final String UPDATE_SQL =
            "UPDATE " + TABLE + " SET classes_held = ?, classes_attended = ?, "
                    + "recorded_on = ? WHERE student_id = ? AND course_id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE + " WHERE student_id = ? AND course_id = ?";

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM " + TABLE;

    public AttendanceDaoImpl(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    // ------------------------------------------------------------------
    // Create / Update
    // ------------------------------------------------------------------

    @Override
    public int upsert(Attendance attendance) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, attendance.getStudentId());
            statement.setString(2, attendance.getCourseId());
            statement.setInt(3, attendance.getClassesHeld());
            statement.setInt(4, attendance.getClassesAttended());
            statement.setDate(5, java.sql.Date.valueOf(attendance.getRecordedOn()));
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("recording attendance for " + attendance.getStudentId()
                    + " / " + attendance.getCourseId(), e,
                    new DatabaseAccessException("Could not save the attendance record.", e));
        }
    }

    @Override
    public int update(Attendance attendance) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setInt(1, attendance.getClassesHeld());
            statement.setInt(2, attendance.getClassesAttended());
            statement.setDate(3, java.sql.Date.valueOf(attendance.getRecordedOn()));
            statement.setString(4, attendance.getStudentId());
            statement.setString(5, attendance.getCourseId());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("updating attendance for " + attendance.getStudentId()
                    + " / " + attendance.getCourseId(), e,
                    new DatabaseAccessException("Could not update the attendance record.", e));
        }
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Override
    public boolean delete(String studentId, String courseId) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, studentId);
            statement.setString(2, courseId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("deleting attendance for " + studentId + " / " + courseId, e,
                    new DatabaseAccessException("Could not delete the attendance record.", e));
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Override
    public Optional<Attendance> findById(long attendanceId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("attendance_id = ?", attendanceId);
        List<Attendance> rows = findMany(where);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<Attendance> findByStudentAndCourse(String studentId, String courseId)
            throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        where.andCondition("course_id = ?", courseId);
        List<Attendance> rows = findMany(where);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Attendance> findAll() throws AppException {
        return findMany(new WhereClause());
    }

    @Override
    public List<Attendance> findByStudent(String studentId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        return findMany(where);
    }

    @Override
    public List<Attendance> findByCourse(String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_id = ?", courseId);
        return findMany(where);
    }

    // ------------------------------------------------------------------
    // Aggregate helpers
    // ------------------------------------------------------------------

    @Override
    public long count() throws AppException {
        WhereClause where = new WhereClause();
        return countRows(where);
    }

    @Override
    public List<String> findDistinctStudentIds() throws AppException {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT DISTINCT student_id FROM " + TABLE + " ORDER BY student_id";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read attendance students.", e);
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // Row mapping and execution helpers
    // ------------------------------------------------------------------

    private Attendance mapRow(ResultSet rs) throws SQLException, AppException {
        String studentId = rs.getString("student_id");
        String courseId = rs.getString("course_id");
        try {
            Attendance attendance = new Attendance(
                    studentId,
                    courseId,
                    rs.getInt("classes_held"),
                    rs.getInt("classes_attended"),
                    readDate(rs, "recorded_on"));
            attendance.setAttendanceId(rs.getLong("attendance_id"));
            return attendance;
        } catch (ValidationException e) {
            throw invalidRow(TABLE, studentId + "/" + courseId, e);
        }
    }

    private List<Attendance> findMany(WhereClause where) throws AppException {
        String sql = SELECT_SQL + where.toSql() + " ORDER BY student_id, course_id";
        List<Attendance> records = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            where.bindTo(statement, 1);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the attendance list.", e);
        }
        return records;
    }

    private long countRows(WhereClause where) throws AppException {
        String sql = "SELECT COUNT(*) FROM " + TABLE + where.toSql();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            where.bindTo(statement, 1);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not count attendance records.", e);
        }
    }
}
