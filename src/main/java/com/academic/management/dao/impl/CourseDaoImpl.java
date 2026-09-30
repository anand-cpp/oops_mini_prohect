package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.CourseDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.ValidationException;
import com.academic.management.model.Course;
import com.academic.management.model.CourseSearchCriteria;
import com.academic.management.model.IdName;
import com.academic.management.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link CourseDao} against the {@code courses} table.
 *
 * <p>{@code courses.faculty_id} is a nullable foreign key with
 * {@code ON DELETE SET NULL}, so a course can legitimately exist with no
 * teacher assigned. The DAO therefore writes {@code null} rather than an
 * empty string when the field is blank, and {@link Course#hasFaculty()}
 * distinguishes the two cases.
 */
public class CourseDaoImpl extends AbstractDao implements CourseDao {

    private static final String TABLE = "courses";

    private static final String COLUMNS =
            "course_id, course_code, course_name, credits, faculty_id, department, "
                    + "semester, description";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE + " (" + COLUMNS + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE " + TABLE + " SET course_code = ?, course_name = ?, credits = ?, "
                    + "faculty_id = ?, department = ?, semester = ?, description = ? "
                    + "WHERE course_id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE + " WHERE course_id = ?";

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM " + TABLE;

    private static final String SUMMARY_SQL =
            "SELECT course_id, course_code, course_name FROM " + TABLE
                    + " ORDER BY course_code";

    public CourseDaoImpl(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Override
    public void insert(Course course) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, course.getCourseId());
            statement.setString(2, course.getCourseCode());
            statement.setString(3, course.getCourseName());
            statement.setDouble(4, course.getCredits());
            statement.setString(5, course.getFacultyId());
            statement.setString(6, course.getDepartment());
            statement.setInt(7, course.getSemester());
            statement.setString(8, course.getDescription());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("inserting course " + course.getCourseId(), e,
                    new DatabaseAccessException("Could not save the course.", e));
        }
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @Override
    public boolean update(Course course) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setString(1, course.getCourseCode());
            statement.setString(2, course.getCourseName());
            statement.setDouble(3, course.getCredits());
            statement.setString(4, course.getFacultyId());
            statement.setString(5, course.getDepartment());
            statement.setInt(6, course.getSemester());
            statement.setString(7, course.getDescription());
            statement.setString(8, course.getCourseId());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("updating course " + course.getCourseId(), e,
                    new DatabaseAccessException("Could not update the course.", e));
        }
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Override
    public boolean delete(String courseId) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, courseId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("deleting course " + courseId, e,
                    new DatabaseAccessException("Could not delete the course.", e));
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Override
    public Optional<Course> findById(String courseId) throws AppException {
        return findOne(SELECT_SQL + " WHERE course_id = ?",
                statement -> statement.setString(1, courseId));
    }

    @Override
    public Optional<Course> findByCode(String courseCode) throws AppException {
        return findOne(SELECT_SQL + " WHERE course_code = ?",
                statement -> statement.setString(1, courseCode));
    }

    @Override
    public List<Course> findAll() throws AppException {
        return findMany(SELECT_SQL + " ORDER BY course_code", null);
    }

    @Override
    public List<Course> findByStudent(String studentId) throws AppException {
        String sql = SELECT_SQL + " JOIN enrollments e ON e.course_id = " + TABLE
                + ".course_id WHERE e.student_id = ? ORDER BY course_code";
        WhereClause where = new WhereClause();
        where.andCondition("e.student_id = ?", studentId);
        return findMany(sql, where);
    }

    @Override
    public List<Course> findByFaculty(String facultyId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("faculty_id = ?", facultyId);
        return findMany(SELECT_SQL + where.toSql() + " ORDER BY course_code", where);
    }

    @Override
    public List<IdName> findAllSummaries() throws AppException {
        List<IdName> summaries = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SUMMARY_SQL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                summaries.add(new IdName(rs.getString("course_id"),
                        rs.getString("course_code") + " - " + rs.getString("course_name")));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not list courses.", e);
        }
        return summaries;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Override
    public List<Course> search(CourseSearchCriteria query) throws AppException {
        WhereClause where = new WhereClause();
        if (query != null) {
            where.andEqualsIfPresent("course_id", query.getCourseId());
            where.andLikeIfPresent("course_code", query.codeLikePattern());
            where.andLikeIfPresent("course_name", query.nameLikePattern());
            where.andLikeIfPresent("department", query.departmentLikePattern());
            where.andEqualsIfPresent("faculty_id", query.getFacultyId());
            where.andIfPresent("semester = ?", query.getSemester());
        }
        return findMany(SELECT_SQL + where.toSql() + " ORDER BY course_code", where);
    }

    // ------------------------------------------------------------------
    // Aggregate helpers
    // ------------------------------------------------------------------

    @Override
    public long count() throws AppException {
        return countRows("SELECT COUNT(*) FROM " + TABLE, null);
    }

    @Override
    public int findHighestNumericSuffix() throws AppException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(course_id, ?) AS UNSIGNED)), 0) "
                + "FROM " + TABLE + " WHERE course_id LIKE ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Course.ID_PREFIX.length() + 1);
            statement.setString(2, Course.ID_PREFIX + "%");
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read course count", e);
        }
    }

    @Override
    public List<String> findDistinctDepartments() throws AppException {
        return findDistinctValues("SELECT DISTINCT department FROM " + TABLE
                + " ORDER BY department");
    }

    @Override
    public boolean existsById(String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_id = ?", courseId);
        return countRows("SELECT COUNT(*) FROM " + TABLE + where.toSql(), where) > 0;
    }

    @Override
    public boolean existsByCode(String courseCode, String excludingCourseId)
            throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_code = ?", courseCode);
        if (excludingCourseId != null && !excludingCourseId.isBlank()) {
            where.andCondition("course_id <> ?", excludingCourseId);
        }
        return countRows("SELECT COUNT(*) FROM " + TABLE + where.toSql(), where) > 0;
    }

    // ------------------------------------------------------------------
    // Row mapping and execution helpers
    // ------------------------------------------------------------------

    private Course mapRow(ResultSet rs) throws SQLException, AppException {
        String id = rs.getString("course_id");
        try {
            return new Course(
                    id,
                    rs.getString("course_code"),
                    rs.getString("course_name"),
                    rs.getDouble("credits"),
                    rs.getString("faculty_id"),
                    rs.getString("department"),
                    rs.getInt("semester"),
                    rs.getString("description"));
        } catch (ValidationException e) {
            throw invalidRow(TABLE, id, e);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private List<Course> findMany(String sql, WhereClause where) throws AppException {
        List<Course> courses = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (where != null) {
                where.bindTo(statement, 1);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    courses.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the course list.", e);
        }
        return courses;
    }

    private Optional<Course> findOne(String sql, Binder binder) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the course.", e);
        }
    }

    private long countRows(String sql, WhereClause where) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (where != null) {
                where.bindTo(statement, 1);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not count courses.", e);
        }
    }

    private List<String> findDistinctValues(String sql) throws AppException {
        List<String> values = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the list of values.", e);
        }
        return values;
    }
}
