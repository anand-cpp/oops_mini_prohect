package com.academic.management.util;

import com.academic.management.exception.ValidationException;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/**
 * Field-level input validation, shared by the service layer and the UI.
 *
 * <p>Methods throw {@link ValidationException} carrying a message that is
 * safe to show directly in a dialog, so the same rule never has to be
 * written twice (once to colour the field red and again to reject the
 * save).
 */
public final class Validator {

    // Deliberately pragmatic rather than RFC 5322 complete.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Digits, spaces and the usual separators, 7 to 15 characters.
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[0-9+()\\-\\s]{7,15}$");

    private static final Pattern ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{3,20}$");

    private static final Pattern COURSE_CODE_PATTERN =
            Pattern.compile("^[A-Za-z]{2,10}[0-9]{2,6}$");

    private Validator() {
        // utility class
    }

    public static String requireText(String field, String value) throws ValidationException {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(field, field + " is required.");
        }
        return value.trim();
    }

    public static String requireText(String field, String value, int maxLength)
            throws ValidationException {
        String trimmed = requireText(field, value);
        if (trimmed.length() > maxLength) {
            throw new ValidationException(field,
                    field + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static String requireIdentifier(String field, String value)
            throws ValidationException {
        String trimmed = requireText(field, value);
        if (!ID_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(field,
                    field + " must be 3-20 characters using letters, digits, '-' or '_'.");
        }
        return trimmed.toUpperCase();
    }

    public static String requireEmail(String field, String value) throws ValidationException {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(field, field + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 120) {
            throw new ValidationException(field, field + " must be at most 120 characters.");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(field,
                    "'" + trimmed + "' is not a valid email address.");
        }
        return trimmed.toLowerCase();
    }

    /** Phone is optional, but must look like a phone number when supplied. */
    public static String optionalPhone(String field, String value) throws ValidationException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(field,
                    field + " must be 7-15 characters using digits, spaces, '+', '-' or "
                            + "parentheses.");
        }
        return trimmed;
    }

    public static String optionalText(String field, String value, int maxLength)
            throws ValidationException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return requireText(field, value, maxLength);
    }

    public static String requireCourseCode(String field, String value)
            throws ValidationException {
        String trimmed = requireText(field, value);
        if (!COURSE_CODE_PATTERN.matcher(trimmed.toUpperCase()).matches()) {
            throw new ValidationException(field,
                    field + " must look like CS301 (2-10 letters followed by 2-6 digits).");
        }
        return trimmed.toUpperCase();
    }

    public static int requireSemester(String field, String value) throws ValidationException {
        int parsed = requireInt(field, value);
        if (parsed < Constants.MIN_SEMESTER || parsed > Constants.MAX_SEMESTER) {
            throw new ValidationException(field, field + " must be between "
                    + Constants.MIN_SEMESTER + " and " + Constants.MAX_SEMESTER + ".");
        }
        return parsed;
    }

    public static int requireInt(String field, String value) throws ValidationException {
        String trimmed = requireText(field, value);
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new ValidationException(field, field + " must be a whole number.");
        }
    }

    /** Accepts integers and decimals, rejects everything else. */
    public static double requireDouble(String field, String value)
            throws ValidationException {
        String trimmed = requireText(field, value);
        try {
            double parsed = Double.parseDouble(trimmed);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new ValidationException(field, field + " must be a real number.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ValidationException(field, field + " must be a number.");
        }
    }

    public static double requireMarks(String field, String value, double max)
            throws ValidationException {
        double marks = requireDouble(field, value);
        if (marks < 0) {
            throw new ValidationException(field, field + " cannot be negative.");
        }
        if (marks > max) {
            throw new ValidationException(field,
                    field + " must be between 0 and " + trimNumber(max) + ".");
        }
        return marks;
    }

    public static LocalDate requireDate(String field, String value) throws ValidationException {
        String trimmed = requireText(field, value);
        try {
            return LocalDate.parse(trimmed);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ValidationException(field,
                    field + " must be a date in YYYY-MM-DD format.");
        }
    }

    /** A date of birth must be in the past and imply a plausible age. */
    public static LocalDate requireDateOfBirth(String field, String value)
            throws ValidationException {
        LocalDate dob = requireDate(field, value);
        LocalDate today = LocalDate.now();
        if (!dob.isBefore(today)) {
            throw new ValidationException(field, field + " must be in the past.");
        }
        int age = Period.between(dob, today).getYears();
        if (age < Constants.MIN_AGE_YEARS) {
            throw new ValidationException(field, field + " implies an age of " + age
                    + "; the person must be at least " + Constants.MIN_AGE_YEARS + ".");
        }
        if (age > Constants.MAX_AGE_YEARS) {
            throw new ValidationException(field, field + " implies an age of " + age
                    + "; please check the year.");
        }
        return dob;
    }

    /** Validates the 0 <= attended <= held relationship. */
    public static void requireAttendanceCounts(int held, int attended)
            throws ValidationException {
        if (held < 0) {
            throw new ValidationException("Classes Held", "Classes held cannot be negative.");
        }
        if (attended < 0) {
            throw new ValidationException("Classes Attended",
                    "Classes attended cannot be negative.");
        }
        if (held > 10_000) {
            throw new ValidationException("Classes Held",
                    "Classes held must be 10,000 or fewer.");
        }
        if (attended > held) {
            throw new ValidationException("Classes Attended",
                    "Classes attended (" + attended + ") cannot exceed classes held ("
                            + held + ").");
        }
    }

    /** Renders 40.0 as "40" and 3.5 as "3.5" for tidy error messages. */
    public static String trimNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public static boolean isValidEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value.trim()).matches();
    }
}
