package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.Validator;

import java.time.LocalDate;

/**
 * A person who studies at the institution.
 *
 * <p>{@code Student is-a Person}: it inherits the identifier, name, date
 * of birth, gender and contact details, and adds the state that is
 * specific to studying.
 *
 * <p>The class is {@code final} because a student is a complete domain
 * type: nothing should extend it, and closing the hierarchy means
 * {@link #validateSubtype()} can never be re-implemented in a way that
 * would observe a half-built object.
 */
public final class Student extends Person {

    public static final String ID_PREFIX = "STU";
    private static final int MAX_DEPARTMENT_LENGTH = 80;
    private static final int MAX_GUARDIAN_LENGTH = 20;

    private String department;
    private int semester;
    private LocalDate admissionDate;
    private String guardianContact;

    /**
     * @param studentId       unique student identifier, for example {@code STU001}
     * @param name            full name
     * @param dateOfBirth     date of birth
     * @param gender          gender
     * @param email           unique email address
     * @param phone           phone number, may be {@code null}
     * @param address         address, may be {@code null}
     * @param department      owning department
     * @param semester        current semester, 1 to 10
     * @param admissionDate   date of admission
     * @param guardianContact guardian/emergency contact, may be {@code null}
     * @throws ValidationException if any value breaks a validation rule
     */
    public Student(String studentId, String name, LocalDate dateOfBirth, Gender gender,
                   String email, String phone, String address, String department,
                   int semester, LocalDate admissionDate, String guardianContact)
            throws ValidationException {
        super(studentId, name, dateOfBirth, gender, email, phone, address);
        this.department = Validator.requireText("Department", department, MAX_DEPARTMENT_LENGTH);
        this.semester = semester;
        this.admissionDate = admissionDate;
        this.guardianContact = Validator.optionalPhone("Guardian Contact", guardianContact);
        validateSubtype();
    }

    /** Convenience constructor without the optional guardian contact. */
    public Student(String studentId, String name, LocalDate dateOfBirth, Gender gender,
                   String email, String phone, String address, String department,
                   int semester, LocalDate admissionDate)
            throws ValidationException {
        this(studentId, name, dateOfBirth, gender, email, phone, address,
                department, semester, admissionDate, null);
    }

    // ------------------------------------------------------------------
    // Person contract
    // ------------------------------------------------------------------

    @Override
    public String getRoleLabel() {
        return "Student";
    }

    @Override
    public String describe() {
        return "Student " + getId() + " - " + getName()
                + " (" + department + ", semester " + semester + ")";
    }

    /**
     * {@code final} on purpose: a student has no further subtypes, so the
     * rules here are complete and must not be re-implemented downstream.
     */
    @Override
    protected final void validateSubtype() throws ValidationException {
        if (semester < com.academic.management.util.Constants.MIN_SEMESTER
                || semester > com.academic.management.util.Constants.MAX_SEMESTER) {
            throw new ValidationException("Semester", "Semester must be between "
                    + com.academic.management.util.Constants.MIN_SEMESTER + " and "
                    + com.academic.management.util.Constants.MAX_SEMESTER + ".");
        }
        if (admissionDate != null && admissionDate.isAfter(LocalDate.now())) {
            throw new ValidationException("Admission Date",
                    "Admission date cannot be in the future.");
        }
        if (admissionDate != null && getDateOfBirth() != null
                && admissionDate.isBefore(getDateOfBirth())) {
            throw new ValidationException("Admission Date",
                    "Admission date cannot be before the student's date of birth.");
        }
    }

    @Override
    public String getIdPrefix() {
        return ID_PREFIX;
    }

    /**
     * Overridden to add the academic summary on top of the inherited
     * display name. Calling this through a {@code Person} reference
     * produces different text depending on the runtime type.
     */
    @Override
    public String getDisplayName() {
        return super.getDisplayName() + " - " + department + " (Sem " + semester + ")";
    }

    // ------------------------------------------------------------------
    // Student-specific state
    // ------------------------------------------------------------------

    public String getStudentId() {
        return getId();
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) throws ValidationException {
        this.department = Validator.requireText("Department", department, MAX_DEPARTMENT_LENGTH);
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) throws ValidationException {
        int previous = this.semester;
        this.semester = semester;
        try {
            validateSubtype();
        } catch (ValidationException e) {
            this.semester = previous;
            throw e;
        }
    }

    public LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(LocalDate admissionDate) throws ValidationException {
        LocalDate previous = this.admissionDate;
        this.admissionDate = admissionDate;
        try {
            validateSubtype();
        } catch (ValidationException e) {
            this.admissionDate = previous;
            throw e;
        }
    }

    public String getGuardianContact() {
        return guardianContact;
    }

    public void setGuardianContact(String guardianContact) throws ValidationException {
        this.guardianContact = Validator.optionalPhone("Guardian Contact", guardianContact);
    }

    /** Years since admission, or 0 when the admission date is unknown. */
    public int getYearsSinceAdmission() {
        if (admissionDate == null) {
            return 0;
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
                admissionDate, LocalDate.now());
        return (int) Math.max(0, months / 12);
    }
}
