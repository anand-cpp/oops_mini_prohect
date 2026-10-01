package com.academic.management.ui.common;

import com.academic.management.model.Gender;
import com.academic.management.util.Validator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Display formatting shared by every screen.
 *
 * <h2>Why formatting lives here</h2>
 * The same value - a percentage, a date, a nullable name - appears on half
 * a dozen screens, and formatting it at each call site is how one screen
 * ends up showing "72.5" where another shows "72.50". Every number the user
 * reads therefore passes through here, which is also where the rounding
 * rule lives: two decimal places, with whole values shown without a
 * pointless ".0".
 */
public final class UiSupport {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    /** Accepts "15 Jan 2004" and "15 Jan 04", the two shapes a user types. */
    private static final DateTimeFormatter DISPLAY_PARSE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter STORAGE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    /** Shown in place of a value the database has not got. */
    public static final String NONE = "—";

    private UiSupport() {
        // static helpers only
    }

    // ------------------------------------------------------------------
    // Dates
    // ------------------------------------------------------------------

    /** {@code 15 Jan 2004}, or an em dash when the date is null. */
    public static String date(LocalDate value) {
        return value == null ? NONE : DISPLAY_DATE.format(value);
    }

    /**
     * {@code 15 Jan 2004} as text, and the same format accepted back.
     *
     * <p>Reading what the screen wrote is what lets an edit form round-trip
     * a date without the user retyping it, and parsing the display format
     * rather than ISO is what makes that work.
     */
    public static String dateForEditing(LocalDate value) {
        return value == null ? "" : DISPLAY_DATE.format(value);
    }

    /**
     * Accepts a display date, an ISO date, or a human phrase such as
     * "today", and returns the date it means.
     *
     * <p>Being generous here is deliberate: the alternative is rejecting a
     * reasonable thing a user typed. {@code null} means "leave as it was",
     * which is how an optional date is cleared.
     *
     * @throws com.academic.management.exception.ValidationException if the
     *         text cannot be understood
     */
    public static LocalDate parseFlexibleDate(String field, String text)
            throws com.academic.management.exception.ValidationException {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.ENGLISH);
        if (lower.equals("today") || lower.equals("now")) {
            return LocalDate.now();
        }
        if (lower.equals("yesterday")) {
            return LocalDate.now().minusDays(1);
        }
        if (lower.equals("tomorrow")) {
            return LocalDate.now().plusDays(1);
        }
        if (trimmed.matches("\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}")) {
            // "15 Jan 2004": the year-month-day order the display uses.
            try {
                return LocalDate.parse(trimmed, DISPLAY_PARSE_FORMAT);
            } catch (DateTimeParseException ignored) {
                // fall through to the ISO attempt for a clearer error
            }
        }
        if (trimmed.contains("/")) {
            trimmed = trimmed.replace('/', '-');
        }
        if (trimmed.matches("\\d{1,2}-\\d{1,2}-\\d{4}")) {
            // "15-1-2004" is the ISO order with a flexible day and month.
            String[] parts = trimmed.split("-");
            trimmed = String.format("%04d-%02d-%02d", Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        }
        return Validator.requireDate(field, trimmed);
    }

    /** Today in the display format, for pre-filling a new record's date. */
    public static String todayForEditing() {
        return DISPLAY_DATE.format(LocalDate.now());
    }

    /** {@code 15 Jan 2024, 09:30}, for the status bar. */
    public static String timestamp(LocalDateTime value) {
        return value == null ? NONE : TIMESTAMP.format(value);
    }

    // ------------------------------------------------------------------
    // Numbers
    // ------------------------------------------------------------------

    /**
     * A number with at most two decimals and no trailing zeros, so a mark
     * of 36 reads "36" rather than "36.00".
     */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return NONE;
        }
        return Validator.trimNumber(Math.round(value * 100.0) / 100.0);
    }

    /** A number always showing two decimals, for a marks column. */
    public static String marks(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return NONE;
        }
        return String.format(Locale.ENGLISH, "%.2f", value);
    }

    /** A percentage with one decimal, or an em dash when not applicable. */
    public static String percent(Double value) {
        return value == null ? NONE : String.format(Locale.ENGLISH, "%.1f%%", value);
    }

    /** A percentage with no decimal, for a headline figure. */
    public static String percent(double value) {
        return String.format(Locale.ENGLISH, "%.0f%%", value);
    }

    /** An integer count with a correctly pluralised noun. */
    public static String count(long value, String singular, String plural) {
        return value + " " + (value == 1 ? singular : plural);
    }

    /** Credits, which the domain allows to be fractional. */
    public static String credits(double value) {
        return number(value) + (value == 1 ? " credit" : " credits");
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /** The value, or an em dash when null or blank. */
    public static String orNone(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }

    /** The value with its first letter capitalised. */
    public static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ENGLISH) + value.substring(1);
    }

    /** The gender's display label, for example "Female". */
    public static String gender(Gender value) {
        return value == null ? NONE : value.getLabel();
    }

    /** {@code Pass} or {@code Fail}, coloured later by the table renderer. */
    public static String passFail(boolean pass) {
        return pass ? "Pass" : "Fail";
    }

    /** A date of birth as {@code 15 Jan 2004 (21)}, or an em dash. */
    public static String ageFrom(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return NONE;
        }
        int age = java.time.Period.between(dateOfBirth, LocalDate.now()).getYears();
        return date(dateOfBirth) + " (" + age + ")";
    }

    /** Splits a long message into lines no wider than {@code width}. */
    public static String wrap(String message, int width) {
        if (message == null || message.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String paragraph : message.split("\n")) {
            if (out.length() > 0) {
                out.append('\n');
            }
            int lineLength = 0;
            for (String word : paragraph.split("\\s+")) {
                if (lineLength > 0 && lineLength + word.length() > width) {
                    out.append('\n');
                    lineLength = 0;
                } else if (lineLength > 0) {
                    out.append(' ');
                    lineLength++;
                }
                out.append(word);
                lineLength += word.length();
            }
        }
        return out.toString();
    }

    /** Formats a value for a storage-format date column. */
    public static String toIso(LocalDate value) {
        return value == null ? null : STORAGE_DATE.format(value);
    }
}
