package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.model.Course;
import com.academic.management.model.Enrollment;
import com.academic.management.model.IdName;
import com.academic.management.service.EnrollmentService;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.MainFrame;
import com.academic.management.ui.common.DataTable;
import com.academic.management.ui.common.DisplayTableModel;
import com.academic.management.ui.common.FormDialog;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.ui.common.UiSupport;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is taking which course.
 *
 * <h2>Why this is the pivot of the application</h2>
 * An enrolment is the one record every other workflow depends on: attendance
 * and results both refuse to save for a student who is not enrolled, and
 * both cascade away when the enrolment is removed. That is why this page
 * exists as its own screen rather than as a field on the student form.
 *
 * <h2>Why the course list depends on the student</h2>
 * The enrol form's course dropdown is populated from the courses the
 * selected student is <em>not</em> already enrolled on. Showing every course
 * and rejecting a duplicate at save time would make the user discover the
 * rule by failing it; narrowing the list makes the invalid pairing
 * unreachable. The semester defaults to the student's own, because that is
 * what it almost always is, while staying editable.
 */
public final class EnrollmentsPanel extends PagePanel {

    private static final long serialVersionUID = 1L;

    private static final String[] HEADERS = {
            "Student ID", "Student", "Course ID", "Course", "Semester", "Enrolled on", "Status"};

    private static final int[] WIDTHS = {100, 180, 100, 250, 90, 120, 110};

    private static final int STUDENT_ID_COLUMN = 0;
    private static final int STUDENT_COLUMN = 1;
    private static final int COURSE_ID_COLUMN = 2;
    private static final int COURSE_COLUMN = 3;
    private static final int SEMESTER_COLUMN = 4;
    private static final int STATUS_COLUMN = 6;

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model)
            .fit(STUDENT_COLUMN, COURSE_COLUMN)
            .numeric(SEMESTER_COLUMN)
            .verdict(STATUS_COLUMN);

    private final JTextField searchField = Theme.styleInput(new JTextField());
    private final JComboBox<String> statusFilter = Theme.styleSelect(new JComboBox<>());

    /**
     * The centre of the page, holding either the table or the empty state.
     *
     * <p>Held as a field rather than built inside {@code buildBody} because
     * {@link #showRows} has to swap its child on every render.
     */
    private final JPanel tableArea = new JPanel(new BorderLayout());

    private String searchTerm = "";
    private String status = "";

    /** Lookups built once per load, so the table is three queries not N. */
    private transient Map<String, String> studentNames = Map.of();
    private transient Map<String, Course> courses = Map.of();

    public EnrollmentsPanel(ServiceRegistry services) {
        super("Enrolments", "Enrol students on courses, change a status, or remove an "
                + "enrolment. Removing one also removes its attendance and result.");
        this.services = services;

        JButton enrol = Theme.button("Enrol a student", Theme.PRIMARY);
        enrol.addActionListener(event -> showEnrolForm());
        addToolbarButton(enrol);

        JButton statusButton = Theme.secondaryButton("Change status");
        statusButton.addActionListener(event -> showStatusForm());
        addToolbarButton(statusButton);

        JButton remove = Theme.dangerButton("Remove selected");
        remove.addActionListener(event -> removeSelected());
        addToolbarButton(remove);

        JButton refresh = Theme.secondaryButton("Refresh");
        refresh.addActionListener(event -> reload());
        addActionButton(refresh);

        setContent(buildBody());
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildBody() {
        searchField.setPreferredSize(new Dimension(240, 30));
        searchField.setToolTipText("Type to filter. Matches the student and the course.");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                searchChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                searchChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                searchChanged();
            }
        });

        statusFilter.setPreferredSize(new Dimension(160, 30));
        for (Enrollment.Status value : Enrollment.Status.values()) {
            statusFilter.addItem(value.getLabel());
        }
        statusFilter.addActionListener(event -> {
            Object selected = statusFilter.getSelectedItem();
            status = selected == null ? "" : String.valueOf(selected);
            reload();
        });

        JPanel filters = row(filterLabel("Search:", searchField),
                filterLabel("Status:", statusFilter), glue());
        filters.setBorder(Theme.padding(0, 0, Theme.GAP, 0));

        tableArea.setOpaque(false);
        tableArea.add(table.scroll(), BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, Theme.GAP));
        body.setOpaque(false);
        body.add(filters, BorderLayout.NORTH);
        body.add(tableArea, BorderLayout.CENTER);
        return body;
    }

    private void searchChanged() {
        searchTerm = searchField.getText().trim();
        reloadWhenTypingSettles();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    @Override
    protected List<String[]> load() throws AppException {
        studentNames = loadStudentNames();
        courses = loadCourses();

        List<Enrollment> enrolments = services.enrollments().findAll();
        List<String[]> rows = new ArrayList<>();
        for (Enrollment enrollment : enrolments) {
            if (!status.isEmpty() && !status.equals(enrollment.getStatus().getLabel())) {
                continue;
            }
            if (!matchesSearch(enrollment)) {
                continue;
            }
            Course course = courses.get(enrollment.getCourseId());
            rows.add(new String[]{
                    enrollment.getStudentId(),
                    studentNames.getOrDefault(enrollment.getStudentId(),
                            enrollment.getStudentId()),
                    enrollment.getCourseId(),
                    course == null ? enrollment.getCourseId() : course.getDisplayName(),
                    String.valueOf(enrollment.getSemester()),
                    UiSupport.date(enrollment.getEnrollmentDate()),
                    enrollment.getStatus().getLabel()});
        }
        rows.sort((left, right) -> {
            int byStudent = left[0].compareToIgnoreCase(right[0]);
            return byStudent != 0 ? byStudent
                    : Integer.compare(Integer.parseInt(left[4]), Integer.parseInt(right[4]));
        });
        return rows;
    }

    /**
     * Filters in memory on the resolved names as well as the ids.
     *
     * <p>The alternative is three separate queries for every scope of the
     * search term, which the search service does not have a method for, and
     * the enrolment table is small enough that reading it once and filtering
     * is both simpler and faster.
     */
    private boolean matchesSearch(Enrollment enrollment) {
        if (searchTerm.isEmpty()) {
            return true;
        }
        String needle = searchTerm.toLowerCase(java.util.Locale.ENGLISH);
        String student = studentNames.getOrDefault(enrollment.getStudentId(),
                enrollment.getStudentId());
        Course course = courses.get(enrollment.getCourseId());
        String courseName = course == null ? "" : course.getDisplayName();
        return enrollment.getStudentId().toLowerCase(java.util.Locale.ENGLISH).contains(needle)
                || student.toLowerCase(java.util.Locale.ENGLISH).contains(needle)
                || enrollment.getCourseId().toLowerCase(java.util.Locale.ENGLISH).contains(needle)
                || courseName.toLowerCase(java.util.Locale.ENGLISH).contains(needle);
    }

    private Map<String, String> loadStudentNames() throws AppException {
        Map<String, String> names = new LinkedHashMap<>();
        for (IdName summary : services.students().findSummaries()) {
            names.put(summary.id(), summary.label());
        }
        return names;
    }

    private Map<String, Course> loadCourses() throws AppException {
        Map<String, Course> byId = new LinkedHashMap<>();
        for (Course course : services.courses().findAll()) {
            byId.put(course.getCourseId(), course);
        }
        return byId;
    }

    @Override
    protected void render(List<String[]> rows) {
        setStatus(UiSupport.count(rows.size(), "enrolment", "enrolments")
                + (searchTerm.isEmpty() ? "" : " matching '" + searchTerm + "'"));
        showRows(tableArea, table.scroll(), rows.size(),
                rows.isEmpty() && searchTerm.isEmpty() && status.isEmpty()
                        ? "No enrolments yet. Use Enrol a student to add the first one."
                        : "No enrolment matches the current filters.");
    }

    // ------------------------------------------------------------------
    // Enrol
    // ------------------------------------------------------------------

    private void showEnrolForm() {
        UiErrors.runAsync(this, "Could not open the enrolment form", () -> {
            List<IdName> students = services.students().findSummaries();
            if (students.isEmpty()) {
                throw new com.academic.management.exception.BusinessRuleException(
                        "There are no students to enrol yet. Add a student first.",
                        "Enrol form opened with an empty student list");
            }
            List<Course> available = services.courses().findAll();
            if (available.isEmpty()) {
                throw new com.academic.management.exception.BusinessRuleException(
                        "There are no courses yet. Add a course first.",
                        "Enrol form opened with an empty course list");
            }
            return new EnrolSetup(students, available);
        }, this::showEnrolDialog);
    }

    /** The lists the enrol dialog needs, fetched together off the event thread. */
    private record EnrolSetup(List<IdName> students, List<Course> courses) {
    }

    /**
     * Shows the enrol form.
     *
     * <p>Choosing a student reloads the course dropdown, because which
     * courses are offered depends on what that student is already enrolled
     * on. That is a database read, so it goes through
     * {@link UiErrors#runAsync}; until it returns the dropdown is disabled,
     * so a course cannot be picked from a list that belonged to the previous
     * student.
     */
    private void showEnrolDialog(EnrolSetup setup) {
        List<String> studentOptions = new ArrayList<>();
        for (IdName summary : setup.students()) {
            studentOptions.add(summary.id() + " — " + summary.label());
        }

        FormDialog form = new FormDialog(windowOf(this), "Enrol a student", "Enrol",
                values -> {
                    String studentId = idBeforeDash(FormDialog.required(values, "Student"));
                    String courseId = idBeforeDash(FormDialog.required(values, "Course"));
                    services.enrollments().enroll(studentId, courseId,
                            FormDialog.requiredSemester(values, "Semester"),
                            UiSupport.parseFlexibleDate("Enrolled on",
                                    values.get("Enrolled on")));
                });

        JComboBox<String> studentCombo = form.addComboField("Student", studentOptions);
        JComboBox<String> courseCombo = form.addComboField("Course", new ArrayList<>());
        form.addField("Semester", "1", 6, FormDialog.semesterRule());
        form.addDateField("Enrolled on", UiSupport.todayForEditing(),
                (label, text) -> UiSupport.parseFlexibleDate(label, text));
        form.addNote("Only courses the student is not already enrolled on are offered.");

        // Populate the courses for whichever student is selected, starting
        // with the first.
        Runnable loadCoursesForSelectedStudent = () -> {
            String chosen = String.valueOf(studentCombo.getSelectedItem());
            if (chosen == null || chosen.startsWith("null")) {
                return;
            }
            String studentId = idBeforeDash(chosen);
            courseCombo.setEnabled(false);
            UiErrors.runAsync(this, "Could not list the courses",
                    () -> enrolableCourseIds(studentId, setup.courses()),
                    ids -> {
                        courseCombo.removeAllItems();
                        for (Course course : setup.courses()) {
                            if (ids.contains(course.getCourseId())) {
                                courseCombo.addItem(course.getCourseId() + " — "
                                        + course.getDisplayName());
                            }
                        }
                        courseCombo.setEnabled(true);
                        courseCombo.setSelectedIndex(courseCombo.getItemCount() - 1);
                        form.revalidate();
                    },
                    () -> {
                        courseCombo.setEnabled(true);
                        courseCombo.removeAllItems();
                        courseCombo.addItem("— no course available —");
                    });
        };

        // Populate the courses for whichever student is selected, starting
        // with the first. One method serves both the initial fill and every
        // later change of student, so the two cannot diverge.
        studentCombo.addActionListener(event -> {
            if (form.isShowing()) {
                loadCoursesForSelectedStudent.run();
            }
        });
        loadCoursesForSelectedStudent.run();
        suggestSemester(studentCombo, form);

        form.showDialog();
        if (form.wasSaved()) {
            setStatus("Enrolment saved.");
            reload();
            refreshOtherPages(MainFrame.ATTENDANCE, MainFrame.RESULTS,
                    MainFrame.PROFILES, MainFrame.SEARCH);
        }
    }

    /**
     * The course ids a student may still be enrolled on.
     *
     * <p>The existing enrolments are read once and the offered list is the
     * difference, rather than querying "not enrolled in" - which keeps the
     * check on the same code path the save will use, so the dropdown cannot
     * offer something the service will then refuse.
     */
    private java.util.Set<String> enrolableCourseIds(String studentId, List<Course> all)
            throws AppException {
        java.util.Set<String> taken = new java.util.LinkedHashSet<>();
        for (Enrollment existing : services.enrollments().findByStudent(studentId)) {
            taken.add(existing.getCourseId());
        }
        java.util.Set<String> offerable = new java.util.LinkedHashSet<>();
        for (Course course : all) {
            if (!taken.contains(course.getCourseId())) {
                offerable.add(course.getCourseId());
            }
        }
        return offerable;
    }

    /** Puts the student's own semester into the form's semester field. */
    private void suggestSemester(JComboBox<String> studentCombo, FormDialog form) {
        Object selected = studentCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        UiErrors.runAsync(this, "Could not read the student's semester", () ->
                        services.enrollments().suggestSemester(
                                idBeforeDash(String.valueOf(selected))),
                semester -> form.setFieldText("Semester", String.valueOf(semester)));
    }

    /** The id part of a "STU001 — Name" dropdown entry. */
    private static String idBeforeDash(String option) {
        int dash = option.indexOf(" — ");
        return dash < 0 ? option : option.substring(0, dash);
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    private void showStatusForm() {
        String studentId = table.selectedValue(STUDENT_ID_COLUMN);
        String courseId = table.selectedValue(COURSE_ID_COLUMN);
        if (studentId == null || courseId == null) {
            UiErrors.showWarning(this, "No enrolment selected",
                    "Select an enrolment in the table, then choose Change status.");
            return;
        }
        UiErrors.runAsync(this, "Could not open the enrolment",
                () -> services.enrollments().findByStudentAndCourse(studentId, courseId),
                this::showStatusDialog);
    }

    private void showStatusDialog(Enrollment enrollment) {
        List<String> options = new ArrayList<>();
        for (Enrollment.Status value : Enrollment.Status.values()) {
            options.add(value.getLabel());
        }

        FormDialog form = new FormDialog(windowOf(this), "Change enrolment status", "Save",
                values -> {
                    Enrollment updated = new Enrollment(enrollment.getStudentId(),
                            enrollment.getCourseId(), enrollment.getSemester(),
                            enrollment.getEnrollmentDate(),
                            Enrollment.Status.fromLabel(
                                    FormDialog.required(values, "Status")));
                    updated.setEnrollmentId(enrollment.getEnrollmentId());
                    services.enrollments().updateStatus(updated);
                });

        form.addLockedField("Student", enrollment.getStudentId());
        form.addLockedField("Course", courseNameOf(enrollment.getCourseId()));
        JComboBox<String> combo = form.addComboField("Status", options);
        combo.setSelectedItem(enrollment.getStatus().getLabel());
        form.addNote("Completed and Withdrawn enrolments stay in the table as a record;"
                + " only attendance and results require an active enrolment.");

        form.showDialog();
        if (form.wasSaved()) {
            setStatus("Updated the status of " + enrollment.getStudentId() + " / "
                    + enrollment.getCourseId() + ".");
            reload();
            refreshOtherPages(MainFrame.ATTENDANCE, MainFrame.RESULTS,
                    MainFrame.PROFILES, MainFrame.SEARCH);
        }
    }

    private String courseNameOf(String courseId) {
        Course course = courses.get(courseId);
        return course == null ? courseId : course.getDisplayName();
    }

    // ------------------------------------------------------------------
    // Remove
    // ------------------------------------------------------------------

    private void removeSelected() {
        String studentId = table.selectedValue(STUDENT_ID_COLUMN);
        String courseId = table.selectedValue(COURSE_ID_COLUMN);
        if (studentId == null || courseId == null) {
            UiErrors.showWarning(this, "No enrolment selected",
                    "Select an enrolment in the table, then choose Remove.");
            return;
        }
        // Two steps rather than one, because the confirmation is a Swing
        // dialog and a Swing dialog has to be shown on the event thread -
        // asking for it from inside the worker would show it off the event
        // thread, where it can deadlock against the thread that owns it.
        UiErrors.runAsync(this, "Could not read what removing this enrolment would affect",
                () -> services.enrollments().describeDeletion(studentId, courseId).describe(),
                impact -> {
                    if (UiErrors.confirmDetailed(this, "Remove enrolment", impact)) {
                        removeNow(studentId, courseId);
                    }
                });
    }

    private void removeNow(String studentId, String courseId) {
        UiErrors.runAsync(this, "Could not remove the enrolment",
                () -> {
                    services.enrollments().unenroll(studentId, courseId);
                    return Boolean.TRUE;
                },
                removed -> {
                    table.clearSelection();
                    setStatus("Removed " + studentId + " from " + courseId + ".");
                    reload();
                    refreshOtherPages(MainFrame.ATTENDANCE, MainFrame.RESULTS,
                            MainFrame.PROFILES, MainFrame.SEARCH);
                });
    }
}
