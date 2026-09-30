package com.academic.management.dao.impl;

import com.academic.management.dao.AbstractDao;
import com.academic.management.dao.UserDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DatabaseAccessException;
import com.academic.management.model.User;
import com.academic.management.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * JDBC implementation of {@link UserDao}.
 *
 * <h2>Password handling</h2>
 * The {@code password_hash} and {@code salt} columns are read and written
 * as opaque strings. This DAO never hashes, never compares, and never
 * sees a plain-text password: that is
 * {@link com.academic.management.util.PasswordHasher}'s job, called by the
 * service layer. Keeping the boundary here means there is exactly one
 * place in the application where a password is transformed, and it cannot
 * be bypassed by a new caller.
 */
public class UserDaoImpl extends AbstractDao implements UserDao {

    private static final String TABLE = "users";

    private static final String COLUMNS =
            "user_id, username, full_name, role, is_active, password_hash, salt";

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM " + TABLE;

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE
                    + " (user_id, username, full_name, role, is_active, password_hash, salt) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_PASSWORD_SQL =
            "UPDATE " + TABLE + " SET password_hash = ?, salt = ? WHERE user_id = ?";

    private static final String COUNT_SQL =
            "SELECT COUNT(*) FROM " + TABLE;

    public UserDaoImpl(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public Optional<User> findByUsername(String username) throws AppException {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = SELECT_SQL + " WHERE username = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not look up the user account.", e);
        }
    }

    @Override
    public void insert(User user) throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, user.getUserId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getFullName());
            statement.setString(4, user.getRole().name());
            statement.setBoolean(5, user.isActive());
            statement.setString(6, user.getPasswordHash());
            statement.setString(7, user.getSalt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw translate("inserting user " + user.getUsername(), e,
                    new DatabaseAccessException("Could not create the user account.", e));
        }
    }

    @Override
    public boolean updatePassword(String userId, String salt, String passwordHash)
            throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD_SQL)) {
            statement.setString(1, passwordHash);
            statement.setString(2, salt);
            statement.setString(3, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("updating the password for user " + userId, e,
                    new DatabaseAccessException("Could not change the password.", e));
        }
    }

    @Override
    public long count() throws AppException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Could not count user accounts.", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("full_name"),
                User.Role.fromLabel(rs.getString("role")),
                rs.getBoolean("is_active"),
                rs.getString("password_hash"),
                rs.getString("salt"));
    }
}
