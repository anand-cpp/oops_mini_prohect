package com.academic.management.service;

import com.academic.management.dao.CourseDao;
import com.academic.management.dao.EnrollmentDao;
import com.academic.management.dao.FacultyDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.BusinessRuleException;
import com.academic.management.exception.DuplicateRecordException;
import com.academic.management.exception.RecordNotFoundException;
import com.academic.management.model.Course;
import com.academic.management.model.CourseSearchCriteria;
import com.academic.management.model.IdName;

import java.util.List;

/**
 * Business rules for courses.
 *
 * <p>The course code is the one identifier students and results are quoted
 * by, so its uniqueness is enforced here before the database's
 * {@code UNIQUE} index gets the chance to complain in its own words.
 *
 * <p>Faculty assignment is validated rather than trusted: a typo'd id would
 * otherwise satisfy the foreign key constraint only if it happened to
 * exist, and {@code null} is a legitimate choice meaning "not yet
 * allocated", so both cases are handled explicitly.
 */
public class CourseService extends BaseService {

    private final CourseDao courseDao;
    private final FacultyDao facultyDao;
    private final EnrollmentDao enrollmentDao;

    public CourseService(CourseDao courseDao, FacultyDao facultyDao, EnrollmentDao enrollmentDao) {
        this.courseDao = courseDao;
        this.facultyDao = facultyDao;
        this.enrollmentDao = enrollmentDao;
    }

    @Override
    protected String entityName() {
        return "Course";
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    public void create(Course course) throws AppException {
        requireCodeFree(course.getCourseCode(), null);
        requireFacultyExistsIfSet(course.getFacultyId());
        courseDao.insert(course);
        log("Created course " + course.getCourseId() + " (" + course.getCourseCode() + ")");
    }

    public String suggestNextId() throws AppException {
        return com.academic.management.model.Course.suggestId(courseDao.findHighestNumericSuffix());
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    public List<Course> findAll() throws AppException {
        return courseDao.findAll();
    }

    public Course findById(String courseId) throws AppException {
        return requireFound(courseDao.findById(courseId.trim()), courseId);
    }

    public List<IdName> findSummaries() throws AppException {
        return courseDao.findAllSummaries();
    }

    public long count() throws AppException {
        return courseDao.count();
    }

    public List<String> findDistinctDepartments() throws AppException {
        return courseDao.findDistinctDepartments();
    }

    public List<Course> search(CourseSearchCriteria criteria) throws AppException {
        return courseDao.search(criteria);
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    public void update(Course course) throws AppException {
        requireExists(course.getCourseId());
        requireCodeFree(course.getCourseCode(), course.getCourseId());
        requireFacultyExistsIfSet(course.getFacultyId());
        courseDao.update(course);
        log("Updated course " + course.getCourseId());
    }

    /**
     * Assigns or clears the teaching faculty member.
     *
     * <p>Exposed separately because this is the single most common edit
     * the enrolment workflow needs, and it is a narrower operation than a
     * full course update.
     *
     * @param courseId  the course to reassign
     * @param facultyId the new member, or {@code null} to unassign
     */
    public void assignFaculty(String courseId, String facultyId) throws AppException {
        Course course = requireFound(courseDao.findById(courseId.trim()), courseId);
        requireFacultyExistsIfSet(facultyId);

        Course reassigned = new Course(course.getCourseId(), course.getCourseCode(),
                course.getCourseName(), course.getCredits(),
                facultyId == null || facultyId.isBlank() ? null : facultyId.trim(),
                course.getDepartment(), course.getSemester(), course.getDescription());
        courseDao.update(reassigned);
        log("Assigned course " + courseId + " to faculty "
                + (facultyId == null || facultyId.isBlank() ? "(none)" : facultyId));
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    /** How many students are enrolled, for the confirmation dialog. */
    public int countEnrolled(String courseId) throws AppException {
        requireExists(courseId);
        return enrollmentDao.countByCourse(courseId);
    }

    /**
     * Deletes a course.
     *
     * <p>Refused while students are enrolled, for the same reason faculty
     * deletion is: cascading would silently destroy a whole class's
     * enrolments, attendance and results.
     */
    public void delete(String courseId) throws AppException {
        requireExists(courseId);
        int enrolled = enrollmentDao.countByCourse(courseId);
        if (enrolled > 0) {
            throw new BusinessRuleException(
                    "This course still has " + enrolled
                            + (enrolled == 1 ? " student enrolled" : " students enrolled")
                            + ". Remove the enrolments before deleting it.",
                    "Cannot delete " + courseId + ": " + enrolled + " enrolment(s) present");
        }
        courseDao.delete(courseId);
        log("Deleted course " + courseId);
    }

    // ------------------------------------------------------------------
    // Rule helpers
    // ------------------------------------------------------------------

    private void requireExists(String courseId) throws AppException {
        if (!courseDao.existsById(courseId.trim())) {
            throw new RecordNotFoundException("Course", courseId);
        }
    }

    private void requireCodeFree(String courseCode, String excludingCourseId) throws AppException {
        if (courseDao.existsByCode(courseCode, excludingCourseId)) {
            throw new DuplicateRecordException("Course code", "course_code", courseCode);
        }
    }

    /**
     * A blank faculty id means "not yet allocated" and is allowed;
     * anything else must be a real member.
     */
    private void requireFacultyExistsIfSet(String facultyId) throws AppException {
        if (facultyId == null || facultyId.isBlank()) {
            return;
        }
        if (!facultyDao.existsById(facultyId.trim())) {
            throw new BusinessRuleException("No faculty member exists with id '"
                            + facultyId.trim() + "'.",
                    "Course references unknown faculty " + facultyId);
        }
    }
}
