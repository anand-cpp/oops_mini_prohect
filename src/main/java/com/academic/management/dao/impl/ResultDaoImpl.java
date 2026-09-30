package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.ResultDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.ValidationException;
import com.academic.management.model.Grade;
import com.academic.management.model.Result;
import com.academic.management.util.DatabaseManager;
import com.academic.management.util.GradingPolicy;
import com.academic.management.util.StandardGradingPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link ResultDao}.
 *
 * <h2>Why the grade is written by Java and not by SQL</h2>
 * {@code total_marks} is a {@code STORED GENERATED} column because it is
 * pure arithmetic. The grade is different: it is <em>policy</em>. The
 * thresholds live in {@link Grade} and the interpretation in
 * {@link com.academic.management.util.GradingPolicy}, and this DAO asks
 * the injected policy for the values it stores. Re-implementing the band
 * table in a {@code CASE} expression here would be a second, silently
 * divergent copy of the same rule.
 */
public class ResultDaoImpl extends AbstractDao implements ResultDao {

    private static final String TABLE = "results";

    private static final String COLUMNS =
            "result_id, student_id, course_id, internal_marks, external_marks, "
                    + "total_marks, percentage, grade, grade_point, is_pass, result_date, "
                    + "remarks";

    private static final String UPSERT_SQL =
            "INSERT INTO " + TABLE + " (student_id, course_id, internal_marks, "
                    + "external_marks, percentage, grade, grade_point, is_pass, "
                    + "result_date, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "internal_marks = VALUES(internal_marks), "
                    + "external_marks = VALUES(external_marks), "
                    + "percentage = VALUES(percentage), "
                    + "grade = VALUES(grade), "
                    + "grade_point = VALUES(grade_point), "
                    + "is_pass = VALUES(is_pass), "
                    + "result_date = VALUES(result_date), "
                    + "remarks = VALUES(remarks)";

    private static final String UPDATE_SQL =
            "UPDATE " + TABLE + " SET internal_marks = ?, external_marks = ?, "
                    + "percentage = ?, grade = ?, grade_point = ?, is_pass = ?, "
                    + "result_date = ?, remarks = ? WHERE student_id = ? AND course_id = ?";

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE + " WHERE student_id = ? AND course_id = ?";

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM " + TABLE;

    private final GradingPolicy policy;

    public ResultDaoImpl(DatabaseManager databaseManager) {
        this(databaseManager, StandardGradingPolicy.getInstance());
    }

    /**
     * @param policy the grading policy used to derive the stored grade;
     *               injected so a different scale can be substituted
     */
    public ResultDaoImpl(DatabaseManager databaseManager, GradingPolicy policy) {
        super(databaseManager);
        this.policy = policy == null ? StandardGradingPolicy.getInstance() : policy;
    }

    // ------------------------------------------------------------------
    // Create / Update
    // ------------------------------------------------------------------

    @Override
    public int upsert(Result result) throws AppException {
        Result stored = withPolicy(result);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            bindResult(statement, stored, 1);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("recording the result for " + stored.getStudentId()
                    + " / " + stored.getCourseId(), e,
                    new DatabaseAccessException("Could not save the result.", e));
        }
    }

    @Override
    public int insert(Result result) throws AppException {
        Result stored = withPolicy(result);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            bindResult(statement, stored, 1);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("inserting the result for " + stored.getStudentId()
                    + " / " + stored.getCourseId(), e,
                    new DatabaseAccessException("Could not save the result.", e));
        }
    }

    @Override
    public int update(Result result) throws AppException {
        Result stored = withPolicy(result);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            bindResult(statement, stored, 1);
            statement.setString(9, stored.getStudentId());
            statement.setString(10, stored.getCourseId());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("updating the result for " + stored.getStudentId()
                    + " / " + stored.getCourseId(), e,
                    new DatabaseAccessException("Could not update the result.", e));
        }
    }

    /**
     * Returns a copy of {@code result} that interprets its marks through
     * this DAO's policy. The original may have been built with a different
     * policy, and the values written to the database must come from the
     * one this DAO was configured with.
     */
    private Result withPolicy(Result result) throws AppException {
        if (result.getPolicy() == policy) {
            return result;
        }
        return new Result(result.getStudentId(), result.getCourseId(),
                result.getInternalMarks(), result.getExternalMarks(),
                result.getResultDate(), result.getRemarks(), policy);
    }

    private void bindResult(PreparedStatement statement, Result result, int startIndex)
            throws SQLException {
        statement.setString(startIndex, result.getStudentId());
        statement.setString(startIndex + 1, result.getCourseId());
        statement.setDouble(startIndex + 2, result.getInternalMarks());
        statement.setDouble(startIndex + 3, result.getExternalMarks());
        statement.setDouble(startIndex + 4, result.getPercentage());
        statement.setString(startIndex + 5, result.getGradeCode());
        statement.setDouble(startIndex + 6, result.getGradePoint());
        statement.setBoolean(startIndex + 7, result.isPass());
        statement.setDate(startIndex + 8, java.sql.Date.valueOf(result.getResultDate()));
        statement.setString(startIndex + 9, result.getRemarks());
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
            throw translate("deleting the result for " + studentId + " / " + courseId, e,
                    new DatabaseAccessException("Could not delete the result.", e));
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Override
    public Optional<Result> findById(long resultId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("result_id = ?", resultId);
        List<Result> rows = findMany(where);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<Result> findByStudentAndCourse(String studentId, String courseId)
            throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        where.andCondition("course_id = ?", courseId);
        List<Result> rows = findMany(where);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Result> findAll() throws AppException {
        return findMany(new WhereClause());
    }

    @Override
    public List<Result> findByStudent(String studentId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("student_id = ?", studentId);
        return findMany(where);
    }

    @Override
    public List<Result> findByCourse(String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_id = ?", courseId);
        return findMany(where);
    }

    @Override
    public List<Result> findByGrade(Grade grade) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("grade = ?", grade.getCode());
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
    public int countPassedInCourse(String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_id = ?", courseId);
        where.andCondition("is_pass = ?", Boolean.TRUE);
        return (int) countRows(where);
    }

    @Override
    public int countResultsInCourse(String courseId) throws AppException {
        WhereClause where = new WhereClause();
        where.andCondition("course_id = ?", courseId);
        return (int) countRows(where);
    }

    // ------------------------------------------------------------------
    // Row mapping and execution helpers
    // ------------------------------------------------------------------

    private Result mapRow(ResultSet rs) throws SQLException, AppException {
        String studentId = rs.getString("student_id");
        String courseId = rs.getString("course_id");
        try {
            Result result = new Result(
                    studentId,
                    courseId,
                    rs.getDouble("internal_marks"),
                    rs.getDouble("external_marks"),
                    readDate(rs, "result_date"),
                    rs.getString("remarks"),
                    policy);
            result.setResultId(rs.getLong("result_id"));
            return result;
        } catch (ValidationException e) {
            throw invalidRow(TABLE, studentId + "/" + courseId, e);
        }
    }

    private List<Result> findMany(WhereClause where) throws AppException {
        String sql = SELECT_SQL + where.toSql() + " ORDER BY student_id, course_id";
        List<Result> results = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            where.bindTo(statement, 1);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not read the result list.", e);
        }
        return results;
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
            throw new DatabaseAccessException("Could not count results.", e);
        }
    }
}
