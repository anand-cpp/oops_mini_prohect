package com.academic.management.service;

import com.academic.management.dao.AcademicProfileDao;
import com.academic.management.exception.AppException;
import com.academic.management.model.AcademicProfile;
import com.academic.management.model.CoursePerformance;
import com.academic.management.model.Student;

import java.util.List;
import java.util.Optional;

/**
 * Assembles the academic profile and the dashboard figures.
 *
 * <p>This service is intentionally thin. The joining happens in the
 * {@code v_student_performance} view and the arithmetic happens in
 * {@link AcademicProfile}, and both were built to be the single source of
 * truth for their concern. A service that recalculated the GPA here would
 * be a third implementation to keep in step with the other two, so the
 * only work left is validating the request and shaping the result.
 */
public class AcademicProfileService extends BaseService {

    private final AcademicProfileDao profileDao;
    private final StudentService studentService;

    public AcademicProfileService(AcademicProfileDao profileDao, StudentService studentService) {
        this.profileDao = profileDao;
        this.studentService = studentService;
    }

    @Override
    protected String entityName() {
        return "Academic profile";
    }

    /**
     * Builds the complete profile for a student.
     *
     * @throws com.academic.management.exception.RecordNotFoundException
     *         if the id is unknown, so the UI can say so rather than
     *         showing an empty screen
     */
    public AcademicProfile buildProfile(String studentId) throws AppException {
        String id = studentId == null ? "" : studentId.trim();
        if (id.isEmpty()) {
            throw new com.academic.management.exception.BusinessRuleException(
                    "Select a student to view their academic profile.", "Blank student id");
        }
        return profileDao.buildProfile(id);
    }

    /** The per-course rows behind the profile. */
    public List<CoursePerformance> findCoursePerformances(String studentId) throws AppException {
        return profileDao.findCoursePerformances(studentId.trim());
    }

    /** The student record, or empty when the id is unknown. */
    public Optional<Student> findStudent(String studentId) throws AppException {
        return profileDao.findStudent(studentId == null ? "" : studentId.trim());
    }

    /** True when the id belongs to a real student. */
    public boolean studentExists(String studentId) throws AppException {
        return studentId != null && !studentId.isBlank() && studentService.findByIdSafe(studentId) != null;
    }

    /**
     * Headline figures for the dashboard, in one query.
     */
    public AcademicProfileDao.DashboardSummary loadDashboardSummary() throws AppException {
        return profileDao.loadDashboardSummary();
    }
}
