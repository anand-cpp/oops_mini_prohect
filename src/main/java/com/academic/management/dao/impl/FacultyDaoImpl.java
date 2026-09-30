package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.FacultyDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.ValidationException;
import com.academic.management.model.Faculty;
import com.academic.management.model.FacultySearchCriteria;
import com.academic.management.model.Gender;
import com.academic.management.model.IdName;
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
 * JDBC implementation of {@link FacultyDao} against the {@code faculty} table.
 *
 * <p>Follows the same shape as {@link StudentDaoImpl}: constant SQL
 * literals with {@code ?} placeholders, every value bound through
 * {@link PreparedStatement}, try-with-resources throughout, and
 * {@link SQLException} translated at the boundary.
 */
public class FacultyDaoImpl extends AbstractDao implements FacultyDao {

    private static final String TABLE = "faculty";

    private static final String COLUMNS =
            "faculty_id, name, date_of_birth, gender, email, phone, address, "
                    + "department, designation, office_location, joining_date";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE + " (" + COLUMNS + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE " + TABLE + " SET name = ?, date_of_birth = ?, gender = ?, "
                    + "email = ?, phone = ?, address = ?, department = ?, designation = ?, "
                    + "office_location = ?, joining_date = ? WHERE faculty_id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE + " WHERE faculty_id = ?";

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM " + TABLE;

    private static final String SUMMARY_SQL =
            "SELECT faculty_id, name, designation FROM " + TABLE
                    + " ORDER BY name";

    public FacultyDaoImpl(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Override
    public void insert(Faculty faculty) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, faculty.getId());
            statement.setString(2, faculty.getName());
            setLocalDate(statement, 3, faculty.getDateOfBirth());
            statement.setString(4, faculty.getGender().name());
            statement.setString(5, faculty.getEmail());
            statement.setString(6, faculty.getPhone());
            statement.setString(7, faculty.getAddress());
            statement.setString(8, faculty.getDepartment());
            statement.setString(9, faculty.getDesignation());
            statement.setString(10, faculty.getOfficeLocation());
            setLocalDate(statement, 11, faculty.getJoiningDate());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("inserting faculty " + faculty.getId(), e,
                    new DatabaseAccessException("Could not save the faculty member.", e));
        }
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @Override
    public boolean update(Faculty faculty) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setString(1, faculty.getName());
            setLocalDate(statement, 2, faculty.getDateOfBirth());
            statement.setString(3, faculty.getGender().name());
            statement.setString(4, faculty.getEmail());
            statement.setString(5, faculty.getPhone());
            statement.setString(6, faculty.getAddress());
            statement.setString(7, faculty.getDepartment());
            statement.setString(8, faculty.getDesignation());
            statement.setString(9, faculty.getOfficeLocation());
            setLocalDate(statement, 10, faculty.getJoiningDate());
            statement.setString(11, faculty.getId());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("updating faculty " + faculty.getId(), e,
                    new DatabaseAccessException("Could not update the faculty member.", e));
        }
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Override
    public boolean delete(String facultyId) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, facultyId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("deleting faculty " + facultyId, e,
                    new DatabaseAccessException("Could not delete the faculty member.", e));
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Override
    public Optional<Faculty> findById(String facultyId) throws AppException {
        return findOne(SELECT_SQL + " WHERE faculty_id = ?",
                statement -> statement.setString(1, facultyId));
    }

    @Override
    public Optional<Faculty> findByEmail(String email) throws AppException {
        return findOne(SELECT_SQL + " WHERE email = ?",
                statement -> statement.setString(1, email));
    }

    @Override
    public List<Faculty> findAll() throws AppException {
        return findMany(SELECT_SQL + " ORDER BY name", null);
    }

    @Override
    public List<IdName> findAllSummaries() throws AppException {
        List<IdName> summaries = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SUMMARY_SQL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("faculty_id");
                summaries.add(new IdName(id, id + " - " + rs.getString("name")
                        + " (" + rs.getString("designation") + ")"));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not list faculty members.", e);
        }
        return summaries;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Override
    public List<Faculty> search(FacultySearchCriteria query) throws AppException {
        WhereClause where = new WhereClause();
        if (query != null) {
            where.andEqualsIfPresent("faculty_id", query.getFacultyId());
            where.andLikeIfPresent("name", query.nameLikePattern());
            where.andLikeIfPresent("department", query.departmentLikePattern());
            where.andLikeIfPresent("designation", query.designationLikePattern());
        }
        return findMany(SELECT_SQL + where.toSql() + " ORDER BY name", where);
    }

    // ------------------------------------------------------------------
    // Aggregate helpers
    // ------------------------------------------------------------------

    @Override
    public long count() throws AppException {
        return countRows("SELECT COUNT(*) FROM " + TABLE, null);
    }

    @Override
    public int countCoursesTaught(String facultyId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("faculty_id = ?", facultyId);
        return (int) countRows("SELECT COUNT(*) FROM courses" + where.toSql(), where);
    }

    @Override
    public int findHighestNumericSuffix() throws AppException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(faculty_id, ?) AS UNSIGNED)), 0) "
                + "FROM " + TABLE + " WHERE faculty_id LIKE ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Faculty.ID_PREFIX.length() + 1);
            statement.setString(2, Faculty.ID_PREFIX + "%");
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read faculty count", e);
        }
    }

    @Override
    public List<String> findDistinctDepartments() throws AppException {
        return findDistinctValues("SELECT DISTINCT department FROM " + TABLE
                + " ORDER BY department");
    }

    @Override
    public List<String> findDistinctDesignations() throws AppException {
        return findDistinctValues("SELECT DISTINCT designation FROM " + TABLE
                + " ORDER BY designation");
    }

    @Override
    public boolean existsById(String facultyId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("faculty_id = ?", facultyId);
        return countRows("SELECT COUNT(*) FROM " + TABLE + where.toSql(), where) > 0;
    }

    @Override
    public boolean existsByEmail(String email, String excludingFacultyId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("email = ?", email);
        if (excludingFacultyId != null && !excludingFacultyId.isBlank()) {
            where.andCondition("faculty_id <> ?", excludingFacultyId);
        }
        return countRows("SELECT COUNT(*) FROM " + TABLE + where.toSql(), where) > 0;
    }

    // ------------------------------------------------------------------
    // Row mapping and execution helpers
    // ------------------------------------------------------------------

    private Faculty mapRow(ResultSet rs) throws SQLException, AppException {
        String id = rs.getString("faculty_id");
        try {
            return new Faculty(
                    id,
                    rs.getString("name"),
                    readDate(rs, "date_of_birth"),
                    Gender.fromLabel(rs.getString("gender")),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("address"),
                    rs.getString("department"),
                    rs.getString("designation"),
                    rs.getString("office_location"),
                    readDate(rs, "joining_date"));
        } catch (ValidationException e) {
            throw invalidRow(TABLE, id, e);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private Optional<Faculty> findOne(String sql, Binder binder) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the faculty member.", e);
        }
    }

    private List<Faculty> findMany(String sql, WhereClause where) throws AppException {
        List<Faculty> facultyMembers = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (where != null) {
                where.bindTo(statement, 1);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    facultyMembers.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the faculty list.", e);
        }
        return facultyMembers;
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
            throw new DatabaseAccessException("Could not count faculty members.", e);
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
