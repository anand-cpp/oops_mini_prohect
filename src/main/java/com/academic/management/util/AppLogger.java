package com.academic.management.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Application-wide logger.
 *
 * <p>Technical detail (SQL, stack traces) goes here; the user interface
 * only ever shows the friendly message carried by the
 * {@link com.academic.management.exception.AppException}. Nothing is
 * silently discarded: if the log file cannot be created the logger falls
 * back to the console and reports that fact instead of failing quietly.
 */
public final class AppLogger {

    private static final Logger ROOT = Logger.getLogger("com.academic.management");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private AppLogger() {
        // utility class
    }

    static {
        ROOT.setUseParentHandlers(false);
        ROOT.setLevel(Level.ALL);

        for (Handler handler : ROOT.getHandlers()) {
            ROOT.removeHandler(handler);
        }

        ROOT.addHandler(new ConsoleHandler());
        addFileHandler();
    }

    private static void addFileHandler() {
        Path dir = Paths.get(Constants.LOG_DIRECTORY);
        try {
            Files.createDirectories(dir);
            FileHandler fileHandler = new FileHandler(
                    dir.resolve("application-%g.log").toString(),
                    5 * 1024 * 1024, 3, true);
            fileHandler.setFormatter(new PlainFormatter());
            ROOT.addHandler(fileHandler);
        } catch (IOException e) {
            // Never let a logging problem stop the application starting.
            ROOT.log(Level.WARNING,
                    "Could not create log directory '" + dir + "'; "
                            + "logging to console only. Cause: " + e.getMessage());
        }
    }

    public static Logger getLogger(Class<?> type) {
        return Logger.getLogger(type.getName());
    }

    /** Logs a message together with the full stack trace of {@code error}. */
    public static void logError(Logger logger, String message, Throwable error) {
        logger.log(Level.SEVERE, message, error);
    }

    /** Renders a throwable's stack trace as a string, for nested messages. */
    public static String stackTraceOf(Throwable error) {
        if (error == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    public static String timestamp() {
        return LocalDateTime.now().format(STAMP);
    }

    /** Single-line log format: {@code 2026-01-01 10:00:00.000 LEVEL logger - message}. */
    private static final class PlainFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            StringBuilder line = new StringBuilder();
            line.append(LocalDateTime.now().format(STAMP))
                .append(' ').append(String.format("%-7s", record.getLevel().getName()))
                .append(' ').append(simpleName(record.getLoggerName()))
                .append(" - ").append(formatMessage(record))
                .append(System.lineSeparator());

            if (record.getThrown() != null) {
                StringWriter writer = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(writer));
                line.append(writer);
            }
            return line.toString();
        }

        private static String simpleName(String loggerName) {
            if (loggerName == null) {
                return "unknown";
            }
            int lastDot = loggerName.lastIndexOf('.');
            return lastDot < 0 ? loggerName : loggerName.substring(lastDot + 1);
        }
    }
}
