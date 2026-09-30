package com.academic.management.service;

import com.academic.management.model.Course;
import com.academic.management.model.CourseSearchCriteria;
import com.academic.management.model.Faculty;
import com.academic.management.model.FacultySearchCriteria;
import com.academic.management.model.Student;
import com.academic.management.model.StudentSearchCriteria;
import com.academic.management.exception.AppException;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-entity search for the search screen.
 *
 * <p>The three searches are separate queries rather than one combined
 * statement because they are three different tables with different
 * columns; the service's job is to accept a single query string from the
 * user, hand it to each entity's own search with its own criteria object,
 * and present the results in a common shape. Each search stays a
 * parameterised {@code LIKE} in its own DAO, so the wildcard is a value,
 * never part of the statement.
 */
public class SearchService extends BaseService {

    private final StudentService studentService;
    private final FacultyService facultyService;
    private final CourseService courseService;

    public SearchService(StudentService studentService, FacultyService facultyService,
                         CourseService courseService) {
        this.studentService = studentService;
        this.facultyService = facultyService;
        this.courseService = courseService;
    }

    @Override
    protected String entityName() {
        return "Search result";
    }

    /** Which entity a search targets. */
    public enum Scope {
        STUDENTS("Students"),
        FACULTY("Faculty"),
        COURSES("Courses");

        private final String label;

        Scope(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * One search result, in a shape all three entity types can share so the
     * results table does not need rebuilding when the scope changes.
     *
     * @param entity  the kind of record
     * @param id      the primary key
     * @param primary the main identifier, e.g. a course code
     * @param name    the display name
     * @param detail  a secondary detail line
     * @param extra   an extra column, e.g. semester or designation
     */
    public record SearchHit(String entity, String id, String primary, String name,
                            String detail, String extra) {
    }

    /**
     * Runs a search.
     *
     * @param scope which entity to search
     * @param term  the text typed by the user; blank means "match
     *              everything", which is more useful than an error when a
     *              user opens search before typing
     * @return the matching records, possibly empty
     */
    public List<SearchHit> search(Scope scope, String term) throws AppException {
        String query = term == null ? "" : term.trim();
        List<SearchHit> hits = new ArrayList<>();
        switch (scope) {
            case STUDENTS -> addStudents(hits, query);
            case FACULTY -> addFaculty(hits, query);
            case COURSES -> addCourses(hits, query);
        }
        log("Searched " + scope + " for '" + query + "': " + hits.size() + " match(es)");
        return hits;
    }

    /**
     * Unions the per-field searches and removes duplicates.
     *
     * <p>Searching id and name in one call would be wrong: the criteria
     * fields are combined with {@code AND}, so a record whose id matches
     * but whose name does not would be dropped. The user means "any of
     * these", so each field is queried separately and the ids are
     * intersected back into one ordered list.
     */
    private void addStudents(List<SearchHit> hits, String query) throws AppException {
        if (query.isEmpty()) {
            for (Student student : studentService.search(StudentSearchCriteria.all())) {
                hits.add(toHit(student));
            }
            return;
        }
        java.util.LinkedHashMap<String, Student> merged = new java.util.LinkedHashMap<>();
        for (Student student : studentService.search(StudentSearchCriteria.byId(query))) {
            merged.put(student.getId(), student);
        }
        for (Student student : studentService.search(StudentSearchCriteria.byName(query))) {
            merged.putIfAbsent(student.getId(), student);
        }
        StudentSearchCriteria withEmail = new StudentSearchCriteria();
        withEmail.setName(query);
        withEmail.setIncludeEmailMatch(true);
        for (Student student : studentService.search(withEmail)) {
            merged.putIfAbsent(student.getId(), student);
        }
        merged.values().forEach(student -> hits.add(toHit(student)));
    }

    private SearchHit toHit(Student student) {
        return new SearchHit("Student", student.getId(), student.getId(), student.getName(),
                student.getEmail() == null ? "" : student.getEmail(),
                student.getDepartment() + " · Sem " + student.getSemester());
    }

    private void addFaculty(List<SearchHit> hits, String query) throws AppException {
        if (query.isEmpty()) {
            for (Faculty faculty : facultyService.search(FacultySearchCriteria.all())) {
                hits.add(toHit(faculty));
            }
            return;
        }
        java.util.LinkedHashMap<String, Faculty> merged = new java.util.LinkedHashMap<>();
        for (Faculty faculty : facultyService.search(FacultySearchCriteria.byId(query))) {
            merged.put(faculty.getId(), faculty);
        }
        for (Faculty faculty : facultyService.search(FacultySearchCriteria.byName(query))) {
            merged.putIfAbsent(faculty.getId(), faculty);
        }
        merged.values().forEach(faculty -> hits.add(toHit(faculty)));
    }

    private SearchHit toHit(Faculty faculty) {
        return new SearchHit("Faculty", faculty.getId(), faculty.getId(), faculty.getName(),
                faculty.getEmail() == null ? "" : faculty.getEmail(),
                faculty.getDesignation());
    }

    private void addCourses(List<SearchHit> hits, String query) throws AppException {
        if (query.isEmpty()) {
            for (Course course : courseService.search(CourseSearchCriteria.all())) {
                hits.add(toHit(course));
            }
            return;
        }
        java.util.LinkedHashMap<String, Course> merged = new java.util.LinkedHashMap<>();
        for (Course course : courseService.search(CourseSearchCriteria.byId(query))) {
            merged.put(course.getCourseId(), course);
        }
        for (Course course : courseService.search(CourseSearchCriteria.byCode(query))) {
            merged.putIfAbsent(course.getCourseId(), course);
        }
        for (Course course : courseService.search(CourseSearchCriteria.byName(query))) {
            merged.putIfAbsent(course.getCourseId(), course);
        }
        merged.values().forEach(course -> hits.add(toHit(course)));
    }

    private SearchHit toHit(Course course) {
        return new SearchHit("Course", course.getCourseId(), course.getCourseCode(),
                course.getCourseName(), course.getDepartment(),
                course.getCredits() + " credits");
    }
}
