package com.academic.management.service;

import com.academic.management.dao.AttendanceDao;
import com.academic.management.dao.CourseDao;
import com.academic.management.dao.EnrollmentDao;
import com.academic.management.dao.StudentDao;
import com.academic.management.exception.AppException;
import com.academic.management.exception.BusinessRuleException;
import com.academic.management.exception.RecordNotFoundException;
import com.academic.management.model.Attendance;
import com.academic.management.model.Course;
import com.academic.management.model.Student;
import com.academic.management.util.Constants;

import java.util.List;

/**
 * Business rules for attendance.
 *
 * <h2>The one rule that matters here</h2>
 * Attendance may only be recorded for a student who is actually enrolled
 * on the course. Without that check the database would happily store a row
 * joining two records that have no relationship, and the academic profile
 * would quietly show nonsense. This is the clearest example of why the
 * service layer exists: the check needs three DAOs to express, and no
 * single DAO is the right place for it.
 *
 * <h2>Arithmetic</h2>
 * The percentage is never computed and stored by this service. It is a
 * {@code STORED GENERATED} column in MySQL and a derived method on
 * {@link Attendance}; both use the same guarded formula, so a course with
 * no classes yet reads 0% instead of dividing by zero, and the two can
 * never disagree.
 */
public class AttendanceService extends BaseService {

    private final AttendanceDao attendanceDao;
    private final EnrollmentDao enrollmentDao;
    private final StudentDao studentDao;
    private final CourseDao courseDao;
    private final StudentService studentService;

    public AttendanceService(AttendanceDao attendanceDao, EnrollmentDao enrollmentDao,
                             StudentDao studentDao, CourseDao courseDao,
                             StudentService studentService) {
        this.attendanceDao = attendanceDao;
        this.enrollmentDao = enrollmentDao;
        this.studentDao = studentDao;
        this.courseDao = courseDao;
        this.studentService = studentService;
    }

    @Override
    protected String entityName() {
        return "Attendance record";
    }

    // ------------------------------------------------------------------
    // Create / Update
    // ------------------------------------------------------------------

    /**
     * Records attendance, creating the row or replacing it.
     *
     * <p>An upsert rather than an insert because the schema allows one row
     * per (student, course) pair: re-recording a fortnight's classes
     * corrects the existing row, and offering the user a choice between
     * "add" and "update" for what is really one record would be noise.
     *
     * @param studentId      an enrolled student
     * @param courseId       the course they are enrolled on
     * @param classesHeld    total classes conducted, 0 or more
     * @param classesAttended classes the student attended, 0 to held
     */
    public void record(String studentId, String courseId, int classesHeld, int classesAttended)
            throws AppException {
        requireEnrolled(studentId, courseId);

        Attendance attendance = new Attendance(studentId.trim(), courseId.trim(),
                classesHeld, classesAttended, java.time.LocalDate.now());
        attendanceDao.upsert(attendance);

        log("Recorded attendance " + studentId + "/" + courseId + ": "
                + classesAttended + "/" + classesHeld
                + " (" + attendance.getAttendancePercentage() + "%)");
    }

    /**
     * Same as {@link #record} but with an explicit date, so a figure can be
     * entered after the fact without losing when it was true.
     */
    public void recordOn(String studentId, String courseId, int classesHeld, int classesAttended,
                         java.time.LocalDate recordedOn) throws AppException {
        requireEnrolled(studentId, courseId);
        attendanceDao.upsert(new Attendance(studentId.trim(), courseId.trim(),
                classesHeld, classesAttended, recordedOn));
        log("Recorded attendance " + studentId + "/" + courseId + " dated " + recordedOn);
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    public List<Attendance> findAll() throws AppException {
        return attendanceDao.findAll();
    }

    public List<Attendance> findByStudent(String studentId) throws AppException {
        return attendanceDao.findByStudent(studentId);
    }

    public List<Attendance> findByCourse(String courseId) throws AppException {
        return attendanceDao.findByCourse(courseId);
    }

    public Attendance findByStudentAndCourse(String studentId, String courseId)
            throws AppException {
        return requireFound(attendanceDao.findByStudentAndCourse(studentId, courseId),
                studentId + " / " + courseId);
    }

    /** The existing record, or empty when attendance has not been started. */
    public java.util.Optional<Attendance> findExisting(String studentId, String courseId)
            throws AppException {
        return attendanceDao.findByStudentAndCourse(studentId, courseId);
    }

    public long count() throws AppException {
        return attendanceDao.count();
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    public void delete(String studentId, String courseId) throws AppException {
        if (attendanceDao.findByStudentAndCourse(studentId, courseId).isEmpty()) {
            throw new RecordNotFoundException("Attendance record", studentId + " / " + courseId);
        }
        attendanceDao.delete(studentId, courseId);
        log("Deleted attendance " + studentId + "/" + courseId);
    }

    // ------------------------------------------------------------------
    // Calculated views
    // ------------------------------------------------------------------

    /**
     * The attendance records of one student, with the course name resolved
     * so the table is readable.
     *
     * <p>Deliberately built from two DAO calls rather than one big join:
     * the set of courses a student attends is small, and resolving the
     * names in Java keeps the query simple and the mapping reusable.
     */
    public List<AttendanceRow> findRowsForStudent(String studentId) throws AppException {
        requireStudentExists(studentId);
        List<AttendanceRow> rows = new java.util.ArrayList<>();
        for (Attendance attendance : attendanceDao.findByStudent(studentId)) {
            String courseName = courseDao.findById(attendance.getCourseId())
                    .map(Course::getCourseName)
                    .orElse("(unknown course)");
            rows.add(AttendanceRow.of(attendance, courseName));
        }
        return rows;
    }

    /** Every attendance record in the system, with course names resolved. */
    public List<AttendanceRow> findAllRows() throws AppException {
        List<AttendanceRow> rows = new java.util.ArrayList<>();
        for (Attendance attendance : attendanceDao.findAll()) {
            String courseName = courseDao.findById(attendance.getCourseId())
                    .map(Course::getCourseName)
                    .orElse("(unknown course)");
            rows.add(AttendanceRow.of(attendance, courseName));
        }
        return rows;
    }

    /**
     * A course-level summary: how many students are recorded, and how many
     * clear the attendance requirement.
     */
    public CourseAttendanceSummary summariseCourse(String courseId) throws AppException {
        requireCourseExists(courseId);
        List<Attendance> records = attendanceDao.findByCourse(courseId);
        int recorded = records.size();
        int eligible = 0;
        int meeting = 0;
        for (Attendance record : records) {
            if (record.getClassesHeld() <= 0) {
                continue;
            }
            eligible++;
            if (record.getAttendancePercentage() >= Constants.ATTENDANCE_REQUIRED_PERCENT) {
                meeting++;
            }
        }
        return new CourseAttendanceSummary(courseId, recorded, eligible, meeting);
    }

    // ------------------------------------------------------------------
    // Workflow rules
    // ------------------------------------------------------------------

    /**
     * The courses a student may record attendance for: exactly the ones
     * they are enrolled on. Populating the combo box from this is what
     * makes an invalid pairing unreachable rather than merely rejected.
     */
    public List<Course> findEnrollableCourses(String studentId) throws AppException {
        return courseDao.findByStudent(studentId);
    }

    private void requireEnrolled(String studentId, String courseId) throws AppException {
        if (studentId == null || studentId.isBlank() || courseId == null || courseId.isBlank()) {
            throw new BusinessRuleException("Select both a student and a course.",
                    "Incomplete attendance request");
        }
        requireStudentExists(studentId);
        requireCourseExists(courseId);
        studentService.requireEnrolled(studentId.trim(), courseId.trim());
    }

    private void requireStudentExists(String studentId) throws AppException {
        if (!studentDao.existsById(studentId.trim())) {
            throw new RecordNotFoundException("Student", studentId);
        }
    }

    private void requireCourseExists(String courseId) throws AppException {
        if (!courseDao.existsById(courseId.trim())) {
            throw new RecordNotFoundException("Course", courseId);
        }
    }

    /**
     * One attendance record plus the course name, shaped for a table row.
     *
     * @param studentId       the student
     * @param courseId        the course
     * @param courseName      resolved course name
     * @param classesHeld     classes conducted
     * @param classesAttended classes attended
     * @param percentage      the guarded percentage
     * @param meetsRequirement whether it clears the required attendance
     */
    public record AttendanceRow(String studentId, String courseId, String courseName,
                                int classesHeld, int classesAttended, double percentage,
                                boolean meetsRequirement) {

        static AttendanceRow of(Attendance attendance, String courseName) {
            return new AttendanceRow(attendance.getStudentId(), attendance.getCourseId(),
                    courseName, attendance.getClassesHeld(), attendance.getClassesAttended(),
                    attendance.getAttendancePercentage(),
                    attendance.getAttendancePercentage() >= Constants.ATTENDANCE_REQUIRED_PERCENT);
        }
    }

    /**
     * @param courseId   the course
     * @param recorded   how many attendance rows exist
     * @param eligible   how many have at least one class held
     * @param meeting    how many of those meet the requirement
     */
    public record CourseAttendanceSummary(String courseId, int recorded, int eligible,
                                          int meeting) {
    }
}
