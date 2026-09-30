package com.academic.management.util;

import com.academic.management.exception.DatabaseAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Loads database settings from {@code application.properties} and builds
 * JDBC connections from them.
 *
 * <h2>Where credentials come from</h2>
 * <ol>
 *   <li>System properties, e.g. {@code -Ddb.password=...} (highest priority)</li>
 *   <li>Environment variables, e.g. {@code ACADEMIC_DB_PASSWORD}</li>
 *   <li>The {@code application.properties} file on the classpath</li>
 * </ol>
 * The real file is git-ignored; only {@code application.properties.example}
 * is committed, so no real password ever reaches version control.
 */
public final class DbConfig {

    private static final Logger LOG = AppLogger.getLogger(DbConfig.class);

    private static final String CONFIG_FILE = "application.properties";
    private static final String DEFAULT_CONFIG_FILE = "application.properties.example";

    private static final String KEY_URL = "db.url";
    private static final String KEY_USER = "db.username";
    private static final String KEY_PASSWORD = "db.password";
    private static final String KEY_DRIVER = "db.driver";
    private static final String KEY_POOL_SIZE = "db.connection.timeoutSeconds";

    private static final String ENV_URL = "ACADEMIC_DB_URL";
    private static final String ENV_USER = "ACADEMIC_DB_USERNAME";
    private static final String ENV_PASSWORD = "ACADEMIC_DB_PASSWORD";

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int connectTimeoutSeconds;

    private DbConfig(String jdbcUrl, String username, String password, int connectTimeoutSeconds) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    /** Reads configuration from the classpath. */
    public static DbConfig load() throws DatabaseAccessException {
        Properties props = new Properties();
        boolean loadedRealConfig = loadInto(props, CONFIG_FILE);

        if (!loadedRealConfig && !loadInto(props, DEFAULT_CONFIG_FILE)) {
            throw new DatabaseAccessException(
                    "Database configuration file is missing.",
                    "Neither '" + CONFIG_FILE + "' nor '" + DEFAULT_CONFIG_FILE
                            + "' was found on the classpath. Copy the .example file to "
                            + CONFIG_FILE + " and fill in your MySQL credentials.",
                    null);
        }
        if (!loadedRealConfig) {
            LOG.warning("No '" + CONFIG_FILE + "' found; falling back to '"
                    + DEFAULT_CONFIG_FILE + "' for database settings.");
        }

        String url = resolve(KEY_URL, ENV_URL, props, "jdbc:mysql://localhost:3306/"
                + "student_academic_management?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC&characterEncoding=UTF-8");
        String user = resolve(KEY_USER, ENV_USER, props, "root");
        String pass = resolve(KEY_PASSWORD, ENV_PASSWORD, props, "");
        int timeout = parsePositiveInt(resolve(KEY_POOL_SIZE, null, props, "10"), 10);
        String driver = resolve(KEY_DRIVER, null, props, "com.mysql.cj.jdbc.Driver");

        if (pass.isEmpty()) {
            LOG.warning("No database password configured. Set db.password in "
                    + CONFIG_FILE + " or the " + ENV_PASSWORD + " environment variable.");
        }

        return new DbConfig(url, user, pass, timeout);
    }

    private static boolean loadInto(Properties props, String resourceName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourceName)) {
            if (in == null) {
                return false;
            }
            props.load(in);
            return true;
        } catch (IOException e) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Failed to read '" + resourceName + "' from the classpath.", e);
            return false;
        }
    }

    /** System property wins, then environment variable, then the file value. */
    private static String resolve(String key, String envKey, Properties props, String fallback) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        if (envKey != null) {
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                return envValue.trim();
            }
        }
        String fileValue = props.getProperty(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue.trim();
        }
        return fallback;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            LOG.warning("Ignoring non-numeric timeout value '" + raw + "'; using " + fallback + ".");
            return fallback;
        }
    }

    /**
     * Opens a new physical connection to MySQL.
     *
     * @throws DatabaseAccessException if the driver is missing or the
     *         server cannot be reached. The original {@link SQLException}
     *         is preserved as the cause.
     */
    public Connection openConnection() throws DatabaseAccessException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new DatabaseAccessException(
                    "The MySQL JDBC driver is missing from the classpath.",
                    "com.mysql.cj.jdbc.Driver could not be loaded. Run 'mvn package' "
                            + "so the connector is on the classpath.", e);
        }

        try {
            return DriverManager.getConnection(jdbcUrl, username, password);
        } catch (SQLException e) {
            throw new DatabaseAccessException("connect to the database", e);
        }
    }

    /**
     * Verifies the database is reachable. Called at start-up so the user
     * is told immediately if MySQL is not running, instead of getting a
     * confusing error later.
     */
    public boolean testConnection() {
        try (Connection connection = openConnection()) {
            // isValid() is a real round-trip, so this proves the server
            // answers rather than merely that a socket opened.
            return connection.isValid(connectTimeoutSeconds);
        } catch (DatabaseAccessException e) {
            LOG.log(java.util.logging.Level.SEVERE, "Database connectivity test failed.", e);
            return false;
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.SEVERE,
                    "Database connectivity test failed while validating the connection.", e);
            return false;
        }
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /** A safe, password-free description for the log and the UI status bar. */
    public String describeSafely() {
        return "jdbcUrl=" + jdbcUrl + ", username=" + username
                + ", password=<redacted>";
    }

    /**
     * True when the real (git-ignored) configuration file is present, so
     * the start-up screen can tell the user whether they still need to
     * create it.
     */
    public static boolean isLocalConfigPresent() {
        return onClasspath(CONFIG_FILE);
    }

    private static boolean onClasspath(String resourceName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader.getResource(resourceName) != null) {
            return true;
        }
        Path asFile = Paths.get("src", "main", "resources", resourceName);
        return Files.exists(asFile);
    }
}
