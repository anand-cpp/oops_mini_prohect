package com.academic.management.model;

import java.util.Objects;

/**
 * A sign-in account for the application.
 *
 * <p>Deliberately <em>not</em> a {@link Person}: a user account is a
 * credential, not a student or a staff record, so forcing it into the
 * person hierarchy would be inheritance for its own sake. The password
 * is never held as a plain string - only the stored hash and the salt
 * travel with this object.
 */
public class User {

    public enum Role {
        ADMIN("Administrator"),
        FACULTY("Faculty");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static Role fromLabel(String value) {
            if (value != null) {
                for (Role role : values()) {
                    if (role.name().equalsIgnoreCase(value.trim())) {
                        return role;
                    }
                }
            }
            return FACULTY;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final String userId;
    private final String username;
    private final String fullName;
    private final Role role;
    private final boolean active;
    private final String passwordHash;
    private final String salt;

    public User(String userId, String username, String fullName, Role role, boolean active,
                String passwordHash, String salt) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role == null ? Role.FACULTY : role;
        this.active = active;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isAdministrator() {
        return role == Role.ADMIN;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    /** The label shown in the dashboard header after sign-in. */
    public String getWelcomeName() {
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof User user && Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    /** Never includes the hash or the salt. */
    @Override
    public String toString() {
        return "User[" + userId + ", " + username + ", " + role + "]";
    }
}
