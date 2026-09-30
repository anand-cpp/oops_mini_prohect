package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.Validator;

import java.time.LocalDate;

/**
 * A member of the teaching staff.
 *
 * <p>{@code Faculty is-a Person}: the same identity and contact data as a
 * student, plus a department, a designation and an office.
 *
 * <p>{@code final} for the same reason as {@link Student}: the type is
 * complete and the hierarchy is closed.
 */
public final class Faculty extends Person {

    public static final String ID_PREFIX = "FAC";
    private static final int MAX_DEPARTMENT_LENGTH = 80;
    private static final int MAX_DESIGNATION_LENGTH = 60;
    private static final int MAX_OFFICE_LENGTH = 60;

    private String department;
    private String designation;
    private String officeLocation;
    private LocalDate joiningDate;

    /**
     * @param facultyId      unique faculty identifier, for example {@code FAC001}
     * @param name           full name
     * @param dateOfBirth    date of birth, may be {@code null}
     * @param gender         gender
     * @param email          unique email address
     * @param phone          phone number, may be {@code null}
     * @param address        address, may be {@code null}
     * @param department     owning department
     * @param designation    job title, for example {@code Professor}
     * @param officeLocation office room, may be {@code null}
     * @param joiningDate    date of joining, may be {@code null}
     * @throws ValidationException if any value breaks a validation rule
     */
    public Faculty(String facultyId, String name, LocalDate dateOfBirth, Gender gender,
                   String email, String phone, String address, String department,
                   String designation, String officeLocation, LocalDate joiningDate)
            throws ValidationException {
        super(facultyId, name, dateOfBirth, gender, email, phone, address);
        this.department = Validator.requireText("Department", department, MAX_DEPARTMENT_LENGTH);
        this.designation = Validator.requireText("Designation", designation,
                MAX_DESIGNATION_LENGTH);
        this.officeLocation = Validator.optionalText("Office Location", officeLocation,
                MAX_OFFICE_LENGTH);
        this.joiningDate = joiningDate;
        validateSubtype();
    }

    /** Convenience constructor without the optional office and joining date. */
    public Faculty(String facultyId, String name, LocalDate dateOfBirth, Gender gender,
                   String email, String phone, String address, String department,
                   String designation)
            throws ValidationException {
        this(facultyId, name, dateOfBirth, gender, email, phone, address,
                department, designation, null, null);
    }

    // ------------------------------------------------------------------
    // Person contract
    // ------------------------------------------------------------------

    @Override
    public String getRoleLabel() {
        return "Faculty";
    }

    @Override
    public String describe() {
        return "Faculty " + getId() + " - " + getName() + " (" + designation
                + ", " + department + ")";
    }

    /**
     * {@code final} on purpose: a faculty member has no further subtypes,
     * so the rules here are complete.
     */
    @Override
    protected final void validateSubtype() throws ValidationException {
        if (joiningDate != null && joiningDate.isAfter(LocalDate.now())) {
            throw new ValidationException("Joining Date",
                    "Joining date cannot be in the future.");
        }
        if (joiningDate != null && getDateOfBirth() != null
                && joiningDate.isBefore(getDateOfBirth())) {
            throw new ValidationException("Joining Date",
                    "Joining date cannot be before the faculty member's date of birth.");
        }
    }

    @Override
    public String getIdPrefix() {
        return ID_PREFIX;
    }

    @Override
    public String getDisplayName() {
        return super.getDisplayName() + " - " + designation + ", " + department;
    }

    // ------------------------------------------------------------------
    // Faculty-specific state
    // ------------------------------------------------------------------

    public String getFacultyId() {
        return getId();
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) throws ValidationException {
        this.department = Validator.requireText("Department", department, MAX_DEPARTMENT_LENGTH);
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) throws ValidationException {
        this.designation = Validator.requireText("Designation", designation,
                MAX_DESIGNATION_LENGTH);
    }

    public String getOfficeLocation() {
        return officeLocation;
    }

    public void setOfficeLocation(String officeLocation) throws ValidationException {
        this.officeLocation = Validator.optionalText("Office Location", officeLocation,
                MAX_OFFICE_LENGTH);
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) throws ValidationException {
        LocalDate previous = this.joiningDate;
        this.joiningDate = joiningDate;
        try {
            validateSubtype();
        } catch (ValidationException e) {
            this.joiningDate = previous;
            throw e;
        }
    }

    /** Years of service, or 0 when the joining date is unknown. */
    public int getYearsOfService() {
        if (joiningDate == null) {
            return 0;
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
                joiningDate, LocalDate.now());
        return (int) Math.max(0, months / 12);
    }
}
