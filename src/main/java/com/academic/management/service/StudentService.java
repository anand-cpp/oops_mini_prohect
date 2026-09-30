package com.academic.management.service;

import com.academic.management.dao.CourseDao;
import com.academic.management.dao.EnrollmentDao;
import com.academic.management.dao.ResultDao;
import com.academic.management.dao.StudentDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.BusinessRuleException;
import com.academic.management.exception.DuplicateRecordException;
import com.academic.management.model.IdName;
import com.academic.management.model.Student;
import com.academic.management.model.StudentSearchCriteria;

import java.util.List;

/**
 * Business rules for student records.
 *
 * <h2>What this adds over the DAO</h2>
 * The {@link Student} constructor already rejects a blank name, a
 * malformed email and a semester outside 1..10, so those rules need no
 * repeat here. What this service owns is everything the model <em>cannot</em>
 * know, because it depends on what is already in the database:
 *
 * <ul>
 *   <li>the id must not already be taken,</li>
 *   <li>the email must not already be registered to someone else,</li>
 *   <li>a student with recorded results cannot be deleted silently - the
 *       caller is told what would be lost first.</li>
 * </ul>
 *
 * <p>The uniqueness checks deliberately run before the INSERT and the
 * database constraint remains the final authority. The pre-check exists
 * to produce a sentence a user can act on ("STU007 is already in use")
 * rather than a driver error number; the constraint exists because two
 * concurrent sessions can both pass a pre-check.
 */
public class StudentService extends BaseService {

    private final StudentDao studentDao;
    private final EnrollmentDao enrollmentDao;
    private final ResultDao resultDao;
    private final CourseDao courseDao;

    public StudentService(StudentDao studentDao, EnrollmentDao enrollmentDao,
                          ResultDao resultDao, CourseDao courseDao) {
        this.studentDao = studentDao;
        this.enrollmentDao = enrollmentDao;
        this.resultDao = resultDao;
        this.courseDao = courseDao;
    }

    @Override
    protected String entityName() {
        return "Student";
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    /**
     * Validates uniqueness and stores a new student.
     *
     * @param student a fully-constructed, already-valid domain object
     * @throws DuplicateRecordException if the id or email is already used
     */
    public void create(Student student) throws AppException {
        requireIdFree(student.getId());
        requireEmailFree(student.getEmail(), null);
        studentDao.insert(student);
        log("Created student " + student.getId() + " (" + student.getName() + ")");
    }

    /**
     * Suggests the next unused id, e.g. {@code STU007}.
     *
     * <p>Derived from the highest existing numeric suffix rather than the
     * row count, so deleting a middle record cannot cause a collision.
     */
    public String suggestNextId() throws AppException {
        return "STU" + String.format("%03d", studentDao.findHighestNumericSuffix() + 1);
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    public List<Student> findAll() throws AppException {
        return studentDao.findAll();
    }

    public Student findById(String studentId) throws AppException {
        return requireFound(studentDao.findById(studentId.trim()), studentId);
    }

    /**
     * The student, or {@code null} when the id is unknown.
     *
     * <p>For callers that genuinely have a "no such student" case to
     * handle, such as resolving a label in a table. Anything the user
     * typed should use {@link #findById(String)} and get the proper
     * not-found error instead.
     */
    public Student findByIdSafe(String studentId) throws AppException {
        if (studentId == null || studentId.isBlank()) {
            return null;
        }
        return studentDao.findById(studentId.trim()).orElse(null);
    }

    public List<IdName> findSummaries() throws AppException {
        return studentDao.findAllSummaries();
    }

    public long count() throws AppException {
        return studentDao.count();
    }

    public List<String> findDistinctDepartments() throws AppException {
        return studentDao.findDistinctDepartments();
    }

    public List<Student> search(StudentSearchCriteria criteria) throws AppException {
        return studentDao.search(criteria);
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    /**
     * Stores changes to an existing student.
     *
     * @param student the edited student; its id is the primary key
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if no such student exists
     */
    public void update(Student student) throws AppException {
        requireExists(student.getId());
        // The email may be unchanged, which is why the student is excluded
        // from its own uniqueness check.
        requireEmailFree(student.getEmail(), student.getId());
        studentDao.update(student);
        log("Updated student " + student.getId());
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    /**
     * Describes what deleting a student would remove.
     *
     * <p>Returned rather than acted on so the UI can show a truthful
     * confirmation dialog. The foreign keys cascade, so the numbers here
     * are exactly what will disappear.
     */
    public DeletionImpact describeDeletion(String studentId) throws AppException {
        requireExists(studentId);
        List<com.academic.management.model.Enrollment> enrolments =
                enrollmentDao.findByStudent(studentId);
        return new DeletionImpact(studentId, enrolments.size(),
                resultDao.findByStudent(studentId).size());
    }

    /**
     * Deletes a student and everything hanging off that record.
     *
     * <p>The cascade is intentional: attendance and results are meaningless
     * without the student they describe. The UI is expected to have shown
     * {@link #describeDeletion(String)} first.
     */
    public void delete(String studentId) throws AppException {
        requireExists(studentId);
        studentDao.delete(studentId);
        log("Deleted student " + studentId + " (enrolments, attendance and results cascaded)");
    }

    // ------------------------------------------------------------------
    // Rule helpers
    // ------------------------------------------------------------------

    private void requireExists(String studentId) throws AppException {
        if (!studentDao.existsById(studentId.trim())) {
            throw new com.academic.management.exception.RecordNotFoundException("Student", studentId);
        }
    }

    private void requireIdFree(String studentId) throws AppException {
        if (studentDao.existsById(studentId)) {
            throw new DuplicateRecordException("Student", "id", studentId);
        }
    }

    private void requireEmailFree(String email, String excludingStudentId) throws AppException {
        if (email != null && studentDao.existsByEmail(email, excludingStudentId)) {
            throw new DuplicateRecordException("Email address", "email", email);
        }
    }

    /**
     * What a delete would take with it, so the confirmation dialog can be
     * specific instead of asking the user to trust a generic warning.
     *
     * @param studentId      the student being removed
     * @param enrolmentCount how many enrolments will be removed
     * @param resultCount    how many results will be removed
     */
    public record DeletionImpact(String studentId, int enrolmentCount, int resultCount) {

        /** A sentence for the confirmation dialog. */
        public String describe() {
            StringBuilder text = new StringBuilder();
            text.append("Delete student ").append(studentId).append("?");
            if (enrolmentCount > 0 || resultCount > 0) {
                text.append("\n\nThis will also permanently remove:");
                if (enrolmentCount > 0) {
                    text.append("\n  • ").append(enrolmentCount)
                            .append(enrolmentCount == 1 ? " enrolment" : " enrolments");
                }
                if (resultCount > 0) {
                    text.append("\n  • ").append(resultCount)
                            .append(resultCount == 1 ? " result" : " results");
                }
            }
            text.append("\n\nThis cannot be undone.");
            return text.toString();
        }
    }

    /** Convenience for the profile screen: the courses a student is taking. */
    public List<com.academic.management.model.Course> findEnrolledCourses(String studentId)
            throws AppException {
        requireExists(studentId);
        return courseDao.findByStudent(studentId);
    }

    /** Guards a workflow rule shared by attendance and results. */
    void requireEnrolled(String studentId, String courseId) throws AppException {
        if (!enrollmentDao.isEnrolled(studentId, courseId)) {
            throw new BusinessRuleException(
                    "That student is not enrolled in that course, so the record cannot be saved.",
                    "No enrolment exists for " + studentId + " / " + courseId);
        }
    }
}
