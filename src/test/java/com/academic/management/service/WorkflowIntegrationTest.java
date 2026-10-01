package com.academic.management.service;

import com.academic.management.exception.AppException;
import com.academic.management.model.AcademicProfile;
import com.academic.management.model.Attendance;
import com.academic.management.model.Course;
import com.academic.management.model.CoursePerformance;
import com.academic.management.model.Gender;
import com.academic.management.model.Grade;
import com.academic.management.model.Result;
import com.academic.management.model.Student;
import com.academic.management.model.User;
import com.academic.management.util.DatabaseManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walks one student's whole academic year through the service layer, against
 * the live MySQL database.
 *
 * <h2>Why this exists</h2>
 * The other two suites check the rules in isolation: {@code DaoIntegrationTest}
 * proves each query round trips, and {@code ServiceRulesTest} proves the rules
 * refuse bad input. Neither proves the two agree. A rule can be perfectly
 * implemented and a DAO can be perfectly correct, and the workflow still break
 * because one of them writes a status the other never expects to read back.
 * The screens are what actually run this path, so the path is worth testing
 * end to end rather than in pieces.
 *
 * <h2>What it covers</h2>
 * Sign in, create a student and a course, enrol, record attendance, publish a
 * result, read the calculated profile, see the dashboard and the search index
 * agree, then delete the student and confirm the cascade took everything with
 * it. The cascade is the half most worth pinning down: a delete that leaves an
 * orphaned attendance row behind is invisible in a single-table test and
 * obvious to a user who then finds last term's attendance still on screen.
 *
 * <h2>Side effects</h2>
 * Every test here writes, so each one deletes what it created in a
 * {@code finally} block and the suite is safe to re-run against the same
 * database. The ids are in the 900 series, which the sample data does not use,
 * so a half-finished previous run cannot collide with a real record.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Whole academic workflow against live MySQL")
class WorkflowIntegrationTest {

    private static final String STUDENT_ID = "STU900";
    private static final String COURSE_ID = "CRS900";
    private static final String STUDENT_NAME = "Wilhelmina Testcase";
    private static final String COURSE_NAME = "Integration Testing";

    private DatabaseManager database;
    private ServiceRegistry services;

    @BeforeAll
    void connect() throws AppException {
        database = DatabaseManager.getInstance();
        assertTrue(database.verifyConnectivity(),
                "MySQL must be reachable for the integration tests to mean anything.");
        services = new ServiceRegistry(database);
    }

    @AfterAll
    void releaseConnection() {
        DatabaseManager.resetForTests();
    }

    // ------------------------------------------------------------------
    // The workflow
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("a student can be created, enrolled, graded and read back as a profile")
    void aStudentCanBeTakenThroughTheWholeYear() throws AppException {
        // The dashboard numbers before anything is added, so the test can
        // prove the counts moved rather than merely that they are positive.
        long studentsBefore = services.students().count();
        long coursesBefore = services.courses().count();
        long enrolmentsBefore = services.enrollments().count();
        long resultsBefore = services.results().count();

        try {
            // 1. Create the two records the rest of the year hangs off.
            services.students().create(new Student(STUDENT_ID, STUDENT_NAME,
                    LocalDate.of(2004, 3, 17), Gender.FEMALE,
                    "wilhelmina.testcase@example.edu", "+1 555 0142",
                    "12 Testcase Way", "Computer Science", 1,
                    LocalDate.of(2024, 8, 1)));
            services.courses().create(new Course(COURSE_ID, "CS900",
                    COURSE_NAME, 4.0, null, "Computer Science", 1));

            // The suggested ids are what the add forms offer, so they have to
            // be free - a collision here would be a bug the user hits first.
            assertFalse(services.students().findSummaries().stream()
                            .anyMatch(s -> s.id().equals(STUDENT_ID + "X")),
                    "the test id must not look like an existing id");

            // 2. Enrol. The service refuses a duplicate, and the semester
            //    suggestion is what the add form defaults to.
            services.enrollments().enroll(STUDENT_ID, COURSE_ID, 1, LocalDate.of(2024, 8, 5));
            assertTrue(services.enrollments().isEnrolled(STUDENT_ID, COURSE_ID));
            assertEquals(1, services.enrollments().suggestSemester(STUDENT_ID));

            // 3. An enrolled, ungraded course is exactly what the results
            //    screen offers, which is the list the publish form is built
            //    from.
            assertTrue(services.results().findGradableCourses(STUDENT_ID).stream()
                            .anyMatch(c -> c.getCourseId().equals(COURSE_ID)),
                    "an enrolled course with no result should be gradable");

            // 4. Attendance. 34 of 40 is 85%.
            services.attendance().record(STUDENT_ID, COURSE_ID, 40, 34);
            Attendance attendance = services.attendance()
                    .findByStudentAndCourse(STUDENT_ID, COURSE_ID);
            assertEquals(40, attendance.getClassesHeld());
            assertEquals(34, attendance.getClassesAttended());

            // 5. Result. 32 of 40 internal plus 50 of 60 external is 82 per
            //    cent, which the published scale calls an A.
            Result published = services.results()
                    .publishOn(STUDENT_ID, COURSE_ID, 32, 50, LocalDate.of(2024, 12, 20),
                            "Solid work on the integration suite.");
            assertEquals(Grade.A, published.getGrade());

            // Once graded, the course must disappear from the gradable list,
            // or the publish form would offer it a second time.
            assertFalse(services.results().findGradableCourses(STUDENT_ID).stream()
                            .anyMatch(c -> c.getCourseId().equals(COURSE_ID)),
                    "a graded course should no longer be offered for publishing");

            // 6. The calculated profile, which is what the Profiles screen
            //    renders. Every figure on that screen is computed here.
            AcademicProfile profile = services.profiles().buildProfile(STUDENT_ID);
            assertEquals(STUDENT_NAME, profile.getStudent().getName());
            assertEquals(1, profile.getCourseCount());
            assertEquals(1, profile.getCoursesWithResult());
            assertEquals(1, profile.getCoursesWithAttendance());
            assertEquals(1, profile.getPassedCourseCount());
            assertEquals(0, profile.getFailedCourseCount());

            CoursePerformance performance = profile.getCourses().get(0);
            assertEquals(COURSE_ID, performance.getCourseId());
            assertEquals(Grade.A.getCode(), performance.getGrade());
            assertEquals(85.0, performance.getAttendancePercentage(), 0.01);
            assertEquals(82.0, performance.getResultPercentage(), 0.01);

            // 4 credits at 4.00 is a 4.00 GPA on one course.
            assertEquals(4.0, profile.getTotalCredits(), 0.001);
            assertEquals(4.0, profile.getGpa(), 0.001);
            assertEquals(Grade.A, profile.getOverallGrade());
            assertTrue(profile.isInGoodStanding());

            // 7. The dashboard counts the new records, so the numbers on the
            //    first screen move the moment anything is added.
            ServiceRegistry.Dashboard after = services.loadDashboard();
            assertEquals(studentsBefore + 1, after.students());
            assertEquals(coursesBefore + 1, after.courses());
            assertEquals(enrolmentsBefore + 1, after.enrolments());
            assertEquals(resultsBefore + 1, after.resultRecords());

            // 8. And the new record is searchable by name, which is the only
            //    way the Search screen finds anything.
            assertTrue(services.search()
                            .search(SearchService.Scope.STUDENTS, "Wilhelmina")
                            .stream()
                            .anyMatch(hit -> hit.id().equals(STUDENT_ID)),
                    "the new student should be findable by name");
        } finally {
            cleanUp();
        }
    }

    @Test
    @Order(2)
    @DisplayName("deleting a student takes the enrolment, attendance and result with it")
    void deletingAStudentCascadesThroughTheirYear() throws AppException {
        try {
            seed();
            long enrolmentsBefore = services.enrollments().count();
            long resultsBefore = services.results().count();

            // The impact text the confirmation dialog shows is computed from
            // the same rows the cascade is about to remove, so the warning
            // cannot disagree with what happens.
            StudentService.DeletionImpact impact =
                    services.students().describeDeletion(STUDENT_ID);
            assertEquals(1, impact.enrolmentCount());
            assertEquals(1, impact.resultCount());

            services.students().delete(STUDENT_ID);

            // The dependent rows are gone rather than orphaned: an orphaned
            // attendance row would still be on the Attendance screen, still
            // counting towards the course summary, with no student to own it.
            assertFalse(services.enrollments().isEnrolled(STUDENT_ID, COURSE_ID));
            assertThrows(AppException.class,
                    () -> services.attendance().findByStudentAndCourse(STUDENT_ID, COURSE_ID));
            assertThrows(AppException.class,
                    () -> services.results().findByStudentAndCourse(STUDENT_ID, COURSE_ID));

            assertEquals(enrolmentsBefore - 1, services.enrollments().count());
            assertEquals(resultsBefore - 1, services.results().count());
            assertFalse(services.profiles().studentExists(STUDENT_ID));
        } finally {
            cleanUp();
        }
    }

    @Test
    @Order(3)
    @DisplayName("the sign-in the login screen performs works against the sample accounts")
    void theSampleAccountsCanSignIn() throws AppException {
        // The credentials in the README and on the login screen's hint. If a
        // change to the password rules or the hashing ever breaks these, the
        // application is unopenable, so they are worth pinning.
        assertEquals(User.Role.ADMIN, services.authentication()
                .authenticate("admin", "admin@123").getRole());
        assertEquals(User.Role.FACULTY, services.authentication()
                .authenticate("faculty", "faculty@123").getRole());
        assertTrue(services.authentication().hasAnyAccount());
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /**
     * Puts the record back in place for a test that needs it to already
     * exist, so each test reads as a single journey rather than four.
     */
    private void seed() throws AppException {
        services.students().create(new Student(STUDENT_ID, STUDENT_NAME,
                LocalDate.of(2004, 3, 17), Gender.FEMALE,
                "wilhelmina.testcase@example.edu", "+1 555 0142",
                "12 Testcase Way", "Computer Science", 1,
                LocalDate.of(2024, 8, 1)));
        services.courses().create(new Course(COURSE_ID, "CS900",
                COURSE_NAME, 4.0, null, "Computer Science", 1));
        services.enrollments().enroll(STUDENT_ID, COURSE_ID, 1, LocalDate.of(2024, 8, 5));
        services.attendance().record(STUDENT_ID, COURSE_ID, 40, 34);
        services.results().publishOn(STUDENT_ID, COURSE_ID, 32, 50,
                LocalDate.of(2024, 12, 20), null);
    }

    /**
     * Removes whatever this suite created, in the order the foreign keys
     * allow. The student goes last because the course cannot be deleted while
     * an enrolment still points at it.
     */
    private void cleanUp() throws AppException {
        if (services.profiles().studentExists(STUDENT_ID)) {
            services.students().delete(STUDENT_ID);
        }
        if (services.courses().findAll().stream().anyMatch(c -> c.getCourseId().equals(COURSE_ID))) {
            services.courses().delete(COURSE_ID);
        }
    }
}
