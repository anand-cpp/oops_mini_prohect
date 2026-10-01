package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.model.Attendance;
import com.academic.management.model.Course;
import com.academic.management.model.IdName;
import com.academic.management.service.AttendanceService;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.MainFrame;
import com.academic.management.ui.common.DataTable;
import com.academic.management.ui.common.DisplayTableModel;
import com.academic.management.ui.common.FormDialog;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.ui.common.UiSupport;
import com.academic.management.util.Validator;

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
import java.util.Locale;
import java.util.Map;

/**
 * Attendance, recorded as "attended out of held" per student per course.
 *
 * <h2>Why the counts are cumulative rather than per-session</h2>
 * The schema holds one row per (student, course) holding running totals, and
 * the screen edits that row directly. A per-session model would be truer to
 * how a class actually runs, but it would make the percentage a query rather
 * than a stored value, and every read of the profile screen would then have
 * to aggregate a growing number of sessions. The running total is recorded
 * with the date it was last corrected, so a user entering a fortnight's
 * classes at once still says when the figure is true.
 *
 * <h2>Why only enrolled courses are offered</h2>
 * The service refuses attendance for a student who is not enrolled, so the
 * course dropdown is filled from that student's enrolments. This turns the
 * rule from a rejection into an absence: the invalid combination is not on
 * the list at all.
 */
public final class AttendancePanel extends PagePanel {

    private static final long serialVersionUID = 1L;

    private static final String[] HEADERS = {
            "Student ID", "Student", "Course ID", "Course",
            "Held", "Attended", "Absent", "Percentage", "Status", "Recorded on"};

    private static final int[] WIDTHS = {100, 180, 100, 240, 70, 80, 70, 100, 110, 120};

    private static final int STUDENT_ID_COLUMN = 0;
    private static final int STUDENT_COLUMN = 1;
    private static final int COURSE_ID_COLUMN = 2;
    private static final int COURSE_COLUMN = 3;
    private static final int HELD_COLUMN = 4;
    private static final int ATTENDED_COLUMN = 5;
    private static final int ABSENT_COLUMN = 6;
    private static final int PERCENTAGE_COLUMN = 7;
    private static final int STATUS_COLUMN = 8;

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model)
            .fit(STUDENT_COLUMN, COURSE_COLUMN)
            .numeric(HELD_COLUMN, ATTENDED_COLUMN, ABSENT_COLUMN, PERCENTAGE_COLUMN)
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

    private transient Map<String, String> studentNames = Map.of();
    private transient Map<String, Course> courses = Map.of();

    public AttendancePanel(ServiceRegistry services) {
        super("Attendance", "Record how many classes each student has attended. The two counts "
                + "are running totals, so a later entry corrects the earlier one.");
        this.services = services;

        JButton record = Theme.button("Record attendance", Theme.PRIMARY);
        record.addActionListener(event -> showRecordForm(null));
        addToolbarButton(record);

        JButton edit = Theme.secondaryButton("Edit selected");
        edit.addActionListener(event -> editSelected());
        addToolbarButton(edit);

        JButton delete = Theme.dangerButton("Delete selected");
        delete.addActionListener(event -> deleteSelected());
        addToolbarButton(delete);

        JButton summary = Theme.secondaryButton("Course summary");
        summary.addActionListener(event -> showCourseSummary());
        addToolbarButton(summary);

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

        statusFilter.setPreferredSize(new Dimension(170, 30));
        statusFilter.addItem("All attendance");
        statusFilter.addItem("Regular");
        statusFilter.addItem("Irregular");
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

    /**
     * Reads the table.
     *
     * <p>Uses {@link AttendanceService#findAll()} rather than
     * {@code findAllRows()} because this screen shows the date each figure
     * was recorded on, and the row projection does not carry it. The course
     * name comes from the lookup already built for the whole table, so the
     * cost is the one query the lookups cost rather than one per row.
     */
    @Override
    protected List<String[]> load() throws AppException {
        studentNames = loadStudentNames();
        courses = loadCourses();

        List<String[]> rows = new ArrayList<>();
        for (Attendance record : services.attendance().findAll()) {
            String verdict = record.hasRegularAttendance() ? "Regular" : "Irregular";
            if (!status.isEmpty() && !"All attendance".equals(status)
                    && !status.equals(verdict)) {
                continue;
            }
            if (!matchesSearch(record)) {
                continue;
            }
            rows.add(new String[]{
                    record.getStudentId(),
                    studentNames.getOrDefault(record.getStudentId(), record.getStudentId()),
                    record.getCourseId(),
                    courseNameOf(record.getCourseId()),
                    String.valueOf(record.getClassesHeld()),
                    String.valueOf(record.getClassesAttended()),
                    String.valueOf(record.getClassesAbsent()),
                    UiSupport.percent(Double.valueOf(record.getAttendancePercentage())),
                    verdict,
                    UiSupport.date(record.getRecordedOn())});
        }
        rows.sort((left, right) -> {
            int byStudent = left[0].compareToIgnoreCase(right[0]);
            return byStudent != 0 ? byStudent : left[2].compareToIgnoreCase(right[2]);
        });
        return rows;
    }

    private boolean matchesSearch(Attendance record) {
        if (searchTerm.isEmpty()) {
            return true;
        }
        String needle = searchTerm.toLowerCase(Locale.ENGLISH);
        String name = studentNames.getOrDefault(record.getStudentId(), record.getStudentId());
        String courseName = courseNameOf(record.getCourseId());
        return record.getStudentId().toLowerCase(Locale.ENGLISH).contains(needle)
                || name.toLowerCase(Locale.ENGLISH).contains(needle)
                || record.getCourseId().toLowerCase(Locale.ENGLISH).contains(needle)
                || courseName.toLowerCase(Locale.ENGLISH).contains(needle);
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
        setStatus(UiSupport.count(rows.size(), "attendance record", "attendance records")
                + (searchTerm.isEmpty() ? "" : " matching '" + searchTerm + "'"));
        showRows(tableArea, table.scroll(), rows.size(),
                rows.isEmpty() && searchTerm.isEmpty()
                        && ("All attendance".equals(status) || status.isEmpty())
                        ? "No attendance recorded yet. Use Record attendance to add the "
                          + "first row."
                        : "No attendance record matches the current filters.");
    }

    // ------------------------------------------------------------------
    // Record and edit
    // ------------------------------------------------------------------

    private void editSelected() {
        String studentId = table.selectedValue(STUDENT_ID_COLUMN);
        String courseId = table.selectedValue(COURSE_ID_COLUMN);
        if (studentId == null || courseId == null) {
            UiErrors.showWarning(this, "No attendance record selected",
                    "Select a record in the table, then choose Edit.");
            return;
        }
        UiErrors.runAsync(this, "Could not open the attendance record",
                () -> services.attendance().findByStudentAndCourse(studentId, courseId),
                this::showRecordForm);
    }

    /**
     * Shows the record form.
     *
     * @param existing the row being corrected, or null to record a new one
     */
    private void showRecordForm(Attendance existing) {
        boolean editing = existing != null;
        String lockedStudent = editing ? existing.getStudentId() : null;
        String lockedCourse = editing ? existing.getCourseId() : null;

        UiErrors.runAsync(this, "Could not open the attendance form", () -> {
            List<IdName> students = services.students().findSummaries();
            if (students.isEmpty()) {
                throw new com.academic.management.exception.BusinessRuleException(
                        "There are no students yet. Add a student first.",
                        "Attendance form opened with an empty student list");
            }
            return new RecordSetup(students, services.courses().findAll());
        }, setup -> showRecordDialog(setup, existing, lockedStudent, lockedCourse));
    }

    private record RecordSetup(List<IdName> students, List<Course> courses) {
    }

    private void showRecordDialog(RecordSetup setup, Attendance existing,
                                  String lockedStudent, String lockedCourse) {
        List<String> studentOptions = new ArrayList<>();
        for (IdName summary : setup.students()) {
            studentOptions.add(summary.id() + " — " + summary.label());
        }

        FormDialog form = new FormDialog(windowOf(this),
                editing(existing) ? "Edit attendance" : "Record attendance",
                editing(existing) ? "Save changes" : "Save",
                values -> {
                    String studentId = lockedStudent != null ? lockedStudent
                            : idBeforeDash(FormDialog.required(values, "Student"));
                    String courseId = lockedCourse != null ? lockedCourse
                            : idBeforeDash(FormDialog.required(values, "Course"));
                    int held = FormDialog.requiredInt(values, "Classes held");
                    int attended = FormDialog.requiredInt(values, "Classes attended");
                    services.attendance().recordOn(studentId, courseId, held, attended,
                            UiSupport.parseFlexibleDate("Recorded on",
                                    values.get("Recorded on")));
                });

        JComboBox<String> studentCombo;
        JComboBox<String> courseCombo;
        if (editing(existing)) {
            form.addLockedField("Student", existing.getStudentId() + " — "
                    + studentNames.getOrDefault(existing.getStudentId(),
                    existing.getStudentId()));
            form.addLockedField("Course", courseNameOf(existing.getCourseId()));
            studentCombo = null;
            courseCombo = null;
        } else {
            studentCombo = form.addComboField("Student", studentOptions);
            courseCombo = form.addComboField("Course", new ArrayList<>());
            form.addNote("Only the courses the student is enrolled on are offered.");
        }

        // The two counts sit side by side because the second cannot exceed
        // the first, and that relationship is easier to see on one row.
        form.addPair("Classes held",
                editing(existing) ? String.valueOf(existing.getClassesHeld()) : "0",
                (label, text) -> Validator.requireInt(label, text),
                "Classes attended",
                editing(existing) ? String.valueOf(existing.getClassesAttended()) : "0",
                (label, text) -> {
                    Validator.requireInt(label, text);
                    Validator.requireAttendanceCounts(
                            FormDialog.requiredInt(form.readValues(), "Classes held"),
                            Validator.requireInt(label, text));
                });
        form.addDateField("Recorded on",
                editing(existing) ? UiSupport.dateForEditing(existing.getRecordedOn())
                        : UiSupport.todayForEditing(),
                (label, text) -> UiSupport.parseFlexibleDate(label, text));

        if (studentCombo != null && courseCombo != null) {
            // The course list follows the student, and is a database read, so
            // it goes through the async helper and the dropdown is disabled
            // until it returns.
            studentCombo.addActionListener(event -> {
                if (form.isShowing()) {
                    loadEnrolledCourses(studentCombo, courseCombo, setup.courses());
                }
            });
            loadEnrolledCourses(studentCombo, courseCombo, setup.courses());
        }

        form.showDialog();
        if (form.wasSaved()) {
            setStatus(editing(existing) ? "Attendance updated." : "Attendance recorded.");
            reload();
            refreshOtherPages(MainFrame.PROFILES, MainFrame.SEARCH);
        }
    }

    private static boolean editing(Attendance existing) {
        return existing != null;
    }

    /**
     * Fills the course dropdown with the selected student's enrolments.
     *
     * <p>This is the same list the service validates against, so the dropdown
     * cannot offer a course the save would then reject.
     */
    private void loadEnrolledCourses(JComboBox<String> studentCombo,
                                     JComboBox<String> courseCombo, List<Course> all) {
        Object selected = studentCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        String studentId = idBeforeDash(String.valueOf(selected));
        courseCombo.setEnabled(false);
        UiErrors.runAsync(this, "Could not list the courses",
                () -> services.attendance().findEnrollableCourses(studentId),
                enrolled -> {
                    java.util.Set<String> ids = new java.util.LinkedHashSet<>();
                    for (Course course : enrolled) {
                        ids.add(course.getCourseId());
                    }
                    courseCombo.removeAllItems();
                    for (Course course : all) {
                        if (ids.contains(course.getCourseId())) {
                            courseCombo.addItem(course.getCourseId() + " — "
                                    + course.getDisplayName());
                        }
                    }
                    if (courseCombo.getItemCount() == 0) {
                        courseCombo.addItem("— not enrolled in any course —");
                    }
                    if (courseCombo.getItemCount() > 0) {
                        courseCombo.setSelectedIndex(0);
                    }
                    courseCombo.setEnabled(true);
                },
                () -> {
                    courseCombo.setEnabled(true);
                    courseCombo.removeAllItems();
                    courseCombo.addItem("— could not load the courses —");
                });
    }

    private String courseNameOf(String courseId) {
        Course course = courses.get(courseId);
        return course == null ? courseId : course.getDisplayName();
    }

    private static String idBeforeDash(String option) {
        int dash = option.indexOf(" — ");
        return dash < 0 ? option : option.substring(0, dash);
    }

    // ------------------------------------------------------------------
    // Course summary
    // ------------------------------------------------------------------

    /**
     * Shows how a course is going overall.
     *
     * <p>Worth having because the per-student rows cannot answer it: a course
     * can have every student regular and still be one withdrawal away from
     * a problem, and the eligible/meeting split says whether the denominator
     * is even meaningful yet.
     */
    private void showCourseSummary() {
        String courseId = table.selectedValue(COURSE_ID_COLUMN);
        if (courseId == null) {
            UiErrors.showWarning(this, "No course selected",
                    "Select a row in the table, then choose Course summary.");
            return;
        }
        UiErrors.runAsync(this, "Could not summarise the course",
                () -> services.attendance().summariseCourse(courseId),
                summary -> {
                    String message = "Attendance summary for " + courseId + "\n\n"
                            + UiSupport.count(summary.recorded(), "record", "records")
                            + " recorded.\n"
                            + UiSupport.count(summary.eligible(), "student", "students")
                            + " have had at least one class held.\n"
                            + UiSupport.count(summary.meeting(), "of them meet", "of them meet")
                            + " the " + UiSupport.number(
                            com.academic.management.util.Constants.ATTENDANCE_REQUIRED_PERCENT)
                            + "% requirement.\n\n"
                            + "A student with no classes held yet is excluded from the"
                            + " percentage, because the attendance is not yet known.";
                    UiErrors.info(this, "Course summary", message);
                });
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    private void deleteSelected() {
        String studentId = table.selectedValue(STUDENT_ID_COLUMN);
        String courseId = table.selectedValue(COURSE_ID_COLUMN);
        if (studentId == null || courseId == null) {
            UiErrors.showWarning(this, "No attendance record selected",
                    "Select a record in the table, then choose Delete.");
            return;
        }
        if (!UiErrors.confirmDetailed(this, "Delete attendance",
                "Delete the attendance record for " + studentId + " in " + courseId
                        + "?\n\nThe result for the same course, if any, is kept.")) {
            return;
        }
        UiErrors.runAsync(this, "Could not delete the attendance record", () -> {
            services.attendance().delete(studentId, courseId);
            return Boolean.TRUE;
        }, deleted -> {
            table.clearSelection();
            setStatus("Deleted the attendance record for " + studentId + " / " + courseId
                    + ".");
            reload();
        });
    }
}
