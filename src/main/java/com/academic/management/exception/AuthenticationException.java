package com.academic.management.exception;

/**
 * Raised when a sign-in attempt fails because the username is unknown,
 * the password does not match, or the account has been deactivated.
 */
public class AuthenticationException extends AppException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String userMessage, String technicalMessage) {
        super(userMessage, technicalMessage);
    }
}
