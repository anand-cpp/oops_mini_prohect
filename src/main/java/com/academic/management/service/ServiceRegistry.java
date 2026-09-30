package com.academic.management.service;

import com.academic.management.dao.StudentDao;
import com.academic.management.exception.AppException;
import com.academic.management.model.Student;
import com.academic.management.model.StudentSearchCriteria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Facade the UI is constructed with.
 *
 * <h2>Why one object holds them all</h2>
 * The service layer has real dependencies between its parts -
 * {@link AttendanceService} needs {@link StudentService} for the
 * enrolment rule, and {@link SearchService} needs all three entity
 * services. Resolving that graph by hand at every construction site would
 * mean every caller repeating the same eleven lines and getting the
 * wiring subtly wrong. Doing it once, here, means the UI is handed a
 * fully connected set of services and cannot accidentally use a
 * misconfigured one.
 *
 * <p>It is also the single place the DAO implementations are named, which
 * is what lets a test swap in a different set.
 */
public class ServiceRegistry {

    private final StudentDao studentDao;
    private final com.academic.management.dao.FacultyDao facultyDao;
    private final com.academic.management.dao.CourseDao courseDao;
    private final com.academic.management.dao.EnrollmentDao enrollmentDao;
    private final com.academic.management.dao.AttendanceDao attendanceDao;
    private final com.academic.management.dao.ResultDao resultDao;
    private final com.academic.management.dao.AcademicProfileDao profileDao;
    private final com.academic.management.dao.UserDao userDao;

    private final AuthenticationService authenticationService;
    private final StudentService studentService;
    private final FacultyService facultyService;
    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final AttendanceService attendanceService;
    private final ResultService resultService;
    private final AcademicProfileService academicProfileService;
    private final SearchService searchService;

    /** Wires the real MySQL-backed implementation of every DAO. */
    public ServiceRegistry(com.academic.management.util.DatabaseManager database) {
        this(
                new com.academic.management.dao.impl.StudentDaoImpl(database),
                new com.academic.management.dao.impl.FacultyDaoImpl(database),
                new com.academic.management.dao.impl.CourseDaoImpl(database),
                new com.academic.management.dao.impl.EnrollmentDaoImpl(database),
                new com.academic.management.dao.impl.AttendanceDaoImpl(database),
                new com.academic.management.dao.impl.ResultDaoImpl(database),
                new com.academic.management.dao.impl.AcademicProfileDaoImpl(database),
                new com.academic.management.dao.impl.UserDaoImpl(database));
    }

    /**
     * Wires a supplied set of DAOs.
     *
     * <p>This is the constructor the tests use: an in-memory or
     * hand-written DAO can be passed in and the whole service graph comes
     * up around it.
     */
    public ServiceRegistry(StudentDao studentDao,
                           com.academic.management.dao.FacultyDao facultyDao,
                           com.academic.management.dao.CourseDao courseDao,
                           com.academic.management.dao.EnrollmentDao enrollmentDao,
                           com.academic.management.dao.AttendanceDao attendanceDao,
                           com.academic.management.dao.ResultDao resultDao,
                           com.academic.management.dao.AcademicProfileDao profileDao,
                           com.academic.management.dao.UserDao userDao) {
        this.studentDao = studentDao;
        this.facultyDao = facultyDao;
        this.courseDao = courseDao;
        this.enrollmentDao = enrollmentDao;
        this.attendanceDao = attendanceDao;
        this.resultDao = resultDao;
        this.profileDao = profileDao;
        this.userDao = userDao;

        this.authenticationService = new AuthenticationService(userDao);
        this.studentService = new StudentService(studentDao, enrollmentDao, resultDao, courseDao);
        this.facultyService = new FacultyService(facultyDao, courseDao);
        this.courseService = new CourseService(courseDao, facultyDao, enrollmentDao);
        this.enrollmentService = new EnrollmentService(enrollmentDao, studentDao, courseDao,
                attendanceDao, resultDao);
        this.attendanceService = new AttendanceService(attendanceDao, enrollmentDao, studentDao,
                courseDao, studentService);
        this.resultService = new ResultService(resultDao, enrollmentDao, studentDao, courseDao,
                studentService);
        this.academicProfileService = new AcademicProfileService(profileDao, studentService);
        this.searchService = new SearchService(studentService, facultyService, courseService);
    }

    public AuthenticationService authentication() {
        return authenticationService;
    }

    public StudentService students() {
        return studentService;
    }

    public FacultyService faculty() {
        return facultyService;
    }

    public CourseService courses() {
        return courseService;
    }

    public EnrollmentService enrollments() {
        return enrollmentService;
    }

    public AttendanceService attendance() {
        return attendanceService;
    }

    public ResultService results() {
        return resultService;
    }

    public AcademicProfileService profiles() {
        return academicProfileService;
    }

    public SearchService search() {
        return searchService;
    }

    // ------------------------------------------------------------------
    // Dashboard figures
    // ------------------------------------------------------------------

    /**
     * Every headline number the dashboard shows, gathered in one place so
     * the panel does not have to know which service owns which count.
     */
    public Dashboard loadDashboard() throws AppException {
        com.academic.management.dao.AcademicProfileDao.DashboardSummary summary =
                academicProfileService.loadDashboardSummary();
        return new Dashboard(
                studentService.count(),
                facultyService.count(),
                courseService.count(),
                enrollmentService.count(),
                attendanceService.count(),
                resultService.count(),
                summary.averageResultPercent(),
                summary.averageAttendancePercent());
    }

    /**
     * The dashboard's summary row.
     *
     * @param students          number of students
     * @param faculty           number of faculty members
     * @param courses           number of courses
     * @param enrolments        number of enrolments
     * @param attendanceRecords number of attendance records
     * @param resultRecords     number of result records
     * @param averageResult     mean result percentage, 0 when none
     * @param averageAttendance mean attendance percentage, 0 when none
     */
    public record Dashboard(long students, long faculty, long courses, long enrolments,
                            long attendanceRecords, long resultRecords,
                            double averageResult, double averageAttendance) {
    }
}
