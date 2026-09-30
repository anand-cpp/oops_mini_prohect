package com.academic.management.service;

import com.academic.management.dao.UserDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.AuthenticationException;
import com.academic.management.model.User;
import com.academic.management.util.PasswordHasher;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Verifies sign-in credentials.
 *
 * <h2>Deliberate simplicity</h2>
 * This is a single-machine academic desktop application, not a bank. There
 * are no sessions to expire, no lockout counters and no password-reset
 * email, because none of those would mean anything without a server. What
 * is <em>not</em> simplified away is the part that actually matters: the
 * password is never stored, never logged, never compared as text, and
 * never leaves this class in plain form.
 *
 * <h2>Not leaking which half was wrong</h2>
 * An unknown username and a wrong password produce the identical message.
 * Distinguishing them would let anyone confirm which usernames exist, which
 * is a small but real information leak; the log records the real reason
 * for an administrator while the user is told only that the sign-in
 * failed.
 */
public class AuthenticationService extends BaseService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserDao userDao;

    public AuthenticationService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    protected String entityName() {
        return "User account";
    }

    /**
     * Authenticates a user.
     *
     * @param username the typed username
     * @param password the typed password, in plain text; it is hashed here
     *                 and the local variable is never stored or logged
     * @return the authenticated user
     * @throws AuthenticationException if either credential is wrong, or the
     *                                 account exists but is deactivated
     */
    public User authenticate(String username, String password) throws AppException {
        String trimmedUsername = username == null ? "" : username.trim();

        if (trimmedUsername.isEmpty() || password == null || password.isEmpty()) {
            // Do not hit the database for an obviously incomplete form.
            throw failed("Username and password are both required.");
        }

        Optional<User> found = userDao.findByUsername(trimmedUsername);
        if (found.isEmpty()) {
            logger.log(Level.WARNING, "Sign-in attempted for unknown username '"
                    + trimmedUsername + "'.");
            throw failed("Invalid username or password.");
        }

        User user = found.get();
        if (!PasswordHasher.matches(user.getSalt(), password, user.getPasswordHash())) {
            logger.log(Level.WARNING, "Sign-in attempted for '" + trimmedUsername
                    + "' with an incorrect password.");
            throw failed("Invalid username or password.");
        }

        if (!user.isActive()) {
            logger.log(Level.WARNING, "Sign-in refused: account '" + trimmedUsername
                    + "' is deactivated.");
            throw new AuthenticationException("This account has been deactivated.",
                    "User " + user.getUserId() + " is not active");
        }

        log("User '" + user.getUsername() + "' signed in as " + user.getRole());
        return user;
    }

    /**
     * Changes a user's own password after verifying the current one.
     *
     * @param username        the account being changed
     * @param currentPassword the existing password
     * @param newPassword     the replacement
     * @throws AppException if the current password is wrong, the new one is
     *                      too short, or it matches the old one
     */
    public void changePassword(String username, String currentPassword, String newPassword)
            throws AppException {
        User user = requireFound(userDao.findByUsername(username.trim()), username.trim());

        if (!PasswordHasher.matches(user.getSalt(), currentPassword, user.getPasswordHash())) {
            throw new AuthenticationException("The current password is incorrect.",
                    "Password change refused for " + user.getUserId());
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthenticationException(
                    "The new password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
                    "Rejected password of length "
                            + (newPassword == null ? 0 : newPassword.length()));
        }
        if (PasswordHasher.matches(user.getSalt(), newPassword, user.getPasswordHash())) {
            throw new AuthenticationException("The new password must be different from the old one.",
                    "Password change refused for " + user.getUserId() + ": unchanged");
        }

        // A fresh salt per password means two users choosing the same
        // password still get different stored hashes.
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(salt, newPassword);
        if (!userDao.updatePassword(user.getUserId(), salt, hash)) {
            throw new AuthenticationException("The password could not be changed.",
                    "No row updated for user " + user.getUserId());
        }
        log("Password changed for user '" + user.getUsername() + "'");
    }

    /** True when at least one account exists, so the login screen is usable. */
    public boolean hasAnyAccount() throws AppException {
        return userDao.count() > 0;
    }

    private AuthenticationException failed(String message) {
        return new AuthenticationException(message, "Authentication failed: " + message);
    }
}
