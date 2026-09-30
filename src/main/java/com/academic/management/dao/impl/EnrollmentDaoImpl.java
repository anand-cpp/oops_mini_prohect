package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.EnrollmentDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.DuplicateRecordException;
import com.academic.management.exception.ValidationException;
import com.academic.management.model.Enrollment;
import com.academic.management.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link EnrollmentDao} against {@code enrollments}.
 *
 * <p>The {@code UNIQUE(student_id, course_id)} index is what actually
 * prevents a double enrolment. This DAO checks for the violation and
 * converts it into a {@link DuplicateRecordException} carrying a message
 * a user can act on, rather than letting a raw driver error surface.
 */
public class EnrollmentDaoImpl extends AbstractDao implements EnrollmentDao {

    private static final String TABLE = "enrollments";

    private static final String COLUMNS =
            "enrollment_id, student_id, course_id, semester, enrollment_date, status";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE
                    + " (student_id, course_id, semester, enrollment_date, status) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE " + TABLE + " SET semester = ?, enrollment_date = ?, status = ? "
                    + "WHERE student_id = ? AND course_id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE + " WHERE student_id = ? AND course_id = ?";

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM " + TABLE;

    public EnrollmentDaoImpl(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Override
    public void insert(Enrollment enrollment) throws AppException {
        insertAndReturn(enrollment);
    }

    @Override
    public Enrollment insertAndReturn(Enrollment enrollment) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, enrollment.getStudentId());
            statement.setString(2, enrollment.getCourseId());
            statement.setInt(3, enrollment.getSemester());
            statement.setDate(4, java.sql.Date.valueOf(enrollment.getEnrollmentDate()));
            statement.setString(5, enrollment.getStatus().name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    enrollment.setEnrollmentId(keys.getLong(1));
                }
            }
            return enrollment;
        } catch (SQLException e) {
            if (DatabaseAccessException.isUniqueViolation(e)) {
                log.warning("Duplicate enrolment refused by the database for "
                        + enrollment.getStudentId() + " / " + enrollment.getCourseId());
                throw new DuplicateRecordException(
                        "This student is already enrolled in that course.",
                        "UNIQUE(student_id, course_id) violated for "
                                + enrollment.getStudentId() + "/" + enrollment.getCourseId());
            }
            throw translate("inserting enrolment " + enrollment.getStudentId()
                    + " -> " + enrollment.getCourseId(), e,
                    new DatabaseAccessException("Could not save the enrolment.", e));
        }
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @Override
    public boolean update(Enrollment enrollment) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setInt(1, enrollment.getSemester());
            statement.setDate(2, java.sql.Date.valueOf(enrollment.getEnrollmentDate()));
            statement.setString(3, enrollment.getStatus().name());
            statement.setString(4, enrollment.getStudentId());
            statement.setString(5, enrollment.getCourseId());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("updating enrolment " + enrollment.getStudentId()
                    + " -> " + enrollment.getCourseId(), e,
                    new DatabaseAccessException("Could not update the enrolment.", e));
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
            throw translate("deleting enrolment " + studentId + " -> " + courseId, e,
                    new DatabaseAccessException("Could not delete the enrolment.", e));
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Override
    public Optional<Enrollment> findById(long enrollmentId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("enrollment_id = ?", enrollmentId);
        return findOne(where);
    }

    @Override
    public Optional<Enrollment> findByStudentAndCourse(String studentId, String courseId)
            throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        where.andCondition("course_id = ?", courseId);
        return findOne(where);
    }

    @Override
    public boolean isEnrolled(String studentId, String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        where.andCondition("course_id = ?", courseId);
        return countRows(where) > 0;
    }

    @Override
    public List<Enrollment> findAll() throws AppException {
        return findMany(new WhereClause());
    }

    @Override
    public List<Enrollment> findByStudent(String studentId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        return findMany(where);
    }

    @Override
    public List<Enrollment> findByCourse(String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_id = ?", courseId);
        return findMany(where);
    }

    @Override
    public List<Enrollment> findByStudentAndSemester(String studentId, int semester)
            throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        where.andCondition("semester = ?", semester);
        return findMany(where);
    }

    @Override
    public List<Enrollment> findEnrolledSince(LocalDate date) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("enrollment_date >= ?", date);
        return findMany(where);
    }

    // ------------------------------------------------------------------
    // Aggregate helpers
    // ------------------------------------------------------------------

    @Override
    public long count() throws AppException {
        return countRows(new WhereClause());
    }

    @Override
    public int countByCourse(String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_id = ?", courseId);
        return (int) countRows(where);
    }

    // ------------------------------------------------------------------
    // Row mapping and execution helpers
    // ------------------------------------------------------------------

    private Enrollment mapRow(ResultSet rs) throws SQLException, AppException {
        String studentId = rs.getString("student_id");
        String courseId = rs.getString("course_id");
        try {
            Enrollment enrollment = new Enrollment(
                    studentId,
                    courseId,
                    rs.getInt("semester"),
                    readDate(rs, "enrollment_date"),
                    Enrollment.Status.fromLabel(rs.getString("status")));
            enrollment.setEnrollmentId(rs.getLong("enrollment_id"));
            return enrollment;
        } catch (ValidationException e) {
            throw invalidRow(TABLE, studentId + "/" + courseId, e);
        }
    }

    private Optional<Enrollment> findOne(WhereClause where) throws AppException {
        List<Enrollment> rows = findMany(where);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private List<Enrollment> findMany(WhereClause where) throws AppException {
        String sql = SELECT_SQL + where.toSql() + " ORDER BY enrollment_date DESC, course_id";
        List<Enrollment> enrollments = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            where.bindTo(statement, 1);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    enrollments.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the enrolment list.", e);
        }
        return enrollments;
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
            throw new DatabaseAccessException("Could not count enrolments.", e);
        }
    }
}
