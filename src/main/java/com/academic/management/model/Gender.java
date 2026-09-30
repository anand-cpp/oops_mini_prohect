package com.academic.management.model;

/**
 * Gender values accepted by the system.
 *
 * <p>The {@link #getLabel()} form is what the UI shows; the enum name is
 * what gets written to the database {@code ENUM} column.
 */
public enum Gender {

    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Case-insensitive lookup used when reading a value back from the
     * database.
     *
     * @return the matching gender, or {@link #OTHER} when unrecognised, so
     *         a surprising legacy row cannot break the whole listing
     */
    public static Gender fromLabel(String value) {
        if (value == null) {
            return OTHER;
        }
        for (Gender gender : values()) {
            if (gender.name().equalsIgnoreCase(value.trim())) {
                return gender;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
