package com.academic.management.util;

/**
 * Application-wide constants.
 *
 * <p>Kept in one place so no "magic number" is scattered through the
 * services, DAOs or UI.
 */
public final class Constants {

    private Constants() {
        // constants holder
    }

    // ---------------- Application ----------------
    public static final String APP_NAME = "Student Academic and Course Management System";
    public static final String APP_VERSION = "1.0.0";
    public static final String LOG_DIRECTORY = "logs";

    // ---------------- Marks split ----------------
    /** Maximum internal (sessional) marks for a course. */
    public static final double MAX_INTERNAL_MARKS = 40.0;
    /** Maximum external (university) marks for a course. */
    public static final double MAX_EXTERNAL_MARKS = 60.0;
    /** Marks are always reported out of this total. */
    public static final double TOTAL_MARKS = MAX_INTERNAL_MARKS + MAX_EXTERNAL_MARKS;

    // ---------------- Attendance ----------------
    /** Attendance at or above this percentage is considered regular. */
    public static final double ATTENDANCE_REQUIRED_PERCENT = 75.0;
    /** Percentage reported when no classes have been held yet. */
    public static final double NO_ATTENDANCE_PERCENT = 0.0;

    // ---------------- Pass criteria ----------------
    public static final double PASS_PERCENTAGE = 40.0;

    // ---------------- Domain ranges ----------------
    public static final int MIN_SEMESTER = 1;
    public static final int MAX_SEMESTER = 10;
    public static final int MAX_CREDITS = 10;
    public static final int MIN_AGE_YEARS = 15;
    public static final int MAX_AGE_YEARS = 100;

    // ---------------- SQL fragments used by several DAOs ----------------
    /** MySQL error code for a duplicate-key (unique index) violation. */
    public static int MYSQL_DUPLICATE_KEY = 1062;
}
