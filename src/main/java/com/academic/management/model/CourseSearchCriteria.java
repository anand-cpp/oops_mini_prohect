package com.academic.management.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Search request for courses. See {@link StudentSearchCriteria} for why
 * an optional-criteria object is used instead of many overloads.
 */
public final class CourseSearchCriteria {

    private String courseId;
    private String courseCode;
    private String courseName;
    private String department;
    private String facultyId;
    private Integer semester;

    public static CourseSearchCriteria byId(String courseId) {
        CourseSearchCriteria criteria = new CourseSearchCriteria();
        criteria.courseId = courseId;
        return criteria;
    }

    public static CourseSearchCriteria byCode(String courseCode) {
        CourseSearchCriteria criteria = new CourseSearchCriteria();
        criteria.courseCode = courseCode;
        return criteria;
    }

    public static CourseSearchCriteria byName(String courseName) {
        CourseSearchCriteria criteria = new CourseSearchCriteria();
        criteria.courseName = courseName;
        return criteria;
    }

    public static CourseSearchCriteria byFaculty(String facultyId) {
        CourseSearchCriteria criteria = new CourseSearchCriteria();
        criteria.facultyId = facultyId;
        return criteria;
    }

    public static CourseSearchCriteria all() {
        return new CourseSearchCriteria();
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = blankToNull(courseId);
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = blankToNull(courseCode);
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = blankToNull(courseName);
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = blankToNull(department);
    }

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = blankToNull(facultyId);
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }

    public boolean isEmpty() {
        return courseId == null && courseCode == null && courseName == null
                && department == null && facultyId == null && semester == null;
    }

    public String codeLikePattern() {
        return "%" + Objects.toString(courseCode, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    public String nameLikePattern() {
        return "%" + Objects.toString(courseName, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    public String departmentLikePattern() {
        return "%" + Objects.toString(department, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "CourseSearchCriteria[id=" + courseId + ", code=" + courseCode
                + ", name=" + courseName + ", department=" + department
                + ", faculty=" + facultyId + ", semester=" + semester + "]";
    }
}
