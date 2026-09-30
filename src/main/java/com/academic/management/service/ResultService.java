package com.academic.management.service;

import com.academic.management.dao.CourseDao;
import com.academic.management.dao.EnrollmentDao;
import com.academic.management.dao.ResultDao;
import com.academic.management.dao.StudentDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.BusinessRuleException;
import com.academic.management.exception.RecordNotFoundException;
import com.academic.management.model.Course;
import com.academic.management.model.Grade;
import com.academic.management.model.Result;
import com.academic.management.model.Student;
import com.academic.management.util.Constants;
import com.academic.management.util.GradingPolicy;
import com.academic.management.util.StandardGradingPolicy;

import java.time.LocalDate;
import java.util.List;

/**
 * Publishes and reports results.
 *
 * <h2>Grading is not decided here</h2>
 * This service never asks "is 72 a B+?". It hands the marks to the
 * injected {@link GradingPolicy} and stores what comes back, and the
 * policy is the same object the {@link Result} model used to compute it.
 * That is the whole point of the indirection: changing the grading scale
 * means editing {@link StandardGradingPolicy} and the bands move through
 * the model, this service, the database and the UI together. A second
 * implementation of the table anywhere else would eventually disagree
 * with the first.
 *
 * <h2>What this service does own</h2>
 * Marks range checking against {@link Constants#MAX_INTERNAL_MARKS} and
 * {@link Constants#MAX_EXTERNAL_MARKS}, the rule that only an enrolled
 * student can have a result, and course-level pass statistics.
 */
public class ResultService extends BaseService {

    private final ResultDao resultDao;
    private final EnrollmentDao enrollmentDao;
    private final StudentDao studentDao;
    private final CourseDao courseDao;
    private final StudentService studentService;
    private final GradingPolicy policy;

    public ResultService(ResultDao resultDao, EnrollmentDao enrollmentDao, StudentDao studentDao,
                         CourseDao courseDao, StudentService studentService) {
        this(resultDao, enrollmentDao, studentDao, courseDao, studentService,
                StandardGradingPolicy.getInstance());
    }

    /**
     * @param policy the grading scale to apply; injectable so a different
     *               scale can be substituted without touching the callers
     */
    public ResultService(ResultDao resultDao, EnrollmentDao enrollmentDao, StudentDao studentDao,
                         CourseDao courseDao, StudentService studentService,
                         GradingPolicy policy) {
        this.resultDao = resultDao;
        this.enrollmentDao = enrollmentDao;
        this.studentDao = studentDao;
        this.courseDao = courseDao;
        this.studentService = studentService;
        this.policy = policy == null ? StandardGradingPolicy.getInstance() : policy;
    }

    @Override
    protected String entityName() {
        return "Result";
    }

    /** The grading scale in force, for the UI to display. */
    public GradingPolicy getPolicy() {
        return policy;
    }

    // ------------------------------------------------------------------
    // Publish
    // ------------------------------------------------------------------

    /**
     * Publishes or replaces a result.
     *
     * @param studentId     an enrolled student
     * @param courseId      the course
     * @param internalMarks 0 to {@link Constants#MAX_INTERNAL_MARKS}
     * @param externalMarks 0 to {@link Constants#MAX_EXTERNAL_MARKS}
     * @param remarks       optional, stored as given
     * @return the stored result, with its total, percentage and grade
     */
    public Result publish(String studentId, String courseId, double internalMarks,
                          double externalMarks, String remarks) throws AppException {
        return publishOn(studentId, courseId, internalMarks, externalMarks,
                LocalDate.now(), remarks);
    }

    /** As {@link #publish}, with an explicit result date. */
    public Result publishOn(String studentId, String courseId, double internalMarks,
                            double externalMarks, LocalDate resultDate, String remarks)
            throws AppException {
        requireEnrolled(studentId, courseId);

        // The Result constructor performs the range validation and raises
        // the ValidationException the UI knows how to display.
        Result result = new Result(studentId.trim(), courseId.trim(),
                internalMarks, externalMarks, resultDate, remarks, policy);
        resultDao.upsert(result);

        log("Published result " + studentId + "/" + courseId + ": "
                + result.getTotalMarks() + "/" + Constants.TOTAL_MARKS
                + " (" + result.getPercentage() + "%, " + result.getGradeCode() + ")");
        return result;
    }

    /**
     * Recomputes what a set of marks would produce without saving anything.
     *
     * <p>Used by the results form to show the operator the grade as they
     * type, so they can see the consequence of a change before committing
     * it. It still validates, so nonsense input is reported immediately.
     */
    public Result preview(String studentId, String courseId, double internalMarks,
                          double externalMarks) throws AppException {
        return new Result(studentId == null ? "STU000" : studentId,
                courseId == null ? "CRS000" : courseId,
                internalMarks, externalMarks, LocalDate.now(), null, policy);
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    public List<Result> findAll() throws AppException {
        return resultDao.findAll();
    }

    public List<Result> findByStudent(String studentId) throws AppException {
        return resultDao.findByStudent(studentId);
    }

    public List<Result> findByCourse(String courseId) throws AppException {
        return resultDao.findByCourse(courseId);
    }

    public List<Result> findByGrade(Grade grade) throws AppException {
        return resultDao.findByGrade(grade);
    }

    public Result findByStudentAndCourse(String studentId, String courseId) throws AppException {
        return requireFound(resultDao.findByStudentAndCourse(studentId, courseId),
                studentId + " / " + courseId);
    }

    public long count() throws AppException {
        return resultDao.count();
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    public void delete(String studentId, String courseId) throws AppException {
        if (resultDao.findByStudentAndCourse(studentId, courseId).isEmpty()) {
            throw new RecordNotFoundException("Result", studentId + " / " + courseId);
        }
        resultDao.delete(studentId, courseId);
        log("Deleted result " + studentId + "/" + courseId);
    }

    // ------------------------------------------------------------------
    // Statistics
    // ------------------------------------------------------------------

    /**
     * How a course performed: how many sat it, how many passed, the mean
     * and the highest mark.
     *
     * <p>The pass count comes from the {@code is_pass} column this service
     * wrote using the same policy as the grade, so the two figures can
     * never contradict each other.
     */
    public CourseResultSummary summariseCourse(String courseId) throws AppException {
        requireCourseExists(courseId);
        List<Result> results = resultDao.findByCourse(courseId);
        if (results.isEmpty()) {
            return new CourseResultSummary(courseId, 0, 0, 0.0, 0.0);
        }
        double total = 0;
        double highest = 0;
        int passed = 0;
        for (Result result : results) {
            double percentage = result.getPercentage();
            total += percentage;
            highest = Math.max(highest, percentage);
            if (result.isPass()) {
                passed++;
            }
        }
        return new CourseResultSummary(courseId, results.size(), passed,
                round2(total / results.size()), round2(highest));
    }

    /** The most common grade in a course, or empty when there are no results. */
    public java.util.Optional<Grade> modalGrade(String courseId) throws AppException {
        java.util.Map<Grade, Integer> tally = new java.util.EnumMap<>(Grade.class);
        for (Result result : resultDao.findByCourse(courseId)) {
            tally.merge(result.getGrade(), 1, Integer::sum);
        }
        return tally.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey);
    }

    // ------------------------------------------------------------------
    // Table rows
    // ------------------------------------------------------------------

    /**
     * Every result in the system with both names resolved, for the results
     * table. A student may have hundreds of results, so the lookup maps are
     * built once rather than issuing a query per row.
     */
    public List<ResultRow> findAllRows() throws AppException {
        java.util.Map<String, String> studentNames = new java.util.HashMap<>();
        for (Student student : studentDao.findAll()) {
            studentNames.put(student.getId(), student.getName());
        }
        java.util.Map<String, String> courseNames = new java.util.HashMap<>();
        for (Course course : courseDao.findAll()) {
            courseNames.put(course.getCourseId(), course.getCourseName());
        }

        List<ResultRow> rows = new java.util.ArrayList<>();
        for (Result result : resultDao.findAll()) {
            rows.add(new ResultRow(
                    result.getStudentId(),
                    studentNames.getOrDefault(result.getStudentId(), "(unknown)"),
                    result.getCourseId(),
                    courseNames.getOrDefault(result.getCourseId(), "(unknown)"),
                    result.getInternalMarks(),
                    result.getExternalMarks(),
                    result.getTotalMarks(),
                    result.getPercentage(),
                    result.getGradeCode(),
                    result.getGradePoint(),
                    result.isPass(),
                    result.getRemarks()));
        }
        return rows;
    }

    public List<ResultRow> findRowsForStudent(String studentId) throws AppException {
        java.util.Map<String, String> courseNames = new java.util.HashMap<>();
        for (Course course : courseDao.findAll()) {
            courseNames.put(course.getCourseId(), course.getCourseName());
        }
        List<ResultRow> rows = new java.util.ArrayList<>();
        for (Result result : resultDao.findByStudent(studentId)) {
            Student student = studentDao.findById(studentId).orElse(null);
            rows.add(new ResultRow(
                    result.getStudentId(),
                    student == null ? studentId : student.getName(),
                    result.getCourseId(),
                    courseNames.getOrDefault(result.getCourseId(), "(unknown)"),
                    result.getInternalMarks(),
                    result.getExternalMarks(),
                    result.getTotalMarks(),
                    result.getPercentage(),
                    result.getGradeCode(),
                    result.getGradePoint(),
                    result.isPass(),
                    result.getRemarks()));
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Workflow rules
    // ------------------------------------------------------------------

    /** Courses whose results this student may be entered for. */
    public List<Course> findGradableCourses(String studentId) throws AppException {
        return courseDao.findByStudent(studentId);
    }

    private void requireEnrolled(String studentId, String courseId) throws AppException {
        if (studentId == null || studentId.isBlank() || courseId == null || courseId.isBlank()) {
            throw new BusinessRuleException("Select both a student and a course.",
                    "Incomplete result request");
        }
        if (!studentDao.existsById(studentId.trim())) {
            throw new RecordNotFoundException("Student", studentId);
        }
        if (!courseDao.existsById(courseId.trim())) {
            throw new RecordNotFoundException("Course", courseId);
        }
        studentService.requireEnrolled(studentId.trim(), courseId.trim());
    }

    private void requireCourseExists(String courseId) throws AppException {
        if (!courseDao.existsById(courseId.trim())) {
            throw new RecordNotFoundException("Course", courseId);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * A result shaped for display, with names resolved.
     *
     * @param studentId     student
     * @param studentName   resolved student name
     * @param courseId      course
     * @param courseName    resolved course name
     * @param internalMarks internal component
     * @param externalMarks external component
     * @param totalMarks    sum of both
     * @param percentage    percentage of the total
     * @param gradeCode     letter grade
     * @param gradePoint    grade point on the 5.00 scale
     * @param pass          whether it counts as a pass
     * @param remarks       optional remarks
     */
    public record ResultRow(String studentId, String studentName, String courseId,
                            String courseName, double internalMarks, double externalMarks,
                            double totalMarks, double percentage, String gradeCode,
                            double gradePoint, boolean pass, String remarks) {
    }

    /**
     * @param courseId       the course
     * @param students       how many results exist
     * @param passed         how many passed
     * @param averagePercent mean percentage
     * @param highestPercent best percentage
     */
    public record CourseResultSummary(String courseId, int students, int passed,
                                      double averagePercent, double highestPercent) {

        /** Pass rate as a percentage, 0 when nobody sat the course. */
        public double passRate() {
            return students == 0 ? 0.0 : round2(passed * 100.0 / students);
        }
    }
}
