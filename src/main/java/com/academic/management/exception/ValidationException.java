package com.academic.management.exception;

/**
 * Raised when user-supplied input fails a validation rule
 * (empty name, malformed email, marks outside the allowed range, ...).
 */
public class ValidationException extends AppException {

    private static final long serialVersionUID = 1L;

    /** The specific field at fault, or {@code null} when not field-specific. */
    private final String field;

    public ValidationException(String field, String userMessage) {
        super(userMessage, "Validation failed for field '" + field + "': " + userMessage);
        this.field = field;
    }

    public ValidationException(String userMessage) {
        this(null, userMessage);
    }

    public String getField() {
        return field;
    }
}
