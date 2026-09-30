package com.academic.management.service;

import com.academic.management.dao.CourseDao;
import com.academic.management.dao.FacultyDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.DuplicateRecordException;
import com.academic.management.model.Faculty;
import com.academic.management.model.FacultySearchCriteria;
import com.academic.management.model.IdName;

import java.util.List;

/**
 * Business rules for faculty records.
 *
 * <p>Mirrors {@link StudentService}: field validity is the model's job,
 * uniqueness against existing rows is this service's job.
 */
public class FacultyService extends BaseService {

    private final FacultyDao facultyDao;
    private final CourseDao courseDao;

    public FacultyService(FacultyDao facultyDao, CourseDao courseDao) {
        this.facultyDao = facultyDao;
        this.courseDao = courseDao;
    }

    @Override
    protected String entityName() {
        return "Faculty member";
    }

    public void create(Faculty faculty) throws AppException {
        requireIdFree(faculty.getId());
        requireEmailFree(faculty.getEmail(), null);
        facultyDao.insert(faculty);
        log("Created faculty " + faculty.getId() + " (" + faculty.getName() + ")");
    }

    public String suggestNextId() throws AppException {
        return "FAC" + String.format("%03d", facultyDao.findHighestNumericSuffix() + 1);
    }

    public List<Faculty> findAll() throws AppException {
        return facultyDao.findAll();
    }

    public Faculty findById(String facultyId) throws AppException {
        return requireFound(facultyDao.findById(facultyId.trim()), facultyId);
    }

    public List<IdName> findSummaries() throws AppException {
        return facultyDao.findAllSummaries();
    }

    public long count() throws AppException {
        return facultyDao.count();
    }

    public List<String> findDistinctDepartments() throws AppException {
        return facultyDao.findDistinctDepartments();
    }

    public List<String> findDistinctDesignations() throws AppException {
        return facultyDao.findDistinctDesignations();
    }

    public List<Faculty> search(FacultySearchCriteria criteria) throws AppException {
        return facultyDao.search(criteria);
    }

    public void update(Faculty faculty) throws AppException {
        requireExists(faculty.getId());
        requireEmailFree(faculty.getEmail(), faculty.getId());
        facultyDao.update(faculty);
        log("Updated faculty " + faculty.getId());
    }

    /** How many courses this member is assigned to teach. */
    public int countCoursesTaught(String facultyId) throws AppException {
        return facultyDao.countCoursesTaught(facultyId);
    }

    /**
     * Deletes a faculty member.
     *
     * <p>Refuses while courses are still assigned, because
     * {@code courses.faculty_id} is nullable and the alternative -
     * silently orphaning the courses - would leave a teaching load with
     * no owner. The caller should reassign the courses first.
     */
    public void delete(String facultyId) throws AppException {
        requireExists(facultyId);
        int assigned = facultyDao.countCoursesTaught(facultyId);
        if (assigned > 0) {
            throw new com.academic.management.exception.BusinessRuleException(
                    "This member still teaches " + assigned
                            + (assigned == 1 ? " course" : " courses")
                            + ". Reassign them before deleting.",
                    "Cannot delete " + facultyId + ": " + assigned + " course(s) assigned");
        }
        facultyDao.delete(facultyId);
        log("Deleted faculty " + facultyId);
    }

    /** True when a faculty id exists, for validating course assignment. */
    public boolean existsById(String facultyId) throws AppException {
        return facultyId != null && !facultyId.isBlank() && facultyDao.existsById(facultyId.trim());
    }

    /** The courses this member teaches, for the faculty detail view. */
    public List<com.academic.management.model.Course> findCoursesTaught(String facultyId)
            throws AppException {
        return courseDao.findByFaculty(facultyId);
    }

    private void requireExists(String facultyId) throws AppException {
        if (!facultyDao.existsById(facultyId.trim())) {
            throw new com.academic.management.exception.RecordNotFoundException(
                    "Faculty member", facultyId);
        }
    }

    private void requireIdFree(String facultyId) throws AppException {
        if (facultyDao.existsById(facultyId)) {
            throw new DuplicateRecordException("Faculty member", "id", facultyId);
        }
    }

    private void requireEmailFree(String email, String excludingFacultyId) throws AppException {
        if (email != null && facultyDao.existsByEmail(email, excludingFacultyId)) {
            throw new DuplicateRecordException("Email address", "email", email);
        }
    }
}
