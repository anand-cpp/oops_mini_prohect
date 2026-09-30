package com.academic.management.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Search request for students. Every field is optional; a {@code null}
 * field is simply not included in the {@code WHERE} clause, so the DAO
 * can build one statement covering every combination instead of a dozen
 * near-identical ones.
 */
public final class StudentSearchCriteria {

    private String studentId;
    private String name;
    private String department;
    private Gender gender;
    private Integer semester;
    private boolean includeEmailMatch;

    public static StudentSearchCriteria byId(String studentId) {
        StudentSearchCriteria criteria = new StudentSearchCriteria();
        criteria.studentId = studentId;
        return criteria;
    }

    public static StudentSearchCriteria byName(String name) {
        StudentSearchCriteria criteria = new StudentSearchCriteria();
        criteria.name = name;
        return criteria;
    }

    public static StudentSearchCriteria byDepartment(String department) {
        StudentSearchCriteria criteria = new StudentSearchCriteria();
        criteria.department = department;
        return criteria;
    }

    public static StudentSearchCriteria all() {
        return new StudentSearchCriteria();
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = blankToNull(studentId);
    }

    /** Matched as a case-insensitive substring. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = blankToNull(name);
    }

    /** Matched as a case-insensitive substring. */
    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = blankToNull(department);
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }

    public boolean isIncludeEmailMatch() {
        return includeEmailMatch;
    }

    public void setIncludeEmailMatch(boolean includeEmailMatch) {
        this.includeEmailMatch = includeEmailMatch;
    }

    /** True when no criterion has been set, meaning "match everything". */
    public boolean isEmpty() {
        return studentId == null && name == null && department == null
                && gender == null && semester == null && !includeEmailMatch;
    }

    /** Normalised pattern for a {@code LIKE} comparison. */
    public String likePattern() {
        return "%" + Objects.toString(name, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    public String departmentLikePattern() {
        return "%" + Objects.toString(department, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "StudentSearchCriteria[id=" + studentId + ", name=" + name
                + ", department=" + department + ", gender=" + gender
                + ", semester=" + semester + "]";
    }
}
