package com.academic.management.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Search request for faculty members. See {@link StudentSearchCriteria}
 * for why an optional-criteria object is used instead of many overloads.
 */
public final class FacultySearchCriteria {

    private String facultyId;
    private String name;
    private String department;
    private String designation;

    public static FacultySearchCriteria byId(String facultyId) {
        FacultySearchCriteria criteria = new FacultySearchCriteria();
        criteria.facultyId = facultyId;
        return criteria;
    }

    public static FacultySearchCriteria byName(String name) {
        FacultySearchCriteria criteria = new FacultySearchCriteria();
        criteria.name = name;
        return criteria;
    }

    public static FacultySearchCriteria byDepartment(String department) {
        FacultySearchCriteria criteria = new FacultySearchCriteria();
        criteria.department = department;
        return criteria;
    }

    public static FacultySearchCriteria byDesignation(String designation) {
        FacultySearchCriteria criteria = new FacultySearchCriteria();
        criteria.designation = designation;
        return criteria;
    }

    public static FacultySearchCriteria all() {
        return new FacultySearchCriteria();
    }

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = blankToNull(facultyId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = blankToNull(name);
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = blankToNull(department);
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = blankToNull(designation);
    }

    public boolean isEmpty() {
        return facultyId == null && name == null && department == null
                && designation == null;
    }

    public String nameLikePattern() {
        return "%" + Objects.toString(name, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    public String departmentLikePattern() {
        return "%" + Objects.toString(department, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    public String designationLikePattern() {
        return "%" + Objects.toString(designation, "").toLowerCase(Locale.ROOT).trim() + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "FacultySearchCriteria[id=" + facultyId + ", name=" + name
                + ", department=" + department + ", designation=" + designation + "]";
    }
}
