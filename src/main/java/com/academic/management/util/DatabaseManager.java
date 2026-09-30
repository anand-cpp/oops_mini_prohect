package com.academic.management.util;

import com.academic.management.exception.DatabaseAccessException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Single entry point for obtaining JDBC connections.
 *
 * <p>Centralising this means the driver is loaded once, credentials live
 * in exactly one object, and every DAO receives a connection instead of
 * building its own. Connections are still opened per unit of work and
 * closed by try-with-resources, so this class deliberately does not
 * implement a pool - for a single-user desktop application that would be
 * premature complexity.
 */
public final class DatabaseManager {

    private static final Logger LOG = AppLogger.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;

    private final DbConfig config;
    private volatile boolean verified;

    private DatabaseManager(DbConfig config) {
        this.config = config;
    }

    /**
     * Lazily creates the singleton. The first call reads the configuration
     * file; later calls reuse the same instance.
     */
    public static synchronized DatabaseManager getInstance() throws DatabaseAccessException {
        if (instance == null) {
            DbConfig config = DbConfig.load();
            instance = new DatabaseManager(config);
            LOG.info("Database configuration loaded: " + config.describeSafely());
        }
        return instance;
    }

    /** Replaces the singleton. Intended for tests only. */
    public static synchronized void resetForTests() {
        instance = null;
    }

    /**
     * Opens a fresh connection. The caller owns it and must close it,
     * which is what try-with-resources does.
     */
    public Connection getConnection() throws DatabaseAccessException {
        return config.openConnection();
    }

    /**
     * Verifies connectivity exactly once per run and logs the outcome.
     *
     * @return {@code true} when MySQL answered.
     */
    public boolean verifyConnectivity() {
        if (verified) {
            return true;
        }
        try (Connection connection = config.openConnection()) {
            String serverVersion = connection.getMetaData().getDatabaseProductVersion();
            LOG.info("Connected to MySQL " + serverVersion + " ("
                    + connection.getCatalog() + ")");
            verified = true;
            return true;
        } catch (DatabaseAccessException e) {
            LOG.log(java.util.logging.Level.SEVERE, "MySQL connectivity check failed.", e);
            return false;
        } catch (SQLException e) {
            // close() or metadata access failed
            LOG.log(java.util.logging.Level.SEVERE,
                    "MySQL connectivity check failed while reading metadata.", e);
            return false;
        }
    }

    /** Human-readable server version, or {@code "unknown"}. */
    public String getServerVersion() {
        try (Connection connection = config.openConnection()) {
            return connection.getMetaData().getDatabaseProductVersion();
        } catch (DatabaseAccessException | SQLException e) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Could not read the server version.", e);
            return "unknown";
        }
    }

    public DbConfig getConfig() {
        return config;
    }
}
