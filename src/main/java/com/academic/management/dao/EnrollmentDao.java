package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.Enrollment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Enrollment}.
 *
 * <p>The {@code UNIQUE(student_id, course_id)} index in the database is
 * the final authority on duplicate enrolments; this DAO surfaces that
 * violation as
 * {@link com.academic.management.exception.DuplicateRecordException} so
 * the service and the UI can explain it.
 */
public interface EnrollmentDao {

    void insert(Enrollment enrollment) throws AppException;

    /** Inserts and returns the row with its generated key populated. */
    Enrollment insertAndReturn(Enrollment enrollment) throws AppException;

    /**
     * Updates semester, date and status of an existing enrolment.
     *
     * @return {@code true} when a row was updated
     */
    boolean update(Enrollment enrollment) throws AppException;

    /**
     * @return {@code true} when a row was deleted
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if the pair is not enrolled
     */
    boolean delete(String studentId, String courseId) throws AppException;

    Optional<Enrollment> findById(long enrollmentId) throws AppException;

    Optional<Enrollment> findByStudentAndCourse(String studentId, String courseId)
            throws AppException;

    /** True when the student is already enrolled in the course. */
    boolean isEnrolled(String studentId, String courseId) throws AppException;

    List<Enrollment> findAll() throws AppException;

    List<Enrollment> findByStudent(String studentId) throws AppException;

    List<Enrollment> findByCourse(String courseId) throws AppException;

    List<Enrollment> findByStudentAndSemester(String studentId, int semester)
            throws AppException;

    long count() throws AppException;

    /** How many students are enrolled in the course. */
    int countByCourse(String courseId) throws AppException;

    /**
     * Enrolments added on or after a date, for a "recent activity" panel.
     */
    List<Enrollment> findEnrolledSince(LocalDate date) throws AppException;
}
