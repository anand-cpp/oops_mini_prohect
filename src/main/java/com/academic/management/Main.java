package com.academic.management;

import com.academic.management.exception.AppException;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.LoginFrame;
import com.academic.management.ui.common.Theme;
import com.academic.management.util.AppLogger;
import com.academic.management.util.Constants;
import com.academic.management.util.DatabaseManager;
import com.academic.management.util.DbConfig;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application entry point.
 *
 * <h2>What start-up is responsible for</h2>
 * Three things, in this order and no others:
 * <ol>
 *   <li>install the look and feel, so nothing paints with the platform
 *       default before the first window appears;</li>
 *   <li>check that MySQL is actually reachable, and say so plainly if it is
 *       not - a user staring at "cannot create connection" halfway through a
 *       form learns far less than one told at launch that the server is
 *       down and what to do about it;</li>
 *   <li>build the service graph and show the sign-in screen.</li>
 * </ol>
 * Nothing else happens here. There is no static mutable state, so a test
 * can build its own {@link ServiceRegistry} without going through this
 * class.
 */
public final class Main {

    private static final Logger LOGGER = AppLogger.getLogger(Main.class);

    private Main() {
        // entry point holder
    }

    public static void main(String[] args) {
        // The log directory is created by AppLogger's static initialiser,
        // which this call is what triggers; doing it first means even a
        // failure to start is recorded.
        LOGGER.info("Starting " + Theme.version());

        Theme.installLookAndFeel();

        // Everything Swing runs on the event dispatch thread. A desktop
        // application has no other thread of its own, so this belongs at
        // the boundary and nowhere else.
        SwingUtilities.invokeLater(() -> start());
    }

    private static void start() {
        try {
            DatabaseManager database = DatabaseManager.getInstance();

            if (!database.verifyConnectivity()) {
                showStartupFailure("Could not connect to MySQL", database);
                return;
            }
            LOGGER.info("Connected to MySQL " + database.getServerVersion()
                    + " at " + database.getConfig().getJdbcUrl());

            if (!hasSchema(database)) {
                showStartupFailure("The database is not initialised",
                        "Connected to MySQL " + database.getServerVersion() + ", but the "
                                + "'student_academic_management' database has no tables.\n\n"
                                + "Run database/schema.sql and then database/sample_data.sql:\n"
                                + "  mysql -u root -p < database/schema.sql\n"
                                + "  mysql -u root -p < database/sample_data.sql");
                return;
            }

            ServiceRegistry services = new ServiceRegistry(database);
            new LoginFrame(services).showLogin();
        } catch (AppException e) {
            LOGGER.log(Level.SEVERE, "Start-up failed.", e);
            JOptionPane.showMessageDialog(null, e.getMessage(), "Cannot start "
                    + Constants.APP_NAME, JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unexpected start-up failure.", e);
            JOptionPane.showMessageDialog(null,
                    "An unexpected internal error occurred while starting.\n\n"
                            + e.getMessage() + "\n\nSee logs/" + Constants.LOG_DIRECTORY
                            + " for the full detail.",
                    "Cannot start " + Constants.APP_NAME, JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Confirms the schema exists before the login screen is shown.
     *
     * <p>Checked by asking for one row from each table rather than by
     * reading {@code information_schema}: the latter would accept a
     * half-applied schema where some tables are missing, and the first
     * thing to fail would then be whichever screen happened to be opened
     * first.
     */
    private static boolean hasSchema(DatabaseManager database) {
        try (java.sql.Connection connection = database.getConnection()) {
            for (String table : new String[]{"students", "faculty", "courses", "users"}) {
                try (java.sql.Statement statement = connection.createStatement();
                     java.sql.ResultSet rows = statement.executeQuery(
                             "SELECT 1 FROM " + table + " LIMIT 1")) {
                    // Executing the query is the check; an absent table
                    // throws a SQLException naming that table.
                    rows.next();
                }
            }
            return true;
        } catch (java.sql.SQLException | AppException e) {
            // A missing table surfaces as SQLException; a refused connection
            // surfaces as DatabaseAccessException. Both mean the same thing
            // here - the schema is not usable - so the caller only needs one
            // answer rather than having to tell the two apart.
            LOGGER.log(Level.WARNING, "Schema check failed.", e);
            return false;
        }
    }

    /** Explains a connection failure in terms of what to do next. */
    private static void showStartupFailure(String title, DatabaseManager database) {
        String configuration = DbConfig.isLocalConfigPresent()
                ? "The configuration in src/main/resources/application.properties was used."
                : "No application.properties was found, so the defaults were used. Copy "
                        + "application.properties.example and fill in your MySQL password.";

        JOptionPane.showMessageDialog(null,
                title + "\n\n"
                        + "Expected: " + database.getConfig().describeSafely() + "\n"
                        + configuration + "\n\n"
                        + "Is the MySQL service running?",
                Constants.APP_NAME, JOptionPane.ERROR_MESSAGE);
    }

    /** Explains a missing schema. */
    private static void showStartupFailure(String title, String detail) {
        JOptionPane.showMessageDialog(null, detail, Constants.APP_NAME,
                JOptionPane.ERROR_MESSAGE);
    }

    /** The window size the application opens at. */
    public static Dimension preferredWindowSize() {
        return Theme.WINDOW;
    }

    /** The look and feel class in use, for the about box. */
    public static String lookAndFeelName() {
        return UIManager.getLookAndFeel() == null
                ? "platform default"
                : UIManager.getLookAndFeel().getName();
    }
}
