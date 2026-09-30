package com.academic.management.dao;

import com.academic.management.exception.AppException;
import com.academic.management.exception.DuplicateRecordException;
import com.academic.management.model.AcademicProfile;
import com.academic.management.model.Attendance;
import com.academic.management.model.Course;
import com.academic.management.model.CoursePerformance;
import com.academic.management.model.Enrollment;
import com.academic.management.model.IdName;
import com.academic.management.model.Result;
import com.academic.management.model.Student;
import com.academic.management.model.User;
import com.academic.management.util.DatabaseManager;
import com.academic.management.util.GradingPolicy;
import com.academic.management.util.PasswordHasher;
import com.academic.management.util.StandardGradingPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises every DAO against the live MySQL database loaded from
 * {@code database/sample_data.sql}.
 *
 * <h2>What this is for</h2>
 * A DAO compiles happily while referencing a column that does not exist,
 * writing a value into a {@code STORED GENERATED} column it must not
 * write, or reading a view column under the wrong name. Only a real round
 * trip catches those. So this test talks to MySQL rather than a mock: a
 * mock would happily agree with whatever the DAO expected.
 *
 * <h2>Side effects</h2>
 * The two tests that write ({@code attendance} and {@code results}) read
 * the current row first and put it back afterwards, so the suite is
 * repeatable and leaves the sample data exactly as it found it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DAO layer against live MySQL")
class DaoIntegrationTest {

    private static final String STUDENT_ID = "STU001";
    private static final String COURSE_ID = "CRS001";

    private DatabaseManager database;
    private StudentDao studentDao;
    private FacultyDao facultyDao;
    private CourseDao courseDao;
    private EnrollmentDao enrollmentDao;
    private AttendanceDao attendanceDao;
    private ResultDao resultDao;
    private UserDao userDao;
    private AcademicProfileDao profileDao;
    private final GradingPolicy policy = StandardGradingPolicy.getInstance();

    @BeforeAll
    void connect() throws AppException {
        database = DatabaseManager.getInstance();
        assertTrue(database.verifyConnectivity(),
                "MySQL must be reachable for the integration tests to mean anything.");

        studentDao = new com.academic.management.dao.impl.StudentDaoImpl(database);
        facultyDao = new com.academic.management.dao.impl.FacultyDaoImpl(database);
        courseDao = new com.academic.management.dao.impl.CourseDaoImpl(database);
        enrollmentDao = new com.academic.management.dao.impl.EnrollmentDaoImpl(database);
        attendanceDao = new com.academic.management.dao.impl.AttendanceDaoImpl(database);
        resultDao = new com.academic.management.dao.impl.ResultDaoImpl(database, policy);
        userDao = new com.academic.management.dao.impl.UserDaoImpl(database);
        profileDao = new com.academic.management.dao.impl.AcademicProfileDaoImpl(database);
    }

    @AfterAll
    void releaseConnection() {
        DatabaseManager.resetForTests();
    }

    // ------------------------------------------------------------------
    // Students and faculty
    // ------------------------------------------------------------------

    @Test
    @DisplayName("students: full read, single read and the summary projection")
    void studentsAreReadable() throws AppException {
        List<Student> all = studentDao.findAll();
        assertEquals(6, all.size(), "sample_data.sql inserts six students");
        assertEquals(6, studentDao.count());

        Student first = all.get(0);
        assertNotNull(first.getEmail(), "a mapped Student must carry its real email");
        assertTrue(first.getSemester() >= 1 && first.getSemester() <= 10);

        assertTrue(studentDao.findById(first.getId()).isPresent());
        assertTrue(studentDao.findById("NOPE999").isEmpty(),
                "an unknown id must be absent, not a fabricated Student");
    }

    @Test
    @DisplayName("students: search filters on real columns")
    void studentSearchFiltersRows() throws AppException {
        com.academic.management.model.StudentSearchCriteria criteria =
                new com.academic.management.model.StudentSearchCriteria();
        criteria.setDepartment("Computer Science");
        List<Student> matched = studentDao.search(criteria);
        assertFalse(matched.isEmpty(), "the sample data has a Computer Science cohort");
        assertTrue(matched.stream()
                        .allMatch(student -> "Computer Science".equals(student.getDepartment())),
                "a department filter must not return other departments");
    }

    @Test
    @DisplayName("summaries are IdName projections, not half-built domain objects")
    void summariesAreIdNameProjections() throws AppException {
        List<IdName> students = studentDao.findAllSummaries();
        assertEquals(6, students.size());
        // Summaries are ordered by name, not by id, so this asserts
        // membership rather than position.
        assertTrue(students.stream().anyMatch(row -> "STU001".equals(row.id())),
                "STU001 must appear in the student summary projection");

        List<IdName> faculty = facultyDao.findAllSummaries();
        assertEquals(5, faculty.size());
        // The label carries the name; a Faculty could not be built from
        // two columns without inventing an email and a joining date.
        assertTrue(faculty.get(0).label().contains("("),
                "faculty summary labels include the designation");
    }

    // ------------------------------------------------------------------
    // Courses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("courses: read, filter by faculty, and search")
    void coursesAreReadableAndFilterable() throws AppException {
        List<Course> all = courseDao.findAll();
        assertEquals(6, all.size());
        assertEquals(6, courseDao.findAllSummaries().size());

        String facultyWithCourses = all.get(0).getFacultyId();
        List<Course> mine = courseDao.findByFaculty(facultyWithCourses);
        assertFalse(mine.isEmpty());
        assertTrue(mine.stream().allMatch(c -> facultyWithCourses.equals(c.getFacultyId())));

        List<Course> enrolled = courseDao.findByStudent(STUDENT_ID);
        assertFalse(enrolled.isEmpty());
        assertTrue(enrolled.stream().allMatch(c -> c.getCourseId() != null));

        com.academic.management.model.CourseSearchCriteria criteria =
                new com.academic.management.model.CourseSearchCriteria();
        criteria.setDepartment("Computer Science");
        assertFalse(courseDao.search(criteria).isEmpty());
    }

    // ------------------------------------------------------------------
    // Enrolments
    // ------------------------------------------------------------------

    @Test
    @DisplayName("enrolments: the unique pair is enforced by the database, not by luck")
    void duplicateEnrolmentIsRejected() throws AppException {
        assertTrue(enrollmentDao.isEnrolled(STUDENT_ID, COURSE_ID));
        assertEquals(14, enrollmentDao.count());

        Enrollment clash = new Enrollment(STUDENT_ID, COURSE_ID, 1,
                LocalDate.of(2025, 8, 1), Enrollment.Status.ACTIVE);

        DuplicateRecordException failure = assertThrows(DuplicateRecordException.class,
                () -> enrollmentDao.insert(clash),
                "UNIQUE(student_id, course_id) must surface as a duplicate, not a raw error");
        assertNotNull(failure.getMessage());
        assertNotNull(failure.getTechnicalMessage(),
                "the technical message must record which constraint fired");

        // The failed insert must not have created anything.
        assertEquals(14, enrollmentDao.count());
    }

    @Test
    @DisplayName("enrolments: delete then re-insert leaves the count unchanged")
    void enrolmentCanBeRemovedAndRestored() throws AppException {
        long before = enrollmentDao.count();
        Enrollment original = enrollmentDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                .orElseThrow(() -> new AssertionError("sample data must contain this pair"));

        assertTrue(enrollmentDao.delete(STUDENT_ID, COURSE_ID));
        assertEquals(before - 1, enrollmentDao.count());
        assertFalse(enrollmentDao.isEnrolled(STUDENT_ID, COURSE_ID));

        enrollmentDao.insertAndReturn(original);
        assertEquals(before, enrollmentDao.count());
        assertTrue(enrollmentDao.isEnrolled(STUDENT_ID, COURSE_ID));
    }

    // ------------------------------------------------------------------
    // Attendance - proves the generated column is computed, not written
    // ------------------------------------------------------------------

    @Test
    @DisplayName("attendance: MySQL's generated percentage matches the Java calculation")
    void attendanceUpsertKeepsJavaAndSqlInAgreement() throws AppException {
        Attendance original = attendanceDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                .orElseThrow(() -> new AssertionError("sample data must contain this pair"));
        try {
            // 30 held, 21 attended is exactly 70.00% - a value that is
            // representable in DECIMAL(5,2) with no rounding involved, so
            // any disagreement here is a logic difference, not rounding.
            Attendance changed = new Attendance(STUDENT_ID, COURSE_ID, 30, 21, LocalDate.now());
            attendanceDao.upsert(changed);

            Attendance reloaded = attendanceDao
                    .findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                    .orElseThrow(() -> new AssertionError("row disappeared after upsert"));

            assertEquals(30, reloaded.getClassesHeld());
            assertEquals(21, reloaded.getClassesAttended());
            assertEquals(70.0, reloaded.getAttendancePercentage(), 0.001,
                    "the Java calculation must agree with the generated column");
        } finally {
            attendanceDao.upsert(original);
        }

        Attendance restored = attendanceDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                .orElseThrow(() -> new AssertionError("row disappeared while restoring"));
        assertEquals(original.getClassesHeld(), restored.getClassesHeld());
        assertEquals(original.getClassesAttended(), restored.getClassesAttended());
    }

    @Test
    @DisplayName("attendance: zero classes held yields 0% rather than a divide-by-zero")
    void zeroClassesHeldIsSafe() throws AppException {
        Attendance original = attendanceDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                .orElseThrow(() -> new AssertionError("sample data must contain this pair"));
        try {
            attendanceDao.upsert(new Attendance(STUDENT_ID, COURSE_ID, 0, 0, LocalDate.now()));
            Attendance reloaded = attendanceDao
                    .findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                    .orElseThrow(() -> new AssertionError("row disappeared"));

            assertEquals(0.0, reloaded.getAttendancePercentage(), 0.001);
        } finally {
            attendanceDao.upsert(original);
        }
    }

    // ------------------------------------------------------------------
    // Results - proves the grade written is the policy's, not a SQL CASE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("results: the stored grade is the one the policy computes")
    void resultGradeComesFromThePolicy() throws AppException {
        Result original = resultDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                .orElseThrow(() -> new AssertionError("sample data must contain this pair"));
        try {
            // 36 internal + 54 external = 90/100 = 90% = A+ on this scale.
            Result candidate = new Result(STUDENT_ID, COURSE_ID, 36, 54,
                    LocalDate.of(2025, 12, 5), "integration test", policy);
            assertEquals("A+", candidate.getGradeCode());
            assertEquals(5.0, candidate.getGradePoint(), 0.001);

            resultDao.upsert(candidate);
            Result reloaded = resultDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                    .orElseThrow(() -> new AssertionError("row disappeared after upsert"));

            assertEquals(90.0, reloaded.getPercentage(), 0.01);
            assertEquals("A+", reloaded.getGradeCode());
            assertTrue(reloaded.isPass());
            assertEquals("integration test", reloaded.getRemarks());
        } finally {
            resultDao.upsert(original);
        }
    }

    @Test
    @DisplayName("results: a failing mark is stored and read back as a fail")
    void failingResultRoundTrips() throws AppException {
        Result original = resultDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                .orElseThrow(() -> new AssertionError("sample data must contain this pair"));
        try {
            // 10 + 15 = 25/100 = 25%, which is below the 40% pass mark.
            resultDao.upsert(new Result(STUDENT_ID, COURSE_ID, 10, 15,
                    LocalDate.of(2025, 12, 5), "deferred", policy));
            Result reloaded = resultDao.findByStudentAndCourse(STUDENT_ID, COURSE_ID)
                    .orElseThrow(() -> new AssertionError("row disappeared after upsert"));

            assertEquals(25.0, reloaded.getPercentage(), 0.01);
            assertEquals("F", reloaded.getGradeCode());
            assertFalse(reloaded.isPass());
        } finally {
            resultDao.upsert(original);
        }
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    @Test
    @DisplayName("users: the seeded admin hash really does match its documented password")
    void seededAdminPasswordVerifies() throws AppException {
        User admin = userDao.findByUsername("admin")
                .orElseThrow(() -> new AssertionError("sample data must seed an admin user"));

        assertTrue(admin.isAdministrator());
        assertTrue(admin.isActive());
        assertEquals(2, userDao.count());
        assertTrue(userDao.findByUsername("does-not-exist").isEmpty());

        // This is the check that matters: the stored hash is a real hash
        // of the documented credential, not a placeholder.
        assertTrue(PasswordHasher.matches(admin.getSalt(), "admin@123", admin.getPasswordHash()),
                "the seeded admin password must verify against the sample credentials");
        assertFalse(PasswordHasher.matches(admin.getSalt(), "wrong", admin.getPasswordHash()));
    }

    // ------------------------------------------------------------------
    // Academic profile
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profile: the aggregate is built from stored rows, not hard-coded")
    void profileIsAssembledFromTheDatabase() throws AppException {
        AcademicProfile profile = profileDao.buildProfile(STUDENT_ID);

        assertEquals(STUDENT_ID, profile.getStudent().getId());
        assertTrue(profile.getCourseCount() > 0, "STU001 is enrolled in the sample data");
        assertTrue(profile.getCoursesWithAttendance() > 0);
        assertTrue(profile.getGpa() > 0.0, "the sample data has published results");
        assertTrue(profile.getTotalCredits() > 0.0);
        assertTrue(profile.getOverallPercentage() > 0.0);
        assertNotNull(profile.getOverallGrade());
        assertNotNull(profile.getStandingSummary());

        // The GPA is a credits-weighted average, so it cannot exceed the
        // scale's maximum however good the record is.
        assertTrue(profile.getGpa() <= 5.0, "GPA is on the 5.00 scale");

        // Every performance row must name a real course.
        for (CoursePerformance course : profile.getCourses()) {
            assertNotNull(course.getCourseCode());
            assertNotNull(course.getCourseName());
        }
    }

    @Test
    @DisplayName("profile: the reported GPA and average match the underlying rows")
    void profileArithmeticMatchesTheRows() throws AppException {
        AcademicProfile profile = profileDao.buildProfile(STUDENT_ID);
        List<CoursePerformance> rows = profileDao.findCoursePerformances(STUDENT_ID);

        assertEquals(rows.size(), profile.getCourseCount(),
                "the profile and the raw row list must describe the same courses");

        // Recompute the average independently from the raw rows, so this
        // fails if AcademicProfile invents its own arithmetic.
        double percentageSum = 0;
        int graded = 0;
        for (CoursePerformance row : rows) {
            if (row.hasResult()) {
                percentageSum += row.getResultPercentage();
                graded++;
            }
        }
        assertTrue(graded > 0, "the sample data has published results for STU001");
        assertEquals(round2(percentageSum / graded), profile.getOverallPercentage(), 0.01,
                "the overall percentage must be the mean of the per-course rows");

        // Recompute the credits-weighted GPA the same way.
        double weighted = 0;
        double credits = 0;
        for (CoursePerformance row : rows) {
            if (row.hasResult() && row.getGradePoint() != null) {
                weighted += row.getGradePoint() * row.getCredits();
                credits += row.getCredits();
            }
        }
        assertEquals(round2(weighted / credits), profile.getGpa(), 0.01,
                "the GPA must be the credits-weighted mean of the per-course rows");
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Test
    @DisplayName("profile: an unknown student is a not-found error, not an empty screen")
    void unknownStudentIsReported() {
        assertThrows(AppException.class, () -> profileDao.buildProfile("NOPE999"));
    }

    @Test
    @DisplayName("dashboard: the summary matches the tables it counts")
    void dashboardSummaryMatchesTheData() throws AppException {
        AcademicProfileDao.DashboardSummary summary = profileDao.loadDashboardSummary();

        assertEquals(studentDao.count(), summary.studentCount());
        assertEquals(facultyDao.count(), summary.facultyCount());
        assertEquals(courseDao.count(), summary.courseCount());
        assertEquals(enrollmentDao.count(), summary.enrollmentCount());
        assertEquals(attendanceDao.count(), summary.attendanceRecords());
        assertEquals(resultDao.count(), summary.resultRecords());
        assertTrue(summary.averageResultPercent() > 0.0);
        assertTrue(summary.averageAttendancePercent() > 0.0);
    }
}
