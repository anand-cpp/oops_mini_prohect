package com.academic.management.service;

import com.academic.management.dao.AttendanceDao;
import com.academic.management.dao.CourseDao;
import com.academic.management.dao.EnrollmentDao;
import com.academic.management.dao.ResultDao;
import com.academic.management.dao.StudentDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.BusinessRuleException;
import com.academic.management.exception.RecordNotFoundException;
import com.academic.management.model.Course;
import com.academic.management.model.Enrollment;
import com.academic.management.model.Student;

import java.time.LocalDate;
import java.util.List;

/**
 * Business rules for enrolments.
 *
 * <h2>Why the rules are checked twice</h2>
 * Both ends of the relationship are verified here even though foreign keys
 * would reject a bad pair anyway. The reason is the <em>message</em>: a
 * foreign-key violation reaches the user as a driver error, whereas
 * "Student STU099 does not exist" tells them what to fix. The constraint
 * is still what actually guarantees integrity under concurrency.
 *
 * <p>The duplicate check gets the same treatment, and note that it is the
 * one rule a pre-check genuinely cannot make safe on its own - which is
 * why {@code UNIQUE(student_id, course_id)} stays in the schema.
 */
public class EnrollmentService extends BaseService {

    private final EnrollmentDao enrollmentDao;
    private final StudentDao studentDao;
    private final CourseDao courseDao;
    private final AttendanceDao attendanceDao;
    private final ResultDao resultDao;

    public EnrollmentService(EnrollmentDao enrollmentDao, StudentDao studentDao,
                             CourseDao courseDao, AttendanceDao attendanceDao,
                             ResultDao resultDao) {
        this.enrollmentDao = enrollmentDao;
        this.studentDao = studentDao;
        this.courseDao = courseDao;
        this.attendanceDao = attendanceDao;
        this.resultDao = resultDao;
    }

    @Override
    protected String entityName() {
        return "Enrolment";
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    /**
     * Enrols a student on a course.
     *
     * @param studentId      must exist
     * @param courseId       must exist
     * @param semester       1..10
     * @param enrollmentDate cannot be in the future
     */
    public void enroll(String studentId, String courseId, int semester, LocalDate enrollmentDate)
            throws AppException {
        requireStudentExists(studentId);
        requireCourseExists(courseId);
        requireNotAlreadyEnrolled(studentId, courseId);

        Enrollment enrollment = new Enrollment(studentId.trim(), courseId.trim(),
                semester, enrollmentDate, Enrollment.Status.ACTIVE);
        enrollmentDao.insert(enrollment);
        log("Enrolled " + studentId + " on " + courseId + " (semester " + semester + ")");
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    public List<Enrollment> findAll() throws AppException {
        return enrollmentDao.findAll();
    }

    public List<Enrollment> findByStudent(String studentId) throws AppException {
        return enrollmentDao.findByStudent(studentId);
    }

    public List<Enrollment> findByCourse(String courseId) throws AppException {
        return enrollmentDao.findByCourse(courseId);
    }

    public List<Enrollment> findByStudentAndSemester(String studentId, int semester)
            throws AppException {
        return enrollmentDao.findByStudentAndSemester(studentId, semester);
    }

    public boolean isEnrolled(String studentId, String courseId) throws AppException {
        return enrollmentDao.isEnrolled(studentId, courseId);
    }

    public long count() throws AppException {
        return enrollmentDao.count();
    }

    public Enrollment findByStudentAndCourse(String studentId, String courseId)
            throws AppException {
        return requireFound(enrollmentDao.findByStudentAndCourse(studentId, courseId),
                studentId + " / " + courseId);
    }

    // ------------------------------------------------------------------
    // Update / Delete
    // ------------------------------------------------------------------

    /** Marks an enrolment completed or withdrawn, or moves it to a semester. */
    public void updateStatus(Enrollment enrollment) throws AppException {
        if (!enrollmentDao.isEnrolled(enrollment.getStudentId(), enrollment.getCourseId())) {
            throw new RecordNotFoundException("Enrolment",
                    enrollment.getStudentId() + " / " + enrollment.getCourseId());
        }
        enrollmentDao.update(enrollment);
        log("Updated enrolment " + enrollment.getStudentId() + " / "
                + enrollment.getCourseId() + " to " + enrollment.getStatus());
    }

    /**
     * Removes an enrolment, together with the attendance and result rows
     * that were recorded against it.
     *
     * <p>The cascade is why the UI asks for confirmation with the counts
     * attached rather than a bare "are you sure?".
     */
    public DeletionImpact describeDeletion(String studentId, String courseId) throws AppException {
        if (!enrollmentDao.isEnrolled(studentId, courseId)) {
            throw new RecordNotFoundException("Enrolment", studentId + " / " + courseId);
        }
        return new DeletionImpact(studentId, courseId,
                attendanceDao.findByStudentAndCourse(studentId, courseId).isPresent(),
                resultDao.findByStudentAndCourse(studentId, courseId).isPresent());
    }

    public void unenroll(String studentId, String courseId) throws AppException {
        if (!enrollmentDao.isEnrolled(studentId, courseId)) {
            throw new RecordNotFoundException("Enrolment", studentId + " / " + courseId);
        }
        enrollmentDao.delete(studentId, courseId);
        log("Removed enrolment " + studentId + " / " + courseId
                + " (attendance and results cascaded)");
    }

    // ------------------------------------------------------------------
    // Supporting lookups for the enrolment form
    // ------------------------------------------------------------------

    public Student requireStudent(String studentId) throws AppException {
        requireStudentExists(studentId);
        return requireFound(studentDao.findById(studentId.trim()), studentId);
    }

    public Course requireCourse(String courseId) throws AppException {
        requireCourseExists(courseId);
        return requireFound(courseDao.findById(courseId.trim()), courseId);
    }

    /** Suggests the semester an enrolling student is most likely in. */
    public int suggestSemester(String studentId) throws AppException {
        return requireStudent(studentId).getSemester();
    }

    // ------------------------------------------------------------------
    // Rule helpers
    // ------------------------------------------------------------------

    private void requireStudentExists(String studentId) throws AppException {
        if (studentId == null || studentId.isBlank()) {
            throw new BusinessRuleException("Select a student before enrolling.",
                    "Blank student id");
        }
        if (!studentDao.existsById(studentId.trim())) {
            throw new RecordNotFoundException("Student", studentId);
        }
    }

    private void requireCourseExists(String courseId) throws AppException {
        if (courseId == null || courseId.isBlank()) {
            throw new BusinessRuleException("Select a course before enrolling.",
                    "Blank course id");
        }
        if (!courseDao.existsById(courseId.trim())) {
            throw new RecordNotFoundException("Course", courseId);
        }
    }

    private void requireNotAlreadyEnrolled(String studentId, String courseId) throws AppException {
        if (enrollmentDao.isEnrolled(studentId.trim(), courseId.trim())) {
            throw new BusinessRuleException("This student is already enrolled in that course.",
                    "Duplicate enrolment " + studentId + " / " + courseId);
        }
    }

    /**
     * What removing an enrolment would take with it.
     *
     * @param studentId the student
     * @param courseId  the course
     * @param hasAttendance whether attendance has been recorded
     * @param hasResult     whether a result has been published
     */
    public record DeletionImpact(String studentId, String courseId,
                                 boolean hasAttendance, boolean hasResult) {

        /** A sentence for the confirmation dialog. */
        public String describe() {
            if (!hasAttendance && !hasResult) {
                return "Remove the enrolment of " + studentId + " from " + courseId + "?";
            }
            StringBuilder text = new StringBuilder()
                    .append("Remove the enrolment of ").append(studentId)
                    .append(" from ").append(courseId).append("?\n\nThis will also remove:");
            if (hasAttendance) {
                text.append("\n  • the attendance record for this course");
            }
            if (hasResult) {
                text.append("\n  • the published result for this course");
            }
            return text.append("\n\nThis cannot be undone.").toString();
        }
    }
}
