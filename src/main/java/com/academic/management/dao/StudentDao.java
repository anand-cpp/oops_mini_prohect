package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.IdName;
import com.academic.management.model.Student;
import com.academic.management.model.StudentSearchCriteria;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Student}.
 *
 * <h2>Abstraction</h2>
 * The service layer depends on this interface, not on
 * {@link com.academic.management.dao.impl.StudentDaoImpl}. That means the
 * service can be unit-tested against an in-memory stub, and a different
 * storage technology could be introduced without touching business
 * logic.
 *
 * <h2>Error contract</h2>
 * Every method either returns a value or throws an
 * {@link AppException} subclass. A lookup that can legitimately find
 * nothing returns {@link Optional#empty()} rather than throwing, while an
 * operation that requires an existing record throws
 * {@link com.academic.management.exception.RecordNotFoundException}.
 */
public interface StudentDao {

    /**
     * Inserts a new student.
     *
     * @throws com.academic.management.exception.DuplicateRecordException
     *         if the id or the email is already taken
     */
    void insert(Student student) throws AppException;

    /**
     * Updates an existing student. The primary key is immutable, so only
     * the mutable attributes are written.
     *
     * @return {@code true} when a row was updated
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if no student has that id
     */
    boolean update(Student student) throws AppException;

    /**
     * Deletes a student. Enrolments, attendance and results cascade at
     * the database level.
     *
     * @return {@code true} when a row was deleted
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if no student has that id
     */
    boolean delete(String studentId) throws AppException;

    /**
     * Finds one student by primary key.
     *
     * @return the student, or empty when there is no such id
     */
    Optional<Student> findById(String studentId) throws AppException;

    /**
     * Finds one student by unique email address.
     *
     * @return the student, or empty when no account uses that address
     */
    Optional<Student> findByEmail(String email) throws AppException;

    /** Every student, ordered by name. */
    List<Student> findAll() throws AppException;

    /**
     * Applies an optional set of criteria. Null fields are ignored, so an
     * empty criteria object returns every student - the same statement
     * serves all combinations.
     */
    List<Student> search(StudentSearchCriteria query) throws AppException;

    /** Number of student rows. */
    long count() throws AppException;

    /**
     * Highest numeric suffix currently used by a student id, so the UI can
     * suggest the next free one. Returns 0 when there are no students.
     */
    int findHighestNumericSuffix() throws AppException;

    /**
     * Every distinct department name, for populating a filter combo box.
     */
    List<String> findDistinctDepartments() throws AppException;

    /**
     * Id and name only, for populating dropdowns. Returns
     * {@link IdName} rather than partly-built {@link Student} objects,
     * so no placeholder data is ever fabricated.
     */
    List<IdName> findAllSummaries() throws AppException;

    /** True when the id is already in use. */
    boolean existsById(String studentId) throws AppException;

    /** True when the email is already in use. */
    boolean existsByEmail(String email, String excludingStudentId) throws AppException;
}
