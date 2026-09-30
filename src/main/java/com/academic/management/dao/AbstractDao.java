package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.exception.DuplicateRecordException;
import com.academic.management.util.AppLogger;
import com.academic.management.util.Constants;
import com.academic.management.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared plumbing for every JDBC DAO.
 *
 * <h2>Why this exists</h2>
 * Six DAOs would otherwise repeat the same three things: obtain a
 * connection, translate {@link SQLException} into a
 * {@link DatabaseAccessException}, and assemble a variable-length
 * {@code WHERE} clause from optional search fields. Centralising them
 * here means the SQL-injection policy is decided once: every value is
 * bound through a {@link PreparedStatement} placeholder and never
 * concatenated into the statement text.
 *
 * <h2>SQL injection policy</h2>
 * {@link WhereClause} is the only supported way to add a condition, and it
 * only ever appends fixed SQL fragments chosen by this class. The
 * user-supplied value is always registered as a {@code ?} parameter, so
 * no user input can ever alter the shape of a statement. The only
 * exception is a {@code LIKE} pattern, where {@code %} and {@code _} are
 * legal wildcards - that is intended behaviour for a search box, and the
 * pattern is still passed as a bound parameter, so it remains safe.
 */
public abstract class AbstractDao {

    protected final Logger log = AppLogger.getLogger(getClass());
    private final DatabaseManager databaseManager;

    protected AbstractDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    protected DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    // ------------------------------------------------------------------
    // Connection handling
    // ------------------------------------------------------------------

    /**
     * Opens a connection for the caller. Always use it in
     * try-with-resources so the connection is returned even when the body
     * throws.
     */
    protected Connection openConnection() throws DatabaseAccessException {
        return databaseManager.getConnection();
    }

    /**
     * Maps a driver failure onto the application's own exception types.
     *
     * <p>A MySQL unique-index violation (error 1062) is reported as a
     * {@link DuplicateRecordException} so the UI can say "that id is
     * already taken" instead of "database error". Every other
     * {@link SQLException} becomes a {@link DatabaseAccessException} with
     * the original kept as the cause. Nothing is swallowed: the log always
     * receives the full stack trace.
     *
     * @param action human-readable description used in the message
     * @param cause  the driver exception
     * @param e      the exception to throw when the cause is not a
     *               duplicate-key violation
     */
    protected AppException translate(String action, SQLException cause, AppException e) {
        log.log(Level.SEVERE, "SQL failure while " + action
                + " (SQLState=" + cause.getSQLState() + ", code=" + cause.getErrorCode() + ")",
                cause);
        if (DatabaseAccessException.isUniqueViolation(cause)) {
            return new DuplicateRecordException(
                    "That record already exists. Please use a different identifier.",
                    "Unique constraint violated while " + action
                            + " (SQLState=" + cause.getSQLState() + ", code="
                            + cause.getErrorCode() + ")");
        }
        return e;
    }

    // ------------------------------------------------------------------
    // Row-mapping helpers
    // ------------------------------------------------------------------

    /**
     * Wraps a {@link com.academic.management.exception.ValidationException}
     * raised while turning a database row into a domain object.
     *
     * <p>This happens when the database holds a value the domain rejects -
     * a blank name, a malformed email, a semester outside 1..10. It is a
     * genuine data-integrity problem, not user input, so it is reported
     * loudly (logged with the offending primary key) instead of being
     * silently dropped, which would make rows disappear from the UI
     * without explanation.
     *
     * @param table     the table being read
     * @param primaryKey the offending row's key
     * @param cause     the validation failure
     */
    protected DatabaseAccessException invalidRow(String table, String primaryKey,
                                                 Exception cause) {
        log.log(Level.SEVERE, "Row in " + table + " with " + primaryKey
                + " violates a domain rule and could not be mapped.", cause);
        return new DatabaseAccessException(
                "A record in the database is not valid (" + table + " " + primaryKey
                        + ") and could not be displayed.",
                "Row mapping failed for " + table + "." + primaryKey + ": "
                        + cause.getMessage(), cause);
    }

    /** Reads a nullable {@code DATE} column as a {@link LocalDate}. */
    protected static LocalDate readDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    /** Reads a nullable {@code DECIMAL}/{@code DOUBLE} as a boxed double. */
    protected static Double readNullableDouble(ResultSet rs, String column)
            throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    /** Reads a nullable {@code INT}/{@code TINYINT} as a boxed integer. */
    protected static Integer readNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** Reads a nullable {@code BOOLEAN}. */
    protected static Boolean readNullableBoolean(ResultSet rs, String column)
            throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    // ------------------------------------------------------------------
    // Dynamic WHERE clause assembly
    // ------------------------------------------------------------------

    /**
     * Accumulates {@code AND} conditions with bound parameters.
     *
     * <p>Conditions are contributed as <em>fixed</em> SQL text chosen by
     * the DAO; the accompanying value is appended as a {@code ?}
     * placeholder and its index recorded, so the finished statement is
     * always {@code SELECT ... WHERE a = ? AND b LIKE ?} - the user's
     * input is a value, never part of the statement.
     */
    public static final class WhereClause {

        private final List<String> conditions = new ArrayList<>();
        private final List<Object> parameters = new ArrayList<>();

        /**
         * Adds {@code fragment} when {@code value} is present, binding the
         * value to the single {@code ?} inside {@code fragment}.
         *
         * @param fragment SQL text containing exactly one {@code ?}
         * @param value    the value to bind; ignored when {@code null} or blank
         */
        public void andIfPresent(String fragment, Object value) {
            if (value == null) {
                return;
            }
            if (value instanceof String text && text.trim().isEmpty()) {
                return;
            }
            conditions.add(fragment);
            parameters.add(value);
        }

        /** Adds an equality condition against a nullable column. */
        public void andEqualsIfPresent(String column, Object value) {
            andIfPresent(column + " = ?", value);
        }

        /**
         * Adds a case-insensitive {@code LIKE} condition.
         *
         * @param column         the column to match
         * @param alreadyWrapped pattern that already has its {@code %} added
         */
        public void andLikeIfPresent(String column, String alreadyWrapped) {
            andIfPresent("LOWER(" + column + ") LIKE ?", alreadyWrapped);
        }

        /** Adds an explicit condition with an arbitrary number of parameters. */
        public void andCondition(String fragment, Object... values) {
            conditions.add(fragment);
            for (Object value : values) {
                parameters.add(value);
            }
        }

        public boolean isEmpty() {
            return conditions.isEmpty();
        }

        /** The SQL fragment, for example {@code " WHERE a = ? AND b = ?"}. */
        public String toSql() {
            return isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        }

        /**
         * Binds every collected parameter, in order, onto
         * {@code statement}. Binds onto whatever index the statement
         * already expects, so this also works for statements that place
         * some parameters in the {@code SET} clause before the
         * {@code WHERE} clause.
         *
         * @param statement  the statement to bind
         * @param startIndex the 1-based index of the first parameter to bind
         */
        public void bindTo(PreparedStatement statement, int startIndex) throws SQLException {
            int index = startIndex;
            for (Object parameter : parameters) {
                bindValue(statement, index++, parameter);
            }
        }

        /** Number of parameters collected. */
        public int parameterCount() {
            return parameters.size();
        }
    }

    /**
     * Binds one parameter using the setter that matches its type.
     * Keeping this in one place is what makes the "no raw user input in
     * SQL" guarantee easy to audit.
     */
    protected static void bindValue(PreparedStatement statement, int index, Object value)
            throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else if (value instanceof String text) {
            statement.setString(index, text);
        } else if (value instanceof Integer number) {
            statement.setInt(index, number);
        } else if (value instanceof Long number) {
            statement.setLong(index, number);
        } else if (value instanceof Double number) {
            statement.setDouble(index, number);
        } else if (value instanceof Boolean flag) {
            statement.setBoolean(index, flag);
        } else if (value instanceof LocalDate date) {
            statement.setDate(index, java.sql.Date.valueOf(date));
        } else if (value instanceof Enum<?> enumValue) {
            statement.setString(index, enumValue.name());
        } else {
            throw new SQLException("Unsupported parameter type: " + value.getClass().getName());
        }
    }

    /** Executes an INSERT/UPDATE/DELETE and returns the affected row count. */
    protected static int executeUpdate(PreparedStatement statement) throws SQLException {
        try {
            return statement.executeUpdate();
        } finally {
            statement.close();
        }
    }

    /** The MySQL vendor code for a duplicate-key violation. */
    protected static boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == Constants.MYSQL_DUPLICATE_KEY;
    }

    /** Closes a {@link Statement} or {@link ResultSet} without masking failures. */
    protected static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            // Closing a resource must never mask the original failure.
            AppLogger.getLogger(AbstractDao.class)
                    .log(Level.FINE, "Ignoring error while closing a JDBC resource.", e);
        }
    }
}
