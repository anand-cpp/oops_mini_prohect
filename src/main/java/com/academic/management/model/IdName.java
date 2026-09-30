package com.academic.management.model;

/**
 * An id/label pair used wherever a dropdown needs to list entities
 * without the caller needing their full state.
 *
 * <p>Using this instead of a partially-populated {@link Student} or
 * {@link Faculty} matters: those classes deliberately refuse to exist
 * without a valid email and date of birth, so filling one in "just for a
 * combo box" would mean inventing placeholder data. This record carries
 * exactly what is stored and nothing more.
 *
 * @param id    the entity's primary key
 * @param label the text shown in the dropdown
 */
public record IdName(String id, String label) {

    public IdName {
        id = id == null ? "" : id;
        label = label == null ? id : label;
    }

    @Override
    public String toString() {
        return label;
    }
}
