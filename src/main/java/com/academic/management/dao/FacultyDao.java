package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.Faculty;
import com.academic.management.model.FacultySearchCriteria;
import com.academic.management.model.IdName;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Faculty}.
 *
 * <p>Same abstraction contract as {@link StudentDao}: the service layer
 * depends on this interface, every method either returns a value or
 * throws an {@link AppException}, and an optional lookup returns
 * {@link Optional#empty()} instead of throwing.
 */
public interface FacultyDao {

    void insert(Faculty faculty) throws AppException;

    /**
     * @return {@code true} when a row was updated
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if no faculty member has that id
     */
    boolean update(Faculty faculty) throws AppException;

    /**
     * Deletes a faculty member. Courses they teach keep existing with a
     * {@code NULL} faculty, because the schema uses
     * {@code ON DELETE SET NULL} on {@code courses.faculty_id}.
     *
     * @return {@code true} when a row was deleted
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if no faculty member has that id
     */
    boolean delete(String facultyId) throws AppException;

    Optional<Faculty> findById(String facultyId) throws AppException;

    Optional<Faculty> findByEmail(String email) throws AppException;

    List<Faculty> findAll() throws AppException;

    List<Faculty> search(FacultySearchCriteria query) throws AppException;

    long count() throws AppException;

    /** How many courses the given faculty member currently teaches. */
    int countCoursesTaught(String facultyId) throws AppException;

    int findHighestNumericSuffix() throws AppException;

    List<String> findDistinctDepartments() throws AppException;

    List<String> findDistinctDesignations() throws AppException;

    /**
     * Id, name and designation only, for populating the course form's
     * combo box. Returns {@link IdName} records rather than partly-built
     * {@link Faculty} objects, so no placeholder data is fabricated.
     */
    List<IdName> findAllSummaries() throws AppException;

    boolean existsById(String facultyId) throws AppException;

    boolean existsByEmail(String email, String excludingFacultyId) throws AppException;
}
