package com.academic.management.service;

import com.academic.management.dao.AcademicProfileDao;
import com.academic.management.dao.AttendanceDao;
import com.academic.management.dao.CourseDao;
import com.academic.management.dao.EnrollmentDao;
import com.academic.management.dao.FacultyDao;
import com.academic.management.dao.ResultDao;
import com.academic.management.dao.StudentDao;
import com.academic.management.dao.UserDao;
import com.academic.management.exception.AppException;
import com.academic.management.model.AcademicProfile;
import com.academic.management.model.Attendance;
import com.academic.management.model.Course;
import com.academic.management.model.CoursePerformance;
import com.academic.management.model.CourseSearchCriteria;
import com.academic.management.model.Enrollment;
import com.academic.management.model.Faculty;
import com.academic.management.model.FacultySearchCriteria;
import com.academic.management.model.Gender;
import com.academic.management.model.Grade;
import com.academic.management.model.IdName;
import com.academic.management.model.Result;
import com.academic.management.model.Student;
import com.academic.management.model.StudentSearchCriteria;
import com.academic.management.model.User;
import com.academic.management.util.GradingPolicy;
import com.academic.management.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-layer business rules.
 *
 * <h2>Why these use in-memory DAOs</h2>
 * {@code DaoIntegrationTest} already proves the JDBC against real MySQL.
 * These tests ask a different question: <em>is the rule right?</em> The
 * interesting behaviour - "cannot enrol twice", "cannot record attendance
 * for a non-enrolled student", "cannot delete faculty who still teach
 * courses" - is a decision made in the service. Testing a decision means
 * setting up the exact precondition and observing the outcome, and fakes
 * make that precise and fast. Together the two suites cover the rules and
 * the plumbing.
 *
 * <h2>Why hand-written fakes rather than a mocking framework</h2>
 * These implement the real interfaces, so a signature change in a DAO
 * breaks this file and every call site at once. More importantly they keep
 * the behaviour the rules actually depend on - the unique (student, course)
 * pair, the highest-numeric-suffix lookup, the cross-DAO visibility that
 * makes "faculty teaches a course" answerable at all. A generated mock
 * would return null for all of it and the tests would pass while proving
 * nothing.
 */
@DisplayName("Service layer business rules")
class ServiceRulesTest {

    private FakeDaos daos;
    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        daos = new FakeDaos();
        registry = new ServiceRegistry(daos.students, daos.faculty, daos.courses,
                daos.enrollments, daos.attendance, daos.results, daos.profiles, daos.users);
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private static LocalDate twentyYearsAgo() {
        return LocalDate.now().minusYears(20);
    }

    private static LocalDate oneYearAgo() {
        return LocalDate.now().minusYears(1);
    }

    private Student givenStudent(String id, String name, String email, String department,
                                 int semester) throws AppException {
        Student student = new Student(id, name, twentyYearsAgo(), Gender.MALE, email,
                "9876543210", "1 Test Street", department, semester, oneYearAgo());
        registry.students().create(student);
        return student;
    }

    private Faculty givenFaculty(String id, String name, String email, String designation)
            throws AppException {
        Faculty member = new Faculty(id, name, twentyYearsAgo(), Gender.FEMALE, email,
                "9876500001", "2 Test Street", "Computer Science", designation, "Block A",
                oneYearAgo());
        registry.faculty().create(member);
        return member;
    }

    private Course givenCourse(String id, String code, String name, String facultyId, int semester)
            throws AppException {
        Course course = new Course(id, code, name, 4, facultyId, "Computer Science", semester,
                "A course");
        registry.courses().create(course);
        return course;
    }

    /** A student, a faculty member and a course, all mutually related. */
    private void givenEnrolledStudent() throws AppException {
        givenFaculty("FAC001", "Meera Iyer", "meera@example.edu", "Professor");
        givenStudent("STU001", "Arjun Krishnan", "arjun@example.edu", "Computer Science", 3);
        givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);
        registry.enrollments().enroll("STU001", "CRS001", 3, oneYearAgo());
    }

    // ==================================================================
    // Students
    // ==================================================================

    @Nested
    @DisplayName("Student rules")
    class StudentRules {

        @Test
        @DisplayName("a duplicate id is refused with a usable message")
        void duplicateIdIsRefused() throws AppException {
            givenStudent("STU001", "Arjun Krishnan", "arjun@example.edu", "Computer Science", 3);

            AppException failure = assertThrows(AppException.class, () ->
                    givenStudent("STU001", "Someone Else", "other@example.edu",
                            "Computer Science", 1));
            assertTrue(failure.getMessage().toLowerCase().contains("already"),
                    "the message should say the id is taken: " + failure.getMessage());
        }

        @Test
        @DisplayName("a duplicate email is refused even with a different id")
        void duplicateEmailIsRefused() throws AppException {
            givenStudent("STU001", "Arjun Krishnan", "arjun@example.edu", "Computer Science", 3);

            AppException failure = assertThrows(AppException.class, () ->
                    givenStudent("STU002", "Diya Menon", "arjun@example.edu",
                            "Computer Science", 2));
            assertTrue(failure.getMessage().toLowerCase().contains("already"),
                    "the message should say the email is taken: " + failure.getMessage());
        }

        @Test
        @DisplayName("a student may keep their own email when updating")
        void emailIsExcludedFromItsOwnUniquenessCheck() throws AppException {
            Student student = givenStudent("STU001", "Arjun Krishnan", "arjun@example.edu",
                    "Computer Science", 3);

            student.setEmail("arjun@example.edu");
            registry.students().update(student);

            assertEquals(1, registry.students().findAll().size());
        }

        @Test
        @DisplayName("a malformed email never reaches the DAO")
        void invalidEmailIsRejectedByTheModel() {
            assertThrows(AppException.class, () -> new Student("STU001", "Bad Email",
                    twentyYearsAgo(), Gender.MALE, "not-an-email", null, null,
                    "Computer Science", 1, oneYearAgo()));
            assertEquals(0, daos.students.rows.size(),
                    "the invalid student must not have been stored");
        }

        @Test
        @DisplayName("a semester outside 1..10 is rejected")
        void semesterRangeIsEnforced() {
            assertThrows(AppException.class, () -> new Student("STU001", "Bad Semester",
                    twentyYearsAgo(), Gender.MALE, "bad@example.edu", null, null,
                    "Computer Science", 11, oneYearAgo()));
        }

        @Test
        @DisplayName("the next suggested id follows the highest existing one")
        void suggestedIdSkipsDeletedRecords() throws AppException {
            givenStudent("STU001", "One", "one@example.edu", "Computer Science", 1);
            givenStudent("STU007", "Seven", "seven@example.edu", "Computer Science", 1);

            // Deleting a middle record must not make the next id collide,
            // which is why the service asks for the highest suffix rather
            // than counting rows.
            daos.students.rows.removeIf(s -> s.getId().equals("STU001"));

            assertEquals("STU008", registry.students().suggestNextId());
        }

        @Test
        @DisplayName("deletion impact is described before anything is removed")
        void deletionImpactIsReported() throws AppException {
            givenEnrolledStudent();
            registry.attendance().record("STU001", "CRS001", 40, 36);
            registry.results().publish("STU001", "CRS001", 36, 54, null);

            StudentService.DeletionImpact impact = registry.students().describeDeletion("STU001");
            assertEquals(1, impact.enrolmentCount());
            assertEquals(1, impact.resultCount());
            assertTrue(impact.describe().contains("1 result"),
                    "the dialog text should name what will be lost: " + impact.describe());

            registry.students().delete("STU001");

            assertEquals(0, registry.students().count());
        }

        @Test
        @DisplayName("deleting an unknown student is reported, not ignored")
        void deletingAnUnknownStudentFails() {
            assertThrows(AppException.class, () -> registry.students().delete("STU999"));
        }
    }

    // ==================================================================
    // Faculty
    // ==================================================================

    @Nested
    @DisplayName("Faculty rules")
    class FacultyRules {

        @Test
        @DisplayName("cannot be deleted while still teaching courses")
        void facultyWithCoursesCannotBeDeleted() throws AppException {
            givenFaculty("FAC001", "Meera Iyer", "meera@example.edu", "Professor");
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            AppException failure = assertThrows(AppException.class,
                    () -> registry.faculty().delete("FAC001"));
            assertTrue(failure.getMessage().contains("teaches"),
                    "the message should explain why: " + failure.getMessage());
            assertEquals(1, registry.courses().count(), "the course must survive");
        }

        @Test
        @DisplayName("can be deleted once the courses are reassigned")
        void facultyCanBeDeletedAfterReassignment() throws AppException {
            givenFaculty("FAC001", "Meera Iyer", "meera@example.edu", "Professor");
            givenFaculty("FAC002", "Ravi Kumar", "ravi@example.edu", "Lecturer");
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            registry.courses().assignFaculty("CRS001", "FAC002");
            assertEquals("FAC002", registry.courses().findById("CRS001").getFacultyId());

            registry.faculty().delete("FAC001");

            assertEquals(1, registry.faculty().count());
            assertNotNull(registry.courses().findById("CRS001").getFacultyId(),
                    "the course must not be orphaned by the delete");
        }

        @Test
        @DisplayName("a duplicate id is refused")
        void duplicateIdIsRefused() throws AppException {
            givenFaculty("FAC001", "Meera Iyer", "meera@example.edu", "Professor");

            assertThrows(AppException.class, () ->
                    givenFaculty("FAC001", "Another", "another@example.edu", "Lecturer"));
        }
    }

    // ==================================================================
    // Courses
    // ==================================================================

    @Nested
    @DisplayName("Course rules")
    class CourseRules {

        @Test
        @DisplayName("the code must be unique")
        void duplicateCodeIsRefused() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            assertThrows(AppException.class, () ->
                    givenCourse("CRS002", "CS101", "Another course", "FAC001", 1));
        }

        @Test
        @DisplayName("a course may keep its own code when updating")
        void codeIsExcludedFromItsOwnCheck() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            Course course = givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            course.setCourseName("Programming II");
            registry.courses().update(course);

            assertEquals("Programming II", registry.courses().findById("CRS001").getCourseName());
        }

        @Test
        @DisplayName("assignment to a non-existent faculty member is refused")
        void unknownFacultyAssignmentIsRefused() {
            AppException failure = assertThrows(AppException.class, () ->
                    givenCourse("CRS001", "CS101", "Programming", "FAC999", 1));
            assertTrue(failure.getMessage().contains("FAC999"),
                    "the message should name the bad id: " + failure.getMessage());
        }

        @Test
        @DisplayName("an unassigned course is allowed")
        void nullFacultyIsAllowed() throws AppException {
            givenCourse("CRS001", "CS101", "Programming", null, 1);

            assertFalse(registry.courses().findById("CRS001").hasFaculty());
            assertNull(registry.courses().findById("CRS001").getFacultyId());
        }

        @Test
        @DisplayName("cannot be deleted while students are enrolled")
        void courseWithEnrolmentsCannotBeDeleted() throws AppException {
            givenEnrolledStudent();

            AppException failure = assertThrows(AppException.class,
                    () -> registry.courses().delete("CRS001"));
            assertTrue(failure.getMessage().contains("enrolled"),
                    "the message should explain why: " + failure.getMessage());
        }

        @Test
        @DisplayName("can be deleted once the enrolments are gone")
        void courseCanBeDeletedAfterUnenrolment() throws AppException {
            givenEnrolledStudent();
            registry.enrollments().unenroll("STU001", "CRS001");

            registry.courses().delete("CRS001");

            assertEquals(0, registry.courses().count());
        }
    }

    // ==================================================================
    // Enrolment
    // ==================================================================

    @Nested
    @DisplayName("Enrolment rules")
    class EnrolmentRules {

        @Test
        @DisplayName("a student cannot be enrolled on the same course twice")
        void duplicateEnrolmentIsRefused() throws AppException {
            givenEnrolledStudent();

            AppException failure = assertThrows(AppException.class,
                    () -> registry.enrollments().enroll("STU001", "CRS001", 3, oneYearAgo()));
            assertTrue(failure.getMessage().contains("already enrolled"),
                    "the message should say so plainly: " + failure.getMessage());
        }

        @Test
        @DisplayName("both the student and the course must exist")
        void enrolmentRequiresRealRecords() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            assertThrows(AppException.class,
                    () -> registry.enrollments().enroll("STU999", "CRS001", 1, oneYearAgo()));

            givenStudent("STU001", "Arjun", "arjun@example.edu", "Computer Science", 1);
            assertThrows(AppException.class,
                    () -> registry.enrollments().enroll("STU001", "CRS999", 1, oneYearAgo()));
        }

        @Test
        @DisplayName("the same student may take a different course")
        void differentCoursesAreAllowed() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenStudent("STU001", "Arjun", "arjun@example.edu", "Computer Science", 3);
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);
            givenCourse("CRS002", "CS102", "Databases", "FAC001", 2);

            registry.enrollments().enroll("STU001", "CRS001", 3, oneYearAgo());
            registry.enrollments().enroll("STU001", "CRS002", 3, oneYearAgo());

            assertEquals(2, registry.enrollments().count());
        }

        @Test
        @DisplayName("removing it reports the attendance and result it will remove")
        void unenrolmentDescribesItsImpact() throws AppException {
            givenEnrolledStudent();
            registry.attendance().record("STU001", "CRS001", 40, 36);
            registry.results().publish("STU001", "CRS001", 36, 54, null);

            EnrollmentService.DeletionImpact impact =
                    registry.enrollments().describeDeletion("STU001", "CRS001");

            assertTrue(impact.hasAttendance());
            assertTrue(impact.hasResult());
            assertTrue(impact.describe().contains("attendance"), impact.describe());
            assertTrue(impact.describe().contains("result"), impact.describe());
        }

        @Test
        @DisplayName("an enrolment with nothing attached needs no warning about it")
        void unenrolmentImpactIsQuietWhenNothingIsAttached() throws AppException {
            givenEnrolledStudent();

            EnrollmentService.DeletionImpact impact =
                    registry.enrollments().describeDeletion("STU001", "CRS001");

            assertFalse(impact.hasAttendance());
            assertFalse(impact.hasResult());
        }
    }

    // ==================================================================
    // Attendance
    // ==================================================================

    @Nested
    @DisplayName("Attendance rules")
    class AttendanceRules {

        @Test
        @DisplayName("cannot be recorded for a student who is not enrolled")
        void attendanceRequiresEnrolment() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenStudent("STU001", "Arjun", "arjun@example.edu", "Computer Science", 3);
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);
            // Deliberately no enrolment.

            AppException failure = assertThrows(AppException.class,
                    () -> registry.attendance().record("STU001", "CRS001", 40, 36));
            assertTrue(failure.getMessage().contains("not enrolled"),
                    "the message should explain the rule: " + failure.getMessage());
            assertEquals(0, daos.attendance.rows.size(),
                    "no orphan attendance row should have been written");
        }

        @Test
        @DisplayName("the percentage is calculated, not stored")
        void percentageIsCalculated() throws AppException {
            givenEnrolledStudent();

            registry.attendance().record("STU001", "CRS001", 40, 30);

            Attendance stored =
                    registry.attendance().findByStudentAndCourse("STU001", "CRS001");
            assertEquals(75.0, stored.getAttendancePercentage(), 0.001);
        }

        @Test
        @DisplayName("recording again replaces the record rather than failing")
        void recordIsUpserted() throws AppException {
            givenEnrolledStudent();

            registry.attendance().record("STU001", "CRS001", 40, 20);
            registry.attendance().record("STU001", "CRS001", 40, 30);

            assertEquals(1, registry.attendance().count());
            assertEquals(75.0,
                    registry.attendance().findByStudentAndCourse("STU001", "CRS001")
                            .getAttendancePercentage(), 0.001);
        }

        @Test
        @DisplayName("attending more classes than were held is rejected")
        void overAttendanceIsRejected() throws AppException {
            givenEnrolledStudent();

            assertThrows(AppException.class,
                    () -> registry.attendance().record("STU001", "CRS001", 10, 11));
            assertEquals(0, daos.attendance.rows.size());
        }

        @Test
        @DisplayName("no classes held gives 0% rather than a division by zero")
        void zeroClassesHeldIsSafe() throws AppException {
            givenEnrolledStudent();

            registry.attendance().record("STU001", "CRS001", 0, 0);

            assertEquals(0.0,
                    registry.attendance().findByStudentAndCourse("STU001", "CRS001")
                            .getAttendancePercentage(), 0.001);
        }

        @Test
        @DisplayName("the course summary counts who meets the requirement")
        void courseSummaryCountsCompliantStudents() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenStudent("STU001", "Arjun", "arjun@example.edu", "Computer Science", 3);
            givenStudent("STU002", "Diya", "diya@example.edu", "Computer Science", 3);
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);
            registry.enrollments().enroll("STU001", "CRS001", 3, oneYearAgo());
            registry.enrollments().enroll("STU002", "CRS001", 3, oneYearAgo());
            registry.attendance().record("STU001", "CRS001", 40, 36);  // 90%, compliant
            registry.attendance().record("STU002", "CRS001", 40, 20);  // 50%, not compliant

            AttendanceService.CourseAttendanceSummary summary =
                    registry.attendance().summariseCourse("CRS001");

            assertEquals(2, summary.recorded());
            assertEquals(2, summary.eligible());
            assertEquals(1, summary.meeting());
        }
    }

    // ==================================================================
    // Results
    // ==================================================================

    @Nested
    @DisplayName("Result rules")
    class ResultRules {

        @Test
        @DisplayName("marks out of range are rejected before storage")
        void outOfRangeMarksAreRejected() throws AppException {
            givenEnrolledStudent();

            assertThrows(AppException.class,
                    () -> registry.results().publish("STU001", "CRS001", 45, 50, null));
            assertThrows(AppException.class,
                    () -> registry.results().publish("STU001", "CRS001", 30, 65, null));
            assertThrows(AppException.class,
                    () -> registry.results().publish("STU001", "CRS001", -5, 50, null));
            assertEquals(0, daos.results.rows.size(),
                    "no invalid result should have reached the DAO");
        }

        @Test
        @DisplayName("total, percentage and grade are all derived")
        void resultDerivesEveryFigure() throws AppException {
            givenEnrolledStudent();

            Result stored = registry.results().publish("STU001", "CRS001", 36, 54, "well done");

            assertEquals(90.0, stored.getTotalMarks(), 0.001);
            assertEquals(90.0, stored.getPercentage(), 0.001);
            assertEquals("A+", stored.getGradeCode());
            assertTrue(stored.isPass());
        }

        @Test
        @DisplayName("grading follows the injected policy, not a local table")
        void gradingFollowsTheInjectedPolicy() throws AppException {
            // A deliberately different scale, to prove the service asks the
            // policy rather than consulting a hard-coded band table.
            GradingPolicy lenient = new GradingPolicy() {
                @Override
                public Grade gradeFor(double percentage) {
                    return Grade.fromCode("C");
                }

                @Override
                public double percentageFrom(double marks) {
                    return marks;
                }

                @Override
                public boolean isPass(double percentage) {
                    return true;
                }

                @Override
                public double getPassPercentage() {
                    return 1.0;
                }

                @Override
                public String getDescription() {
                    return "everything passes";
                }
            };
            ResultService lenientService = new ResultService(daos.results, daos.enrollments,
                    daos.students, daos.courses, registry.students(), lenient);
            givenEnrolledStudent();

            Result stored = lenientService.publish("STU001", "CRS001", 1, 1, null);

            assertEquals("C", stored.getGradeCode(),
                    "the injected policy must decide, not the default scale");
        }

        @Test
        @DisplayName("cannot be published for a student who is not enrolled")
        void resultRequiresEnrolment() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenStudent("STU001", "Arjun", "arjun@example.edu", "Computer Science", 3);
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            assertThrows(AppException.class,
                    () -> registry.results().publish("STU001", "CRS001", 30, 45, null));
        }

        @Test
        @DisplayName("the preview shows the grade before it is saved")
        void previewShowsTheGradeWithoutStoring() throws AppException {
            givenEnrolledStudent();

            assertEquals("A+",
                    registry.results().preview("STU001", "CRS001", 36, 54).getGradeCode());
            assertEquals(0, registry.results().count(), "a preview must not write anything");
        }

        @Test
        @DisplayName("the course summary reports the pass rate")
        void courseSummaryReportsPassRate() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenStudent("STU001", "Arjun", "arjun@example.edu", "Computer Science", 3);
            givenStudent("STU002", "Diya", "diya@example.edu", "Computer Science", 3);
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);
            registry.enrollments().enroll("STU001", "CRS001", 3, oneYearAgo());
            registry.enrollments().enroll("STU002", "CRS001", 3, oneYearAgo());
            registry.results().publish("STU001", "CRS001", 36, 54, null);  // 90%, pass
            registry.results().publish("STU002", "CRS001", 10, 15, null);  // 25%, fail

            ResultService.CourseResultSummary summary =
                    registry.results().summariseCourse("CRS001");

            assertEquals(2, summary.students());
            assertEquals(1, summary.passed());
            assertEquals(50.0, summary.passRate(), 0.001);
            assertEquals(90.0, summary.highestPercent(), 0.001);
        }

        @Test
        @DisplayName("an empty course summarises to zero rather than dividing by zero")
        void emptyCourseSummaryIsSafe() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            ResultService.CourseResultSummary summary =
                    registry.results().summariseCourse("CRS001");

            assertEquals(0, summary.students());
            assertEquals(0.0, summary.passRate(), 0.001);
        }
    }

    // ==================================================================
    // Authentication
    // ==================================================================

    @Nested
    @DisplayName("Authentication rules")
    class AuthRules {

        @Test
        @DisplayName("the right password signs in")
        void correctPasswordAuthenticates() throws AppException {
            daos.users.addUser("admin", "admin@123", "Administrator", User.Role.ADMIN);

            User user = registry.authentication().authenticate("admin", "admin@123");

            assertEquals("admin", user.getUsername());
            assertTrue(user.isAdministrator());
        }

        @Test
        @DisplayName("a wrong password is refused")
        void wrongPasswordIsRefused() throws AppException {
            daos.users.addUser("admin", "admin@123", "Administrator", User.Role.ADMIN);

            assertThrows(AppException.class,
                    () -> registry.authentication().authenticate("admin", "wrong"));
        }

        @Test
        @DisplayName("an unknown username and a wrong password are indistinguishable")
        void failureMessagesDoNotLeakWhichHalfWasWrong() throws AppException {
            daos.users.addUser("admin", "admin@123", "Administrator", User.Role.ADMIN);

            String unknownUser = assertThrows(AppException.class,
                    () -> registry.authentication().authenticate("nobody", "admin@123"))
                    .getMessage();
            String wrongPassword = assertThrows(AppException.class,
                    () -> registry.authentication().authenticate("admin", "nope"))
                    .getMessage();

            assertEquals(unknownUser, wrongPassword,
                    "the two failures must read identically to the user");
        }

        @Test
        @DisplayName("a deactivated account cannot sign in")
        void deactivatedAccountIsRefused() throws AppException {
            daos.users.addUser("faculty", "faculty@123", "Faculty User", User.Role.FACULTY);
            daos.users.deactivate("faculty");

            AppException failure = assertThrows(AppException.class,
                    () -> registry.authentication().authenticate("faculty", "faculty@123"));
            assertTrue(failure.getMessage().toLowerCase().contains("deactivated"),
                    "the user should be told the account is inactive: " + failure.getMessage());
        }

        @Test
        @DisplayName("an empty form is rejected before any query")
        void emptyCredentialsAreRejectedImmediately() {
            assertThrows(AppException.class,
                    () -> registry.authentication().authenticate("", ""));
            assertThrows(AppException.class,
                    () -> registry.authentication().authenticate("admin", ""));
        }

        @Test
        @DisplayName("changing a password requires the current one")
        void passwordChangeVerifiesTheCurrentPassword() throws AppException {
            daos.users.addUser("admin", "admin@123", "Administrator", User.Role.ADMIN);
            String originalHash = daos.users.rows.get(0).getPasswordHash();

            assertThrows(AppException.class, () ->
                    registry.authentication().changePassword("admin", "wrong", "newpass1"));

            registry.authentication().changePassword("admin", "admin@123", "newpass1");

            assertEquals("admin",
                    registry.authentication().authenticate("admin", "newpass1").getUsername(),
                    "the new password should work");
            assertThrows(AppException.class, () ->
                            registry.authentication().authenticate("admin", "admin@123"),
                    "the old password should no longer work");
            assertFalse(originalHash.equals(daos.users.rows.get(0).getPasswordHash()),
                    "the stored hash should have been replaced");
        }

        @Test
        @DisplayName("a new password must differ from the old one")
        void unchangedPasswordIsRefused() throws AppException {
            daos.users.addUser("admin", "admin@123", "Administrator", User.Role.ADMIN);

            assertThrows(AppException.class, () ->
                    registry.authentication().changePassword("admin", "admin@123", "admin@123"));
        }

        @Test
        @DisplayName("a password is never stored in plain text")
        void passwordsAreNotStoredInPlainText() throws AppException {
            daos.users.addUser("admin", "admin@123", "Administrator", User.Role.ADMIN);
            User stored = daos.users.findByUsername("admin").orElseThrow();

            assertFalse("admin@123".equals(stored.getPasswordHash()),
                    "the stored value must be a hash, not the password");
            assertTrue(PasswordHasher.matches(stored.getSalt(), "admin@123",
                    stored.getPasswordHash()));
        }
    }

    // ==================================================================
    // Search
    // ==================================================================

    @Nested
    @DisplayName("Search rules")
    class SearchRules {

        @Test
        @DisplayName("a term matches on id or name, not both at once")
        void searchUnionsFields() throws AppException {
            givenStudent("STU001", "Arjun Krishnan", "arjun@example.edu", "Computer Science", 3);
            givenStudent("STU002", "Bhavana Rao", "bhavana@example.edu", "Computer Science", 3);

            List<SearchService.SearchHit> byName =
                    registry.search().search(SearchService.Scope.STUDENTS, "arjun");
            assertEquals(1, byName.size());
            assertEquals("Arjun Krishnan", byName.get(0).name());

            // A term that matches an id but not a name must still be
            // found; combining the fields with AND would have hidden it.
            List<SearchService.SearchHit> byId =
                    registry.search().search(SearchService.Scope.STUDENTS, "STU002");
            assertEquals(1, byId.size());
            assertEquals("Bhavana Rao", byId.get(0).name());
        }

        @Test
        @DisplayName("a term matching both fields returns the record once")
        void aRecordIsNotDuplicatedAcrossFields() throws AppException {
            givenStudent("STU001", "Arjun Krishnan", "arjun@example.edu", "Computer Science", 3);

            assertEquals(1,
                    registry.search().search(SearchService.Scope.STUDENTS, "Arjun").size());
        }

        @Test
        @DisplayName("a blank term lists everything")
        void blankSearchListsEverything() throws AppException {
            givenStudent("STU001", "One", "one@example.edu", "Computer Science", 1);
            givenStudent("STU002", "Two", "two@example.edu", "Computer Science", 1);

            assertEquals(2,
                    registry.search().search(SearchService.Scope.STUDENTS, "").size());
        }

        @Test
        @DisplayName("no match returns an empty list, not an error")
        void noMatchesIsNotAnError() throws AppException {
            givenStudent("STU001", "One", "one@example.edu", "Computer Science", 1);

            assertTrue(registry.search().search(SearchService.Scope.STUDENTS, "zzz").isEmpty());
        }

        @Test
        @DisplayName("each scope returns its own entity type")
        void scopesAreDistinct() throws AppException {
            givenFaculty("FAC001", "Meera Iyer", "meera@example.edu", "Professor");
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            assertEquals("Faculty",
                    registry.search().search(SearchService.Scope.FACULTY, "Meera")
                            .get(0).entity());
            assertEquals("Course",
                    registry.search().search(SearchService.Scope.COURSES, "CS101")
                            .get(0).entity());
        }

        @Test
        @DisplayName("a course is findable by its public code")
        void courseSearchMatchesTheCode() throws AppException {
            givenFaculty("FAC001", "Meera", "meera@example.edu", "Professor");
            givenCourse("CRS001", "CS101", "Programming", "FAC001", 1);

            List<SearchService.SearchHit> hits =
                    registry.search().search(SearchService.Scope.COURSES, "CS101");

            assertEquals(1, hits.size());
            assertEquals("Programming", hits.get(0).name());
        }
    }

    // ==================================================================
    // Dashboard
    // ==================================================================

    @Nested
    @DisplayName("Dashboard")
    class DashboardRules {

        @Test
        @DisplayName("the counts match what was actually stored")
        void dashboardCountsAreReal() throws AppException {
            givenEnrolledStudent();
            givenStudent("STU002", "Diya", "diya@example.edu", "Computer Science", 3);
            registry.attendance().record("STU001", "CRS001", 40, 36);
            registry.results().publish("STU001", "CRS001", 36, 54, null);

            ServiceRegistry.Dashboard dashboard = registry.loadDashboard();

            assertEquals(2, dashboard.students());
            assertEquals(1, dashboard.faculty());
            assertEquals(1, dashboard.courses());
            assertEquals(1, dashboard.enrolments());
            assertEquals(1, dashboard.attendanceRecords());
            assertEquals(1, dashboard.resultRecords());
        }

        @Test
        @DisplayName("an empty system reports zeroes, not nulls")
        void emptySystemIsHandled() throws AppException {
            ServiceRegistry.Dashboard dashboard = registry.loadDashboard();

            assertEquals(0, dashboard.students());
            assertEquals(0, dashboard.courses());
            assertEquals(0.0, dashboard.averageResult(), 0.001);
        }
    }

    // ==================================================================
    // In-memory DAO doubles
    // ==================================================================

    /** The set of fakes, wired to each other so cross-DAO rules work. */
    private final class FakeDaos {
        final FakeStudentDao students = new FakeStudentDao();
        final FakeFacultyDao faculty = new FakeFacultyDao();
        final FakeCourseDao courses = new FakeCourseDao();
        final FakeEnrollmentDao enrollments = new FakeEnrollmentDao();
        final FakeAttendanceDao attendance = new FakeAttendanceDao();
        final FakeResultDao results = new FakeResultDao();
        final FakeProfileDao profiles = new FakeProfileDao();
        final FakeUserDao users = new FakeUserDao();
    }

    private final class FakeStudentDao implements StudentDao {
        final List<Student> rows = new ArrayList<>();

        @Override public void insert(Student student) { rows.add(student); }

        @Override public boolean update(Student student) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getId().equals(student.getId())) {
                    rows.set(i, student);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean delete(String studentId) {
            return rows.removeIf(s -> s.getId().equals(studentId));
        }

        @Override public Optional<Student> findById(String studentId) {
            return rows.stream().filter(s -> s.getId().equals(studentId)).findFirst();
        }

        @Override public Optional<Student> findByEmail(String email) {
            return rows.stream().filter(s -> email.equals(s.getEmail())).findFirst();
        }

        @Override public List<Student> findAll() { return new ArrayList<>(rows); }

        @Override public List<Student> search(StudentSearchCriteria query) {
            if (query == null || query.isEmpty()) {
                return new ArrayList<>(rows);
            }
            List<Student> matches = new ArrayList<>();
            for (Student student : rows) {
                if (query.getStudentId() != null
                        && !containsIgnoreCase(student.getId(), query.getStudentId())) {
                    continue;
                }
                if (query.getName() != null
                        && !containsIgnoreCase(student.getName(), query.getName())) {
                    continue;
                }
                if (query.getDepartment() != null
                        && !containsIgnoreCase(student.getDepartment(), query.getDepartment())) {
                    continue;
                }
                if (query.getSemester() != null
                        && student.getSemester() != query.getSemester()) {
                    continue;
                }
                matches.add(student);
            }
            return matches;
        }

        @Override public long count() { return rows.size(); }

        @Override public int findHighestNumericSuffix() {
            return highestNumericSuffixOf(rows.stream().map(Student::getId).toList());
        }

        @Override public List<String> findDistinctDepartments() {
            return rows.stream().map(Student::getDepartment).distinct().sorted().toList();
        }

        @Override public List<IdName> findAllSummaries() {
            return rows.stream()
                    .map(s -> new IdName(s.getId(), s.getName()))
                    .toList();
        }

        @Override public boolean existsById(String studentId) {
            return rows.stream().anyMatch(s -> s.getId().equals(studentId));
        }

        @Override public boolean existsByEmail(String email, String excludingStudentId) {
            return rows.stream().anyMatch(s -> email.equals(s.getEmail())
                    && !s.getId().equals(excludingStudentId));
        }
    }

    private final class FakeFacultyDao implements FacultyDao {
        final List<Faculty> rows = new ArrayList<>();

        @Override public void insert(Faculty faculty) { rows.add(faculty); }

        @Override public boolean update(Faculty faculty) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getId().equals(faculty.getId())) {
                    rows.set(i, faculty);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean delete(String facultyId) {
            return rows.removeIf(f -> f.getId().equals(facultyId));
        }

        @Override public Optional<Faculty> findById(String facultyId) {
            return rows.stream().filter(f -> f.getId().equals(facultyId)).findFirst();
        }

        @Override public Optional<Faculty> findByEmail(String email) {
            return rows.stream().filter(f -> email.equals(f.getEmail())).findFirst();
        }

        @Override public List<Faculty> findAll() { return new ArrayList<>(rows); }

        @Override public List<Faculty> search(FacultySearchCriteria query) {
            if (query == null || query.isEmpty()) {
                return new ArrayList<>(rows);
            }
            List<Faculty> matches = new ArrayList<>();
            for (Faculty faculty : rows) {
                if (query.getFacultyId() != null
                        && !containsIgnoreCase(faculty.getId(), query.getFacultyId())) {
                    continue;
                }
                if (query.getName() != null
                        && !containsIgnoreCase(faculty.getName(), query.getName())) {
                    continue;
                }
                if (query.getDepartment() != null
                        && !containsIgnoreCase(faculty.getDepartment(), query.getDepartment())) {
                    continue;
                }
                if (query.getDesignation() != null
                        && !containsIgnoreCase(faculty.getDesignation(), query.getDesignation())) {
                    continue;
                }
                matches.add(faculty);
            }
            return matches;
        }

        @Override public long count() { return rows.size(); }

        @Override public int countCoursesTaught(String facultyId) {
            return (int) daos.courses.rows.stream()
                    .filter(c -> facultyId.equals(c.getFacultyId())).count();
        }

        @Override public int findHighestNumericSuffix() {
            return highestNumericSuffixOf(rows.stream().map(Faculty::getId).toList());
        }

        @Override public List<String> findDistinctDepartments() {
            return rows.stream().map(Faculty::getDepartment).distinct().sorted().toList();
        }

        @Override public List<String> findDistinctDesignations() {
            return rows.stream().map(Faculty::getDesignation).distinct().sorted().toList();
        }

        @Override public List<IdName> findAllSummaries() {
            return rows.stream()
                    .map(f -> new IdName(f.getId(), f.getName() + " (" + f.getDesignation() + ")"))
                    .toList();
        }

        @Override public boolean existsById(String facultyId) {
            return rows.stream().anyMatch(f -> f.getId().equals(facultyId));
        }

        @Override public boolean existsByEmail(String email, String excludingFacultyId) {
            return rows.stream().anyMatch(f -> email.equals(f.getEmail())
                    && !f.getId().equals(excludingFacultyId));
        }
    }

    private final class FakeCourseDao implements CourseDao {
        final List<Course> rows = new ArrayList<>();

        @Override public void insert(Course course) { rows.add(course); }

        @Override public boolean update(Course course) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getCourseId().equals(course.getCourseId())) {
                    rows.set(i, course);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean delete(String courseId) {
            return rows.removeIf(c -> c.getCourseId().equals(courseId));
        }

        @Override public Optional<Course> findById(String courseId) {
            return rows.stream().filter(c -> c.getCourseId().equals(courseId)).findFirst();
        }

        @Override public Optional<Course> findByCode(String courseCode) {
            return rows.stream().filter(c -> courseCode.equals(c.getCourseCode())).findFirst();
        }

        @Override public List<Course> findAll() { return new ArrayList<>(rows); }

        @Override public List<Course> search(CourseSearchCriteria query) {
            if (query == null || query.isEmpty()) {
                return new ArrayList<>(rows);
            }
            List<Course> matches = new ArrayList<>();
            for (Course course : rows) {
                if (query.getCourseId() != null
                        && !containsIgnoreCase(course.getCourseId(), query.getCourseId())) {
                    continue;
                }
                if (query.getCourseCode() != null
                        && !containsIgnoreCase(course.getCourseCode(), query.getCourseCode())) {
                    continue;
                }
                if (query.getCourseName() != null
                        && !containsIgnoreCase(course.getCourseName(), query.getCourseName())) {
                    continue;
                }
                if (query.getDepartment() != null
                        && !containsIgnoreCase(course.getDepartment(), query.getDepartment())) {
                    continue;
                }
                if (query.getFacultyId() != null
                        && !query.getFacultyId().equals(course.getFacultyId())) {
                    continue;
                }
                if (query.getSemester() != null
                        && course.getSemester() != query.getSemester()) {
                    continue;
                }
                matches.add(course);
            }
            return matches;
        }

        @Override public long count() { return rows.size(); }

        @Override public int findHighestNumericSuffix() {
            return highestNumericSuffixOf(rows.stream().map(Course::getCourseId).toList());
        }

        @Override public List<String> findDistinctDepartments() {
            return rows.stream().map(Course::getDepartment).distinct().sorted().toList();
        }

        @Override public List<IdName> findAllSummaries() {
            return rows.stream()
                    .map(c -> new IdName(c.getCourseId(), c.getDisplayName()))
                    .toList();
        }

        @Override public boolean existsById(String courseId) {
            return rows.stream().anyMatch(c -> c.getCourseId().equals(courseId));
        }

        @Override public boolean existsByCode(String courseCode, String excludingCourseId) {
            return rows.stream().anyMatch(c -> courseCode.equals(c.getCourseCode())
                    && !c.getCourseId().equals(excludingCourseId));
        }

        @Override public List<Course> findByStudent(String studentId) {
            List<Course> courses = new ArrayList<>();
            for (Enrollment enrollment : daos.enrollments.rows) {
                if (enrollment.getStudentId().equals(studentId)) {
                    findById(enrollment.getCourseId()).ifPresent(courses::add);
                }
            }
            return courses;
        }

        @Override public List<Course> findByFaculty(String facultyId) {
            return rows.stream().filter(c -> facultyId.equals(c.getFacultyId())).toList();
        }
    }

    private final class FakeEnrollmentDao implements EnrollmentDao {
        final List<Enrollment> rows = new ArrayList<>();
        private long nextId;

        @Override public void insert(Enrollment enrollment) {
            store(enrollment);
        }

        @Override public Enrollment insertAndReturn(Enrollment enrollment) {
            return store(enrollment);
        }

        /** Mimics the generated key the real table supplies. */
        private Enrollment store(Enrollment enrollment) {
            enrollment.setEnrollmentId(++nextId);
            rows.add(enrollment);
            return enrollment;
        }

        @Override public boolean update(Enrollment enrollment) {
            return delete(enrollment.getStudentId(), enrollment.getCourseId());
        }

        @Override public boolean delete(String studentId, String courseId) {
            return rows.removeIf(e -> e.getStudentId().equals(studentId)
                    && e.getCourseId().equals(courseId));
        }

        @Override public Optional<Enrollment> findById(long enrollmentId) {
            return rows.stream().filter(e -> e.getEnrollmentId() == enrollmentId).findFirst();
        }

        @Override public Optional<Enrollment> findByStudentAndCourse(String studentId,
                                                                     String courseId) {
            return rows.stream()
                    .filter(e -> e.getStudentId().equals(studentId)
                            && e.getCourseId().equals(courseId))
                    .findFirst();
        }

        @Override public boolean isEnrolled(String studentId, String courseId) {
            return findByStudentAndCourse(studentId, courseId).isPresent();
        }

        @Override public List<Enrollment> findAll() { return new ArrayList<>(rows); }

        @Override public List<Enrollment> findByStudent(String studentId) {
            return rows.stream().filter(e -> e.getStudentId().equals(studentId)).toList();
        }

        @Override public List<Enrollment> findByCourse(String courseId) {
            return rows.stream().filter(e -> e.getCourseId().equals(courseId)).toList();
        }

        @Override public List<Enrollment> findByStudentAndSemester(String studentId, int semester) {
            return rows.stream()
                    .filter(e -> e.getStudentId().equals(studentId) && e.getSemester() == semester)
                    .toList();
        }

        @Override public long count() { return rows.size(); }

        @Override public int countByCourse(String courseId) {
            return (int) rows.stream().filter(e -> e.getCourseId().equals(courseId)).count();
        }

        @Override public List<Enrollment> findEnrolledSince(LocalDate date) {
            return rows.stream()
                    .filter(e -> !e.getEnrollmentDate().isBefore(date))
                    .toList();
        }
    }

    private final class FakeAttendanceDao implements AttendanceDao {
        final List<Attendance> rows = new ArrayList<>();
        private long nextId;

        @Override public int upsert(Attendance attendance) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getStudentId().equals(attendance.getStudentId())
                        && rows.get(i).getCourseId().equals(attendance.getCourseId())) {
                    rows.set(i, attendance);
                    return 1;
                }
            }
            attendance.setAttendanceId(++nextId);
            rows.add(attendance);
            return 1;
        }

        @Override public int update(Attendance attendance) { return upsert(attendance); }

        @Override public boolean delete(String studentId, String courseId) {
            return rows.removeIf(a -> a.getStudentId().equals(studentId)
                    && a.getCourseId().equals(courseId));
        }

        @Override public Optional<Attendance> findById(long attendanceId) {
            return rows.stream().filter(a -> a.getAttendanceId() == attendanceId).findFirst();
        }

        @Override public Optional<Attendance> findByStudentAndCourse(String studentId,
                                                                    String courseId) {
            return rows.stream()
                    .filter(a -> a.getStudentId().equals(studentId)
                            && a.getCourseId().equals(courseId))
                    .findFirst();
        }

        @Override public List<Attendance> findAll() { return new ArrayList<>(rows); }

        @Override public List<Attendance> findByStudent(String studentId) {
            return rows.stream().filter(a -> a.getStudentId().equals(studentId)).toList();
        }

        @Override public List<Attendance> findByCourse(String courseId) {
            return rows.stream().filter(a -> a.getCourseId().equals(courseId)).toList();
        }

        @Override public long count() { return rows.size(); }

        @Override public List<String> findDistinctStudentIds() {
            return rows.stream().map(Attendance::getStudentId).distinct().sorted().toList();
        }
    }

    private final class FakeResultDao implements ResultDao {
        final List<Result> rows = new ArrayList<>();
        private long nextId;

        @Override public int upsert(Result result) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getStudentId().equals(result.getStudentId())
                        && rows.get(i).getCourseId().equals(result.getCourseId())) {
                    rows.set(i, result);
                    return 1;
                }
            }
            result.setResultId(++nextId);
            rows.add(result);
            return 1;
        }

        @Override public int insert(Result result) { return upsert(result); }

        @Override public int update(Result result) { return upsert(result); }

        @Override public boolean delete(String studentId, String courseId) {
            return rows.removeIf(r -> r.getStudentId().equals(studentId)
                    && r.getCourseId().equals(courseId));
        }

        @Override public Optional<Result> findById(long resultId) {
            return rows.stream().filter(r -> r.getResultId() == resultId).findFirst();
        }

        @Override public Optional<Result> findByStudentAndCourse(String studentId, String courseId) {
            return rows.stream()
                    .filter(r -> r.getStudentId().equals(studentId)
                            && r.getCourseId().equals(courseId))
                    .findFirst();
        }

        @Override public List<Result> findAll() { return new ArrayList<>(rows); }

        @Override public List<Result> findByStudent(String studentId) {
            return rows.stream().filter(r -> r.getStudentId().equals(studentId)).toList();
        }

        @Override public List<Result> findByCourse(String courseId) {
            return rows.stream().filter(r -> r.getCourseId().equals(courseId)).toList();
        }

        @Override public List<Result> findByGrade(Grade grade) {
            return rows.stream().filter(r -> r.getGrade() == grade).toList();
        }

        @Override public long count() { return rows.size(); }

        @Override public int countPassedInCourse(String courseId) {
            return (int) rows.stream()
                    .filter(r -> r.getCourseId().equals(courseId) && r.isPass()).count();
        }

        @Override public int countResultsInCourse(String courseId) {
            return (int) rows.stream().filter(r -> r.getCourseId().equals(courseId)).count();
        }
    }

    private final class FakeProfileDao implements AcademicProfileDao {

        @Override public AcademicProfile buildProfile(String studentId) throws AppException {
            Student student = daos.students.findById(studentId)
                    .orElseThrow(() -> new com.academic.management.exception
                            .RecordNotFoundException("Academic profile", studentId));
            return new AcademicProfile(student, List.of());
        }

        @Override public List<CoursePerformance> findCoursePerformances(String studentId) {
            return List.of();
        }

        @Override public Optional<Student> findStudent(String studentId) {
            return daos.students.findById(studentId);
        }

        @Override public DashboardSummary loadDashboardSummary() {
            return new DashboardSummary(0, 0, 0, 0, 0, 0, 0.0, 0.0);
        }
    }

    private final class FakeUserDao implements UserDao {
        final List<User> rows = new ArrayList<>();

        void addUser(String username, String password, String fullName, User.Role role) {
            String salt = PasswordHasher.newSalt();
            rows.add(new User("USR" + username.toUpperCase(), username, fullName, role, true,
                    PasswordHasher.hash(salt, password), salt));
        }

        void deactivate(String username) {
            for (int i = 0; i < rows.size(); i++) {
                User current = rows.get(i);
                if (current.getUsername().equals(username)) {
                    rows.set(i, new User(current.getUserId(), current.getUsername(),
                            current.getFullName(), current.getRole(), false,
                            current.getPasswordHash(), current.getSalt()));
                }
            }
        }

        @Override public Optional<User> findByUsername(String username) {
            return rows.stream().filter(u -> u.getUsername().equals(username)).findFirst();
        }

        @Override public void insert(User user) { rows.add(user); }

        @Override public boolean updatePassword(String userId, String salt, String passwordHash) {
            for (int i = 0; i < rows.size(); i++) {
                User current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, new User(current.getUserId(), current.getUsername(),
                            current.getFullName(), current.getRole(), current.isActive(),
                            passwordHash, salt));
                    return true;
                }
            }
            return false;
        }

        @Override public long count() { return rows.size(); }
    }

    // ------------------------------------------------------------------
    // Shared fake helpers
    // ------------------------------------------------------------------

    private static boolean containsIgnoreCase(String value, String term) {
        return value != null && value.toLowerCase().contains(term.trim().toLowerCase());
    }

    private static int highestNumericSuffixOf(List<String> ids) {
        int highest = 0;
        for (String id : ids) {
            String digits = id.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                // A non-numeric id simply does not contribute a suffix.
                continue;
            }
            highest = Math.max(highest, Integer.parseInt(digits));
        }
        return highest;
    }
}
