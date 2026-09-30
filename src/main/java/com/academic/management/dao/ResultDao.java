package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.model.Grade;
import com.academic.management.model.Result;
import com.academic.management.util.GradingPolicy;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Result}.
 *
 * <p>Like attendance, at most one row exists per (student, course) pair,
 * so marking a course again replaces the previous mark via
 * {@link #upsert(Result)}.
 *
 * <p>{@code percentage}, {@code grade}, {@code grade_point} and
 * {@code is_pass} are written by this DAO <em>using the injected
 * {@link GradingPolicy}</em>, never recomputed in SQL. Keeping the rule
 * in one place is what stops the stored grade and the displayed grade
 * from ever disagreeing.
 */
public interface ResultDao {

    /**
     * @return the affected row count (1 for both insert and update)
     */
    int upsert(Result result) throws AppException;

    int insert(Result result) throws AppException;

    int update(Result result) throws AppException;

    /**
     * @return {@code true} when a row was deleted
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if the pair has no result
     */
    boolean delete(String studentId, String courseId) throws AppException;

    Optional<Result> findById(long resultId) throws AppException;

    Optional<Result> findByStudentAndCourse(String studentId, String courseId)
            throws AppException;

    List<Result> findAll() throws AppException;

    List<Result> findByStudent(String studentId) throws AppException;

    List<Result> findByCourse(String courseId) throws AppException;

    List<Result> findByGrade(Grade grade) throws AppException;

    long count() throws AppException;

    /** How many students received a passing mark in the course. */
    int countPassedInCourse(String courseId) throws AppException;

    /** How many students took the course. */
    int countResultsInCourse(String courseId) throws AppException;
}
