package com.academic.management.exception;

/**
 * Root of the application's checked exception hierarchy.
 *
 * <p>Every failure the application raises on purpose extends this class,
 * which lets the presentation layer catch a single type and show a
 * user-friendly message, while {@link #getTechnicalMessage()} is kept for
 * the log file so raw stack traces are never shown to a normal user.
 */
public abstract class AppException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Detailed, developer-facing context (for example the failing SQL). */
    private final String technicalMessage;

    protected AppException(String userMessage, String technicalMessage, Throwable cause) {
        super(userMessage, cause);
        this.technicalMessage = technicalMessage == null ? userMessage : technicalMessage;
    }

    protected AppException(String userMessage, String technicalMessage) {
        this(userMessage, technicalMessage, null);
    }

    /** The message that is safe to display in a dialog. */
    @Override
    public String getMessage() {
        return super.getMessage();
    }

    /**
     * Detailed context for the log file. Written to the log, never shown
     * in the user interface.
     */
    public String getTechnicalMessage() {
        return technicalMessage;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + getMessage()
                + " [" + technicalMessage + "]";
    }
}
