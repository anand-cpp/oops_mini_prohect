package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.User;

import java.util.Optional;

/**
 * Persistence contract for sign-in accounts.
 */
public interface UserDao {

    /**
     * Looks up a user by username. Returns empty for an unknown username
     * so the caller can report "invalid credentials" without revealing
     * which half was wrong.
     */
    Optional<User> findByUsername(String username) throws AppException;

    void insert(User user) throws AppException;

    /**
     * Replaces the stored hash and salt for a user.
     *
     * @return {@code true} when a row was updated
     */
    boolean updatePassword(String userId, String salt, String passwordHash) throws AppException;

    long count() throws AppException;
}
