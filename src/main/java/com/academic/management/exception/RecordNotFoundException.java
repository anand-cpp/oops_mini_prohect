package com.academic.management.exception;

/**
 * Raised when a record that the caller explicitly asked for does not
 * exist (unknown student id, unknown course code, ...).
 */
public class RecordNotFoundException extends AppException {

    private static final long serialVersionUID = 1L;

    public RecordNotFoundException(String entity, String identifier) {
        super(entity + " '" + identifier + "' was not found.",
              "No row found in " + entity + " for identifier '" + identifier + "'");
        this.entity = entity;
        this.identifier = identifier;
    }

    private final String entity;
    private final String identifier;

    public String getEntity() {
        return entity;
    }

    public String getIdentifier() {
        return identifier;
    }
}
