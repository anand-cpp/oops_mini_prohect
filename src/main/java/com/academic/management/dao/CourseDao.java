package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.Course;
import com.academic.management.model.CourseSearchCriteria;
import com.academic.management.model.IdName;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Course}.
 */
public interface CourseDao {

    void insert(Course course) throws AppException;

    /**
     * @return {@code true} when a row was updated
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if no course has that id
     */
    boolean update(Course course) throws AppException;

    /**
     * Deletes a course and, by cascade, its enrolments, attendance and
     * results.
     *
     * @return {@code true} when a row was deleted
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if no course has that id
     */
    boolean delete(String courseId) throws AppException;

    Optional<Course> findById(String courseId) throws AppException;

    /** Looks a course up by its unique public code, for example {@code CS301}. */
    Optional<Course> findByCode(String courseCode) throws AppException;

    List<Course> findAll() throws AppException;

    List<Course> search(CourseSearchCriteria query) throws AppException;

    long count() throws AppException;

    int findHighestNumericSuffix() throws AppException;

    List<String> findDistinctDepartments() throws AppException;

    /**
     * Id, code and name only, for populating dropdowns. Returns
     * {@link IdName} records rather than partly-built {@link Course}
     * objects, so no placeholder data is ever fabricated.
     */
    List<IdName> findAllSummaries() throws AppException;

    boolean existsById(String courseId) throws AppException;

    boolean existsByCode(String courseCode, String excludingCourseId) throws AppException;

    /** Every course a student is currently enrolled in. */
    List<Course> findByStudent(String studentId) throws AppException;

    /** Every course a faculty member currently teaches. */
    List<Course> findByFaculty(String facultyId) throws AppException;
}
