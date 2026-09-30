package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.Validator;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Common supertype of every human being in the system.
 *
 * <h2>Why this is genuine inheritance</h2>
 * A {@link Student} and a {@link Faculty} member really are people who
 * share an identifier, a name, contact details, a date of birth and a
 * gender. Modelling that once here removes the duplication that would
 * otherwise appear in both subclasses and in both DAO row mappers.
 *
 * <h2>Abstraction</h2>
 * The class is abstract because the parts that differ - the identifier
 * prefix, the display title, the tabular summary - are left to the
 * subclasses through {@link #getRoleLabel()} and {@link #describe()}.
 * A {@code Person} reference therefore dispatches to student or faculty
 * behaviour at run time (polymorphism), which is exactly what
 * {@link #getIdPrefix()} is used for when generating a new identifier.
 *
 * <h2>Encapsulation</h2>
 * Every field is private. Nothing outside this class can put a student
 * into the system with an empty name or a malformed email, because those
 * checks live in {@link #validateAll()}.
 */
public abstract class Person implements Comparable<Person> {

    /** Shared validation limits, so both subclasses agree on them. */
    protected static final int MAX_NAME_LENGTH = 100;
    protected static final int MAX_EMAIL_LENGTH = 120;
    protected static final int MAX_PHONE_LENGTH = 20;
    protected static final int MAX_ADDRESS_LENGTH = 255;
    protected static final int MAX_ID_LENGTH = 20;

    private final String id;
    private String name;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String email;
    private String phone;
    private String address;

    /**
     * Canonical constructor. Validates the shared person fields inline.
     *
     * <p>It deliberately does <em>not</em> call {@link #validateSubtype()}:
     * an overridable method invoked here would run before the subclass
     * fields were assigned, so the override would inspect defaults. Each
     * subclass calls {@code validateSubtype()} at the end of its own
     * constructor instead.
     *
     * @param id         unique identifier; must not be blank
     * @param name       full name; must not be blank
     * @param dateOfBirth date of birth; must not be in the future
     * @param gender     gender
     * @param email      valid email address
     * @param phone      phone number, may be {@code null}
     * @param address    postal address, may be {@code null}
     * @throws ValidationException if the shared person rules are broken
     */
    protected Person(String id, String name, LocalDate dateOfBirth, Gender gender,
                     String email, String phone, String address)
            throws ValidationException {
        this.id = Validator.requireIdentifier("Id", id);
        this.name = Validator.requireText("Name", name, MAX_NAME_LENGTH);
        this.dateOfBirth = dateOfBirth == null ? null : dateOfBirth;
        this.gender = gender == null ? Gender.OTHER : gender;
        this.email = Validator.requireEmail("Email", email);
        this.phone = Validator.optionalPhone("Phone", phone);
        this.address = Validator.optionalText("Address", address, MAX_ADDRESS_LENGTH);
        checkDateOfBirthNotInFuture();
    }

    private void checkDateOfBirthNotInFuture() throws ValidationException {
        if (dateOfBirth != null && !dateOfBirth.isBefore(LocalDate.now())) {
            throw new ValidationException("Date Of Birth", "Date of birth must be in the past.");
        }
    }

    // ------------------------------------------------------------------
    // Abstract contract every subclass must satisfy
    // ------------------------------------------------------------------

    /**
     * Which kind of person this is, for example {@code "Student"}.
     * Used for headings, messages and the id generator.
     */
    public abstract String getRoleLabel();

    /**
     * One-line summary of the subtype-specific state. The base class
     * cannot know this, which is why it is abstract.
     */
    public abstract String describe();

    /**
     * Subtype-specific validation. Called by each subclass at the end of
     * its own constructor, and again after any mutation that could break
     * a cross-field rule, so a {@link Student} can never hold an
     * inconsistent state.
     *
     * @throws ValidationException if the subclass rules are broken
     */
    protected abstract void validateSubtype() throws ValidationException;

    /**
     * Re-runs the complete set of rules: the shared person rules plus the
     * polymorphic subtype rules. Used by the service layer to re-check an
     * object before it is written to the database.
     *
     * @throws ValidationException if any rule is broken
     */
    public final void validateAll() throws ValidationException {
        Validator.requireText("Name", name, MAX_NAME_LENGTH);
        Validator.requireEmail("Email", email);
        Validator.optionalPhone("Phone", phone);
        Validator.optionalText("Address", address, MAX_ADDRESS_LENGTH);
        checkDateOfBirthNotInFuture();
        validateSubtype();
    }

    /**
     * The prefix used when the application suggests a new identifier,
     * for example {@code "STU"}. Part of the polymorphic id generation.
     */
    public abstract String getIdPrefix();

    // ------------------------------------------------------------------
    // Shared, polymorphic behaviour
    // ------------------------------------------------------------------

    /**
     * Identity line shown in the UI, for example
     * {@code "STU001 - Arjun Krishnan"}.
     *
     * <p>Note this is declared on the supertype but behaves differently
     * per subclass because the subclasses decide how to present
     * themselves, so a heterogeneous {@code List<Person>} can be rendered
     * without the caller knowing the concrete types.
     */
    public String getDisplayName() {
        return id + " - " + name;
    }

    /**
     * Age in whole years on {@code asOf}, or -1 when unknown.
     *
     * <p>Final accessors below are deliberate: {@link #validateSubtype()}
     * reads them from a subclass constructor, so allowing an override
     * would let a broken subclass corrupt the base class's invariants.
     */
    public final int getAgeOn(LocalDate asOf) {
        if (dateOfBirth == null || asOf == null || asOf.isBefore(dateOfBirth)) {
            return -1;
        }
        return Period.between(dateOfBirth, asOf).getYears();
    }

    public final int getAgeToday() {
        return getAgeOn(LocalDate.now());
    }

    /**
     * Suggests the next sequential identifier for this kind of person.
     * Subclasses supply the prefix; the shared logic lives here.
     *
     * @param highestExistingNumber highest numeric suffix already in use,
     *                              or 0 when there are none
     */
    public final String suggestNextId(int highestExistingNumber) {
        return String.format("%s%03d", getIdPrefix(), highestExistingNumber + 1);
    }

    // ------------------------------------------------------------------
    // Encapsulated state
    // ------------------------------------------------------------------

    /** The identifier is immutable: it is the primary key. */
    public final String getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    public void setName(String name) throws ValidationException {
        this.name = Validator.requireText("Name", name, MAX_NAME_LENGTH);
    }

    public final LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) throws ValidationException {
        if (dateOfBirth != null && !dateOfBirth.isBefore(LocalDate.now())) {
            throw new ValidationException("Date Of Birth", "Date of birth must be in the past.");
        }
        this.dateOfBirth = dateOfBirth;
    }
    public final Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender == null ? Gender.OTHER : gender;
    }

    public final String getEmail() {
        return email;
    }

    public void setEmail(String email) throws ValidationException {
        this.email = Validator.requireEmail("Email", email);
    }

    public final String getPhone() {
        return phone;
    }

    public void setPhone(String phone) throws ValidationException {
        this.phone = Validator.optionalPhone("Phone", phone);
    }

    public final String getAddress() {
        return address;
    }

    public void setAddress(String address) throws ValidationException {
        this.address = Validator.optionalText("Address", address, MAX_ADDRESS_LENGTH);
    }

    // ------------------------------------------------------------------
    // Object contract
    // ------------------------------------------------------------------

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Person person)) {
            return false;
        }
        return id.equals(person.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    /** Sorts people by name, case-insensitively, for stable listings. */
    @Override
    public int compareTo(Person other) {
        int byName = String.CASE_INSENSITIVE_ORDER.compare(name, other.name);
        return byName != 0 ? byName : id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return describe();
    }
}
