package com.academic.management.exception;

/**
 * Raised when a business rule is violated that is not a simple field
 * validation, for example deleting a course that still has enrolments
 * or recording attendance for a student who is not enrolled.
 */
public class BusinessRuleException extends AppException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String userMessage) {
        super(userMessage, userMessage);
    }

    public BusinessRuleException(String userMessage, String technicalMessage) {
        super(userMessage, technicalMessage);
    }
}
