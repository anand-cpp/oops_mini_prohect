package com.academic.management.model;

import com.academic.management.exception.ValidationException;
import com.academic.management.util.GradingPolicy;
import com.academic.management.util.PasswordHasher;
import com.academic.management.util.StandardGradingPolicy;
import com.academic.management.util.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure domain tests. Nothing here touches a database, so a failure means
 * the rule itself is wrong rather than the plumbing.
 */
@DisplayName("Domain rules")
class DomainRulesTest {

    private static final GradingPolicy POLICY = StandardGradingPolicy.getInstance();

    // ------------------------------------------------------------------
    // Grading
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("grading scale")
    class GradingScale {

        @ParameterizedTest(name = "{0}% is a {1}")
        @CsvSource({
                "90,  A+", "89.99, A", "80,  A",  "79.99, B+", "70,  B+",
                "69.99, B",  "60,  B",  "59.99, C", "50,  C",  "49.99, D",
                "40,  D",   "39.99, F", "0,  F",   "100, A+"
        })
        @DisplayName("each band boundary maps to the right grade")
        void bandsAreInclusiveAtTheBottom(double percentage, String expectedCode) {
            assertEquals(expectedCode, POLICY.gradeFor(percentage).getCode(),
                    percentage + "% should be a " + expectedCode);
        }

        @Test
        @DisplayName("the pass mark is 40%")
        void passBoundary() {
            assertFalse(POLICY.isPass(39.99));
            assertTrue(POLICY.isPass(40.0));
            assertEquals(40.0, POLICY.getPassPercentage());
        }

        @Test
        @DisplayName("marks are scaled out of 100")
        void marksBecomePercentage() {
            assertEquals(90.0, POLICY.percentageFrom(90), 0.001);
            assertEquals(0.0, POLICY.percentageFrom(0), 0.001);
            assertEquals(50.0, POLICY.percentageFrom(50), 0.001);
        }

        @Test
        @DisplayName("a percentage outside 0..100 is rejected rather than clamped")
        void outOfRangePercentageIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> POLICY.gradeFor(101));
            assertThrows(IllegalArgumentException.class, () -> POLICY.gradeFor(-1));
        }

        @Test
        @DisplayName("grade points are ordered consistently with the bands")
        void gradePointsFollowTheBands() {
            double previous = Double.MAX_VALUE;
            for (Grade grade : Grade.orderedByThreshold()) {
                assertTrue(grade.getGradePoint() < previous,
                        grade + " must be worth less than the band above it");
                previous = grade.getGradePoint();
            }
        }

        @Test
        @DisplayName("grade codes round-trip")
        void gradeCodesRoundTrip() {
            for (Grade grade : Grade.values()) {
                assertEquals(grade, Grade.fromCode(grade.getCode()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Result
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("result")
    class ResultRules {

        @Test
        @DisplayName("total, percentage and grade all come from the marks")
        void derivesEverythingFromMarks() throws ValidationException {
            Result result = new Result("STU001", "CRS001", 36, 54,
                    LocalDate.of(2025, 12, 5), null, POLICY);

            assertEquals(90.0, result.getTotalMarks(), 0.001);
            assertEquals(90.0, result.getPercentage(), 0.001);
            assertEquals("A+", result.getGradeCode());
            assertEquals(5.0, result.getGradePoint(), 0.001);
            assertTrue(result.isPass());
        }

        @Test
        @DisplayName("marks beyond the internal maximum are rejected")
        void internalMarksAreCappedAt40() {
            assertThrows(ValidationException.class, () -> new Result("STU001", "CRS001",
                    41, 10, LocalDate.of(2025, 12, 5), null, POLICY));
        }

        @Test
        @DisplayName("marks beyond the external maximum are rejected")
        void externalMarksAreCappedAt60() {
            assertThrows(ValidationException.class, () -> new Result("STU001", "CRS001",
                    10, 61, LocalDate.of(2025, 12, 5), null, POLICY));
        }

        @Test
        @DisplayName("negative marks are rejected")
        void negativeMarksAreRejected() {
            assertThrows(ValidationException.class, () -> new Result("STU001", "CRS001",
                    -1, 10, LocalDate.of(2025, 12, 5), null, POLICY));
        }

        @Test
        @DisplayName("a future result date is rejected")
        void futureDateIsRejected() {
            assertThrows(ValidationException.class, () -> new Result("STU001", "CRS001",
                    10, 10, LocalDate.now().plusDays(1), null, POLICY));
        }

        @Test
        @DisplayName("a rejected mark update leaves the old marks intact")
        void failedUpdateDoesNotCorruptState() throws ValidationException {
            Result result = new Result("STU001", "CRS001", 20, 30,
                    LocalDate.of(2025, 12, 5), null, POLICY);

            assertThrows(ValidationException.class, () -> result.setInternalMarks(99));
            assertEquals(20.0, result.getInternalMarks(), 0.001,
                    "a refused update must not half-apply");
            assertEquals(30.0, result.getExternalMarks(), 0.001);
        }

        @Test
        @DisplayName("a blank student id is rejected")
        void identifiersAreRequired() {
            assertThrows(ValidationException.class, () -> new Result("  ", "CRS001",
                    10, 10, LocalDate.of(2025, 12, 5), null, POLICY));
        }
    }

    // ------------------------------------------------------------------
    // Attendance
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("attendance")
    class AttendanceRules {

        @Test
        @DisplayName("percentage is attended over held")
        void computesPercentage() throws ValidationException {
            assertEquals(75.0, new Attendance("STU001", "CRS001", 40, 30,
                    LocalDate.of(2025, 9, 1)).getAttendancePercentage(), 0.001);
        }

        @Test
        @DisplayName("no classes held yields zero, not a division by zero")
        void zeroClassesHeldIsSafe() throws ValidationException {
            assertEquals(0.0, new Attendance("STU001", "CRS001", 0, 0,
                    LocalDate.of(2025, 9, 1)).getAttendancePercentage(), 0.001);
        }

        @Test
        @DisplayName("attending more classes than were held is rejected")
        void overAttendanceIsRejected() {
            assertThrows(ValidationException.class, () -> new Attendance("STU001", "CRS001",
                    10, 11, LocalDate.of(2025, 9, 1)));
        }

        @Test
        @DisplayName("negative counts are rejected")
        void negativeCountsAreRejected() {
            assertThrows(ValidationException.class, () -> new Attendance("STU001", "CRS001",
                    -1, 0, LocalDate.of(2025, 9, 1)));
        }
    }

    // ------------------------------------------------------------------
    // Person hierarchy
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("person hierarchy")
    class PersonRules {

        private Student student(String id, String name, int semester) throws ValidationException {
            return new Student(id, name, LocalDate.of(2004, 5, 15), Gender.MALE,
                    id.toLowerCase() + "@example.edu", "9876543210", "Test Street",
                    "Computer Science", semester, LocalDate.of(2023, 8, 1));
        }

        @Test
        @DisplayName("Student and Faculty share the Person base through inheritance")
        void studentsAndFacultyArePersons() throws ValidationException {
            Person asStudent = student("STU001", "Arjun Krishnan", 3);
            Faculty asFaculty = new Faculty("FAC001", "Meera Iyer", LocalDate.of(1985, 2, 10),
                    Gender.FEMALE, "meera@example.edu", "9876500001", "Main Street",
                    "Computer Science", "Professor", "Block A", LocalDate.of(2015, 7, 1));

            assertTrue(asStudent instanceof Person);
            assertTrue(asFaculty instanceof Person);
            assertEquals("STU001", asStudent.getId());
            assertEquals("FAC001", asFaculty.getId());
        }

        @Test
        @DisplayName("a future date of birth is rejected")
        void futureDateOfBirthIsRejected() {
            assertThrows(ValidationException.class, () -> new Student("STU001", "Future Child",
                    LocalDate.now().plusDays(1), Gender.MALE, "future@example.edu", null, null,
                    "Computer Science", 1, LocalDate.of(2023, 8, 1)));
        }

        @Test
        @DisplayName("today counts as the future, not as a valid birthday")
        void todayIsNotABirthday() {
            assertThrows(ValidationException.class, () -> new Student("STU001", "Born Today",
                    LocalDate.now(), Gender.MALE, "today@example.edu", null, null,
                    "Computer Science", 1, LocalDate.of(2023, 8, 1)));
        }

        @Test
        @DisplayName("age is derived from the date of birth, not stored")
        void ageIsDerived() throws ValidationException {
            LocalDate sixteenYearsAgo = LocalDate.now().minusYears(16).minusDays(1);
            Student teenager = new Student("STU009", "Teenager", sixteenYearsAgo, Gender.MALE,
                    "teen@example.edu", null, null, "Computer Science", 2,
                    LocalDate.of(2023, 8, 1));
            assertEquals(16, teenager.getAgeToday(),
                    "age must be computed from the date of birth, not stored");
        }

        @Test
        @DisplayName("a semester outside 1..10 is rejected")
        void semesterRangeIsEnforced() {
            assertThrows(ValidationException.class, () -> student("STU001", "Bad Semester", 0));
            assertThrows(ValidationException.class, () -> student("STU001", "Bad Semester", 11));
        }

        @Test
        @DisplayName("a malformed email is rejected")
        void emailFormatIsEnforced() {
            assertThrows(ValidationException.class, () -> new Student("STU001", "Bad Email",
                    LocalDate.of(2004, 5, 15), Gender.MALE, "not-an-email", null, null,
                    "Computer Science", 1, LocalDate.of(2023, 8, 1)));
        }

        @Test
        @DisplayName("equality is by id and ignores surrounding case")
        void equalityIsById() throws ValidationException {
            assertEquals(student("STU001", "Arjun Krishnan", 3),
                    student("stu001", "Arjun Krishnan", 3));
            assertNotEquals(student("STU001", "Arjun Krishnan", 3),
                    student("STU002", "Arjun Krishnan", 3));
        }
    }

    // ------------------------------------------------------------------
    // Profile
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("academic profile")
    class ProfileRules {

        private Student student() throws ValidationException {
            return new Student("STU001", "Arjun Krishnan", LocalDate.of(2004, 5, 15),
                    Gender.MALE, "arjun@example.edu", "9876543210", "Test Street",
                    "Computer Science", 3, LocalDate.of(2023, 8, 1));
        }

        private CoursePerformance row(String code, double credits, Double percentage,
                                      Double gradePoint, Boolean passed, int held, int attended)
                throws ValidationException {
            String grade = percentage == null ? null : POLICY.gradeFor(percentage).getCode();
            return new CoursePerformance(code, code, "Course " + code, credits, 1,
                    "Dr Rao", "ACTIVE", held, attended, percentage, grade, gradePoint, passed);
        }

        @Test
        @DisplayName("a student with no results reports zero rather than failing")
        void noResultsYieldsZeroes() throws ValidationException {
            AcademicProfile profile = new AcademicProfile(student(), java.util.List.of(
                    row("CRS001", 4, null, null, null, 40, 30),
                    row("CRS002", 3, null, null, null, 0, 0)));

            assertFalse(profile.hasAnyResult());
            assertEquals(0.0, profile.getOverallPercentage(), 0.001);
            assertEquals(0.0, profile.getGpa(), 0.001);
            // Attendance is independent of results: 30 of 40 held is 75%,
            // and the ungraded course contributes no classes either way.
            assertEquals(75.0, profile.getOverallAttendancePercentage(), 0.001);
            assertEquals("No results published yet.", profile.getStandingSummary());
        }

        @Test
        @DisplayName("GPA is weighted by credits, not a plain average")
        void gpaIsCreditWeighted() throws ValidationException {
            AcademicProfile profile = new AcademicProfile(student(), java.util.List.of(
                    row("CRS001", 4, 90.0, 5.0, true, 40, 36),
                    row("CRS002", 1, 50.0, 2.0, true, 10, 5)));

            // A plain average would be (5.00 + 2.00) / 2 = 3.50.
            // Credits-weighted: (5*4 + 2*1) / 5 = 22/5 = 4.40.
            assertEquals(3.50, (5.0 + 2.0) / 2, 0.001, "the plain average, for contrast");
            assertEquals(4.40, profile.getGpa(), 0.01,
                    "the 4-credit course must count for more than the 1-credit one");
        }

        @Test
        @DisplayName("overall attendance pools held and attended, not per-course averages")
        void attendancePoolsRatherThanAverages() throws ValidationException {
            AcademicProfile profile = new AcademicProfile(student(), java.util.List.of(
                    row("CRS001", 4, 90.0, 5.0, true, 10, 10),
                    row("CRS002", 3, 90.0, 5.0, true, 30, 0)));

            // A per-course average would be (100 + 0) / 2 = 50%.
            // Pooled: 10 attended out of 40 held = 25%.
            assertEquals(25.0, profile.getOverallAttendancePercentage(), 0.01,
                    "a 30-class course must not be diluted by a 10-class one");
        }

        @Test
        @DisplayName("courses with no classes held are excluded from the standing verdict")
        void zeroHeldCoursesDoNotSinkAStudent() throws ValidationException {
            AcademicProfile profile = new AcademicProfile(student(), java.util.List.of(
                    row("CRS001", 4, 90.0, 5.0, true, 40, 38),
                    row("CRS002", 3, null, null, null, 0, 0)));

            assertEquals(95.0, profile.getOverallAttendancePercentage(), 0.01);
            assertTrue(profile.isInGoodStanding(),
                    "an ungraded course with no classes yet must not count as absence");
        }

        @Test
        @DisplayName("a failed course is reported even when the average looks healthy")
        void failedCoursesAreCalledOut() throws ValidationException {
            AcademicProfile profile = new AcademicProfile(student(), java.util.List.of(
                    row("CRS001", 4, 90.0, 5.0, true, 40, 36),
                    row("CRS002", 3, 25.0, 0.0, false, 40, 30)));

            // The average is 57.5% and attendance 82.5%, both of which clear
            // their thresholds - which is exactly why testing the average
            // alone would wrongly pass this student.
            assertEquals(57.5, profile.getOverallPercentage(), 0.01);
            assertEquals(82.5, profile.getOverallAttendancePercentage(), 0.01);
            assertEquals(1, profile.getFailedCourseCount());
            assertFalse(profile.isInGoodStanding(),
                    "a strong average must not mask a failed course");
            assertEquals("1 course(s) need re-examination.", profile.getStandingSummary());
        }
    }

    // ------------------------------------------------------------------
    // Passwords and validation helpers
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("password hashing")
    class Passwords {

        @Test
        @DisplayName("the same password under the same salt always matches")
        void hashingIsDeterministic() {
            String salt = PasswordHasher.newSalt();
            String hash = PasswordHasher.hash(salt, "admin@123");
            assertTrue(PasswordHasher.matches(salt, "admin@123", hash));
        }

        @Test
        @DisplayName("a different password does not match")
        void wrongPasswordIsRejected() {
            String salt = PasswordHasher.newSalt();
            String hash = PasswordHasher.hash(salt, "admin@123");
            assertFalse(PasswordHasher.matches(salt, "admin@1234", hash));
            assertFalse(PasswordHasher.matches(salt, "", hash));
        }

        @Test
        @DisplayName("the same password under a different salt does not match")
        void saltIsApplied() {
            String hash = PasswordHasher.hash(PasswordHasher.newSalt(), "admin@123");
            assertFalse(PasswordHasher.matches(PasswordHasher.newSalt(), "admin@123", hash),
                    "a fresh salt must produce a different digest");
        }

        @Test
        @DisplayName("salts are unique")
        void saltsAreUnique() {
            assertNotEquals(PasswordHasher.newSalt(), PasswordHasher.newSalt());
        }
    }

    @Nested
    @DisplayName("validation helpers")
    class ValidationHelpers {

        @Test
        @DisplayName("required text is trimmed and blanks rejected")
        void requiredText() throws ValidationException {
            assertEquals("Arjun", Validator.requireText("Name", "  Arjun  "));
            assertThrows(ValidationException.class, () -> Validator.requireText("Name", "   "));
            assertThrows(ValidationException.class, () -> Validator.requireText("Name", null));
        }

        @Test
        @DisplayName("optional text treats blank as absent")
        void optionalText() throws ValidationException {
            assertNull(Validator.optionalText("Notes", "   ", 20),
                    "a blank optional field normalises to null, not an empty string");
            assertNull(Validator.optionalText("Notes", null, 20));
            assertEquals("hi", Validator.optionalText("Notes", "  hi  ", 20));
        }

        @Test
        @DisplayName("emails are checked for shape")
        void emailShape() throws ValidationException {
            assertNotNull(Validator.requireEmail("Email", "a.b@example.edu"));
            assertThrows(ValidationException.class,
                    () -> Validator.requireEmail("Email", "missing-at-sign"));
            assertThrows(ValidationException.class,
                    () -> Validator.requireEmail("Email", "a@b"));
        }

        @Test
        @DisplayName("numbers are formatted without trailing zeros")
        void numberFormatting() {
            assertEquals("90", Validator.trimNumber(90.0));
            assertEquals("36.5", Validator.trimNumber(36.5));
            assertEquals("0", Validator.trimNumber(0.0));
        }

        @Test
        @DisplayName("semesters are bounded to 1..10")
        void semesterRange() throws ValidationException {
            assertEquals(1, Validator.requireSemester("Semester", "1"));
            assertThrows(ValidationException.class, () -> Validator.requireSemester("Semester", "0"));
            assertThrows(ValidationException.class, () -> Validator.requireSemester("Semester", "11"));
            assertThrows(ValidationException.class, () -> Validator.requireSemester("Semester", "two"));
        }
    }
}
