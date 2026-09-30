package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.Attendance;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Attendance}.
 *
 * <p>There is at most one row per (student, course) pair, enforced by
 * {@code UNIQUE(student_id, course_id)}. Writing a second record for the
 * same pair therefore updates the existing row, which is why this DAO
 * exposes {@link #upsert(Attendance)} rather than an insert that would
 * always collide.
 */
public interface AttendanceDao {

    /**
     * Inserts a new record, or updates the existing one for the same
     * student/course pair.
     *
     * @return the affected row count (1 for both insert and update)
     */
    int upsert(Attendance attendance) throws AppException;

    int update(Attendance attendance) throws AppException;

    /**
     * @return {@code true} when a row was deleted
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if the pair has no record
     */
    boolean delete(String studentId, String courseId) throws AppException;

    Optional<Attendance> findById(long attendanceId) throws AppException;

    Optional<Attendance> findByStudentAndCourse(String studentId, String courseId)
            throws AppException;

    List<Attendance> findAll() throws AppException;

    List<Attendance> findByStudent(String studentId) throws AppException;

    List<Attendance> findByCourse(String courseId) throws AppException;

    long count() throws AppException;

    /** Distinct student ids that have any attendance record. */
    List<String> findDistinctStudentIds() throws AppException;
}
