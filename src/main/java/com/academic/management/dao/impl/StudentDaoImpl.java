package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.StudentDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.RecordNotFoundException;
import com.academic.management.exception.ValidationException;
import com.academic.management.model.Gender;
import com.academic.management.model.IdName;
import com.academic.management.model.Student;
import com.academic.management.model.StudentSearchCriteria;
import com.academic.management.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link StudentDao} against the {@code students} table.
 *
 * <h2>How JDBC is used here</h2>
 * <ol>
 *   <li>{@link #openConnection()} asks {@link DatabaseManager} for a
 *       connection from {@code DbConfig}.</li>
 *   <li>SQL is written once as a string literal with {@code ?}
 *       placeholders. User input is bound with
 *       {@link PreparedStatement#setString} and friends - never
 *       concatenated. This is the defence against SQL injection.</li>
 *   <li>Everything is wrapped in try-with-resources, so the
 *       {@link ResultSet}, {@link PreparedStatement} and
 *       {@link Connection} are all closed even if an exception is thrown
 *       part way through.</li>
 *   <li>{@link SQLException} is translated into an
 *       {@link AppException} so that no {@code java.sql} type escapes this
 *       package, and the original is always preserved as the cause and
 *       written to the log.</li>
 * </ol>
 */
public class StudentDaoImpl extends AbstractDao implements StudentDao {

    private static final String TABLE = "students";

    private static final String COLUMNS =
            "student_id, name, date_of_birth, gender, email, phone, address, "
                    + "department, semester, admission_date, guardian_contact";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE + " (" + COLUMNS + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE " + TABLE + " SET name = ?, date_of_birth = ?, gender = ?, "
                    + "email = ?, phone = ?, address = ?, department = ?, semester = ?, "
                    + "admission_date = ?, guardian_contact = ? WHERE student_id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE + " WHERE student_id = ?";

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM " + TABLE;

    private static final String SUMMARY_SQL =
            "SELECT student_id, name FROM " + TABLE + " ORDER BY name";

    public StudentDaoImpl(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Override
    public void insert(Student student) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            bindInsert(statement, student);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("inserting student " + student.getId(), e,
                    new DatabaseAccessException("Could not save the student.", e));
        }
    }

    private void bindInsert(PreparedStatement statement, Student student) throws SQLException {
        statement.setString(1, student.getId());
        statement.setString(2, student.getName());
        setLocalDate(statement, 3, student.getDateOfBirth());
        statement.setString(4, student.getGender().name());
        statement.setString(5, student.getEmail());
        statement.setString(6, student.getPhone());
        statement.setString(7, student.getAddress());
        statement.setString(8, student.getDepartment());
        statement.setInt(9, student.getSemester());
        setLocalDate(statement, 10, student.getAdmissionDate());
        statement.setString(11, student.getGuardianContact());
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @Override
    public boolean update(Student student) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setString(1, student.getName());
            setLocalDate(statement, 2, student.getDateOfBirth());
            statement.setString(3, student.getGender().name());
            statement.setString(4, student.getEmail());
            statement.setString(5, student.getPhone());
            statement.setString(6, student.getAddress());
            statement.setString(7, student.getDepartment());
            statement.setInt(8, student.getSemester());
            setLocalDate(statement, 9, student.getAdmissionDate());
            statement.setString(10, student.getGuardianContact());
            // The id is the primary key, so it selects the row and is
            // never itself written.
            statement.setString(11, student.getId());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("updating student " + student.getId(), e,
                    new DatabaseAccessException("Could not update the student.", e));
        }
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Override
    public boolean delete(String studentId) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, studentId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("deleting student " + studentId, e,
                    new DatabaseAccessException("Could not delete the student.", e));
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Override
    public Optional<Student> findById(String studentId) throws AppException {
        return findOne(SELECT_SQL + " WHERE student_id = ?", statement ->
                statement.setString(1, studentId));
    }

    @Override
    public Optional<Student> findByEmail(String email) throws AppException {
        return findOne(SELECT_SQL + " WHERE email = ?", statement ->
                statement.setString(1, email));
    }

    @Override
    public List<Student> findAll() throws AppException {
        return findMany(SELECT_SQL + " ORDER BY name", null);
    }

    @Override
    public List<IdName> findAllSummaries() throws AppException {
        List<IdName> summaries = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SUMMARY_SQL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                summaries.add(new IdName(rs.getString("student_id"),
                        rs.getString("student_id") + " - " + rs.getString("name")));
            }
        } catch (SQLException e) {
            throw translate("listing student summaries", e,
                    new DatabaseAccessException("Could not list students.", e));
        }
        return summaries;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Override
    public List<Student> search(StudentSearchCriteria query) throws AppException {
        WhereClause where = new WhereClause();
        if (query != null) {
            where.andEqualsIfPresent("student_id", query.getStudentId());
            where.andLikeIfPresent("name", query.likePattern());
            where.andLikeIfPresent("department", query.departmentLikePattern());
            where.andIfPresent("gender = ?", query.getGender() == null
                    ? null : query.getGender().name());
            where.andIfPresent("semester = ?", query.getSemester());
        }

        String sql = SELECT_SQL + where.toSql() + " ORDER BY name";
        return findMany(sql, where);
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
        // Strips the STU prefix and returns the largest numeric part, so
        // STU009 -> 9 and the UI can suggest STU010. The prefix length is
        // a parameter, never user input.
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(student_id, ?) AS UNSIGNED)), 0) "
                + "FROM " + TABLE + " WHERE student_id LIKE ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int prefixLength = Student.ID_PREFIX.length() + 1;
            statement.setInt(1, prefixLength);
            statement.setString(2, Student.ID_PREFIX + "%");
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read student count", e);
        }
    }

    @Override
    public List<String> findDistinctDepartments() throws AppException {
        return findDistinctValues("SELECT DISTINCT department FROM " + TABLE
                + " ORDER BY department");
    }

    @Override
    public boolean existsById(String studentId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        return countRows("SELECT COUNT(*) FROM " + TABLE + where.toSql(), where) > 0;
    }

    @Override
    public boolean existsByEmail(String email, String excludingStudentId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("email = ?", email);
        if (excludingStudentId != null && !excludingStudentId.isBlank()) {
            where.andCondition("student_id <> ?", excludingStudentId);
        }
        return countRows("SELECT COUNT(*) FROM " + TABLE + where.toSql(), where) > 0;
    }

    // ------------------------------------------------------------------
    // Row mapping
    // ------------------------------------------------------------------

    /**
     * Builds a {@link Student} from the current row. This is the only
     * place a database row becomes a domain object, so a column rename
     * touches one method instead of the whole DAO.
     */
    private Student mapRow(ResultSet rs) throws SQLException, AppException {
        String id = rs.getString("student_id");
        try {
            return mapStudentRow(rs);
        } catch (ValidationException e) {
            throw invalidRow(TABLE, id, e);
        }
    }

    /**
     * Builds a {@link Student} from the current row without wrapping a
     * domain-rule failure.
     *
     * <p>Package-private and static so {@link AcademicProfileDaoImpl} can
     * reuse it for its single-student lookup. Keeping one mapper means a
     * change to the {@code students} columns has to be reflected in one
     * method, not two that could drift apart.
     */
    static Student mapStudentRow(ResultSet rs) throws SQLException, ValidationException {
        return new Student(
                rs.getString("student_id"),
                rs.getString("name"),
                readDate(rs, "date_of_birth"),
                Gender.fromLabel(rs.getString("gender")),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("department"),
                rs.getInt("semester"),
                readDate(rs, "admission_date"),
                rs.getString("guardian_contact"));
    }

    /**
     * The summary projection is returned as {@link IdName} records rather
     * than {@link Student} objects: a {@code Student} cannot legally exist
     * without an email and a date of birth, so building one from a
     * two-column query would mean inventing placeholder values.
     */

    // ------------------------------------------------------------------
    // Shared execution helpers
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private Optional<Student> findOne(String sql, Binder binder) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the student.", e);
        }
    }

    private List<Student> findMany(String sql, WhereClause where) throws AppException {
        List<Student> students = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (where != null) {
                where.bindTo(statement, 1);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    students.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the student list.", e);
        }
        return students;
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
            throw new DatabaseAccessException("Could not count students.", e);
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

    private static void setLocalDate(PreparedStatement statement, int index,
                                     java.time.LocalDate value) throws SQLException {
        if (value == null) {
            statement.setDate(index, null);
        } else {
            statement.setDate(index, java.sql.Date.valueOf(value));
        }
    }
}
