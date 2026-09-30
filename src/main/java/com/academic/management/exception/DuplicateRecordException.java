package com.academic.management.exception;

/**
 * Raised when an insert or update would break a uniqueness rule
 * (duplicate student id, duplicate course code, duplicate enrollment,
 * duplicate email address).
 */
public class DuplicateRecordException extends AppException {

    private static final long serialVersionUID = 1L;

    public DuplicateRecordException(String userMessage, String technicalMessage) {
        super(userMessage, technicalMessage);
    }

    public DuplicateRecordException(String entity, String attribute, String value) {
        super(entity + " with " + attribute + " '" + value + "' already exists.",
              "Uniqueness violation on " + entity + "." + attribute + " = " + value);
    }
}
