package com.academic.management.exception;

import java.sql.SQLException;

/**
 * Wraps every {@link SQLException} raised by the DAO layer so the rest of
 * the application never has to import {@code java.sql}.
 *
 * <p>The original {@link SQLException} is always kept as the cause, so
 * nothing is swallowed and the original SQL state and vendor error code
 * remain available for logging.
 */
public class DatabaseAccessException extends AppException {

    private static final long serialVersionUID = 1L;

    /** SQL state the driver reported, for example {@code 23000}. */
    private final String sqlState;

    /** Vendor error code the driver reported. */
    private final int errorCode;

    public DatabaseAccessException(String action, SQLException cause) {
        super(buildUserMessage(action),
              "Database failure while " + action + ". SQLState=" + cause.getSQLState()
                      + " vendorCode=" + cause.getErrorCode()
                      + " message=" + cause.getMessage(),
              cause);
        this.sqlState = cause.getSQLState();
        this.errorCode = cause.getErrorCode();
    }

    public DatabaseAccessException(String userMessage, String technicalMessage, Throwable cause) {
        super(userMessage, technicalMessage, cause);
        this.sqlState = null;
        this.errorCode = 0;
    }

    public String getSqlState() {
        return sqlState;
    }

    public int getErrorCode() {
        return errorCode;
    }

    /** SQL state for a foreign-key violation. */
    public static boolean isForeignKeyViolation(SQLException e) {
        return e != null && "23000".equals(e.getSQLState());
    }

    /** SQL state for a unique-index violation. */
    public static boolean isUniqueViolation(SQLException e) {
        return e != null && "23000".equals(e.getSQLState()) && e.getErrorCode() == 1062;
    }

    private static String buildUserMessage(String action) {
        return "Could not " + action
                + " because the database was not reachable. "
                + "Check that MySQL is running and that the credentials in "
                + "application.properties are correct.";
    }
}
