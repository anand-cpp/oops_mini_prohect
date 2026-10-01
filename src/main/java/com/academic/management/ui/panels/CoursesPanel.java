package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.model.Course;
import com.academic.management.model.CourseSearchCriteria;
import com.academic.management.model.Faculty;
import com.academic.management.model.IdName;
import com.academic.management.service.CourseService;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.MainFrame;
import com.academic.management.ui.common.DataTable;
import com.academic.management.ui.common.DisplayTableModel;
import com.academic.management.ui.common.FormDialog;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.ui.common.UiSupport;
import com.academic.management.util.AppLogger;
import com.academic.management.util.Constants;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every course, with add, edit, delete and faculty assignment.
 *
 * <h2>Why faculty assignment is its own action</h2>
 * A course's lecturer is the one field that has to reference another table,
 * and it changes on a completely different rhythm from the rest - a lecturer
 * takes over a course, a course runs for one semester. Making it a dropdown
 * inside the add/edit form would mean every unrelated edit to a course also
 * rewrites the lecturer, which is how a course quietly ends up assigned to
 * whoever happened to be selected. Assigning it on its own also lets the
 * screen show who is teaching what, which is the question the action exists
 * to answer.
 *
 * <h2>Why credits are validated here as well as in the model</h2>
 * The {@link Course} constructor already rejects credits outside
 * 0..{@code MAX_CREDITS}, but the live form rule means the field turns red
 * while the user is typing rather than after they press Save. The check is
 * duplicated deliberately: the model is the authority, the rule is the
 * feedback.
 */
public final class CoursesPanel extends PagePanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = AppLogger.getLogger(CoursesPanel.class);

    private static final String[] HEADERS = {
            "Course ID", "Code", "Course name", "Credits", "Department", "Semester",
            "Taught by", "Enrolled"};

    private static final int[] WIDTHS = {100, 100, 240, 80, 130, 90, 180, 90};

    private static final int ID_COLUMN = 0;
    private static final int CREDITS_COLUMN = 3;
    private static final int ENROLLED_COLUMN = 7;

    private static final String ALL_DEPARTMENTS = "All departments";
    private static final String UNASSIGNED = "— not assigned —";

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model)
            .numeric(CREDITS_COLUMN, ENROLLED_COLUMN);

    private final JTextField searchField = Theme.styleInput(new JTextField());
    private final JComboBox<String> departmentFilter = new JComboBox<>();

    private String searchTerm = "";
    private String department = "";

    /**
     * Departments that exist, read by the worker and applied by the renderer.
     *
     * <p>Written on the worker thread and read on the event thread, which is
     * safe because the write happens before the load's result is posted to
     * the event queue - that posting is what publishes it.
     */
    private transient List<String> departmentOptions = new ArrayList<>();

    /** Faculty lookup, so the table can show a name rather than an id. */
    private transient Map<String, String> facultyNames = Map.of();

    public CoursesPanel(ServiceRegistry services) {
        super("Courses", "Add, edit and remove courses, and assign the faculty member who "
                + "teaches each one.");
        this.services = services;

        JButton add = Theme.button("Add course", Theme.PRIMARY);
        add.addActionListener(event -> openAddForm());
        addToolbarButton(add);

        JButton edit = Theme.secondaryButton("Edit selected");
        edit.addActionListener(event -> editSelected());
        addToolbarButton(edit);

        JButton assign = Theme.secondaryButton("Assign faculty");
        assign.addActionListener(event -> showAssignForm());
        addToolbarButton(assign);

        JButton delete = Theme.dangerButton("Delete selected");
        delete.addActionListener(event -> deleteSelected());
        addToolbarButton(delete);

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
        searchField.setToolTipText("Type to filter. Matches the id, the code and the name.");
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

        departmentFilter.setFont(Theme.BODY);
        departmentFilter.setPreferredSize(new Dimension(180, 30));
        departmentFilter.addItem(ALL_DEPARTMENTS);
        departmentFilter.addActionListener(event -> {
            Object selected = departmentFilter.getSelectedItem();
            department = ALL_DEPARTMENTS.equals(selected) ? "" : String.valueOf(selected);
            reload();
        });

        JPanel filters = row(Theme.caption("Search:"), searchField,
                Theme.caption("Department:"), departmentFilter, glue());
        filters.setBorder(Theme.padding(0, 0, Theme.GAP, 0));

        JPanel body = new JPanel(new BorderLayout(0, Theme.GAP));
        body.setOpaque(false);
        body.add(filters, BorderLayout.NORTH);
        body.add(table.scroll(), BorderLayout.CENTER);
        return body;
    }

    private void searchChanged() {
        searchTerm = searchField.getText().trim();
        reload();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    @Override
    protected List<String[]> load() throws AppException {
        facultyNames = loadFacultyNames();
        loadDepartmentOptions();
        List<Course> courses = search();
        List<String[]> rows = new ArrayList<>(courses.size());
        for (Course course : courses) {
            String lecturer = course.hasFaculty()
                    ? facultyNames.getOrDefault(course.getFacultyId(), course.getFacultyId())
                    : UiSupport.NONE;
            rows.add(new String[]{
                    course.getCourseId(),
                    course.getCourseCode(),
                    course.getCourseName(),
                    UiSupport.number(course.getCredits()),
                    course.getDepartment(),
                    String.valueOf(course.getSemester()),
                    lecturer,
                    String.valueOf(services.courses().countEnrolled(course.getCourseId()))});
        }
        return rows;
    }

    /**
     * Resolves faculty ids to names once per load.
     *
     * <p>Fetching the lecturer for each course separately would be one query
     * per row; building the lookup once is a single query for the whole
     * table. An id the lookup does not have is shown as the id, so a course
     * assigned to a deleted record degrades to something identifiable rather
     * than to a blank.
     */
    private Map<String, String> loadFacultyNames() throws AppException {
        Map<String, String> names = new LinkedHashMap<>();
        for (IdName summary : services.faculty().findSummaries()) {
            names.put(summary.id(), summary.label());
        }
        return names;
    }

    /**
     * Runs the search, querying id, code and name separately and merging.
     *
     * <p>A course is findable by any of the three, and the criteria fields
     * combine with AND, so one object carrying the term in all three would
     * match nothing.
     */
    private List<Course> search() throws AppException {
        List<Course> matched;
        if (searchTerm.isEmpty()) {
            matched = new ArrayList<>(services.courses().search(CourseSearchCriteria.all()));
        } else {
            Map<String, Course> merged = new LinkedHashMap<>();
            for (Course course : services.courses().search(
                    CourseSearchCriteria.byId(searchTerm))) {
                merged.put(course.getCourseId(), course);
            }
            for (Course course : services.courses().search(
                    CourseSearchCriteria.byCode(searchTerm))) {
                merged.putIfAbsent(course.getCourseId(), course);
            }
            for (Course course : services.courses().search(
                    CourseSearchCriteria.byName(searchTerm))) {
                merged.putIfAbsent(course.getCourseId(), course);
            }
            matched = new ArrayList<>(merged.values());
        }
        if (!department.isEmpty()) {
            String wanted = department;
            matched.removeIf(course -> !wanted.equals(course.getDepartment()));
        }
        matched.sort(Comparator.comparing(Course::getCourseCode, String.CASE_INSENSITIVE_ORDER));
        return matched;
    }

    @Override
    protected void render(List<String[]> rows) {
        applyDepartmentOptions();
        StringBuilder status = new StringBuilder(
                UiSupport.count(rows.size(), "course", "courses"));
        if (!searchTerm.isEmpty()) {
            status.append(" matching '").append(searchTerm).append('\'');
        }
        setStatus(status.toString());
    }

    /**
     * Reads the departments that exist, on the worker thread.
     *
     * <p>A failure leaves the previous list in place rather than failing the
     * load: the table has already reported the problem, and emptying the
     * filter would be a second confusing symptom of the same failure.
     */
    private void loadDepartmentOptions() {
        try {
            departmentOptions = new ArrayList<>(
                    services.courses().findDistinctDepartments());
        } catch (AppException e) {
            LOGGER.log(Level.WARNING, "Could not refresh the department filter.", e);
        }
    }

    /**
     * Rebuilds the department dropdown, but only when the set has actually
     * changed, so it does not close under the user's cursor on every
     * keystroke.
     */
    private void applyDepartmentOptions() {
        java.util.LinkedHashSet<String> wanted = new java.util.LinkedHashSet<>();
        wanted.add(ALL_DEPARTMENTS);
        wanted.addAll(departmentOptions);
        java.util.LinkedHashSet<String> present = new java.util.LinkedHashSet<>();
        for (int i = 0; i < departmentFilter.getItemCount(); i++) {
            present.add(departmentFilter.getItemAt(i));
        }
        if (present.equals(wanted)) {
            return;
        }
        Object wasSelected = departmentFilter.getSelectedItem();
        departmentFilter.removeAllItems();
        for (String value : wanted) {
            departmentFilter.addItem(value);
        }
        departmentFilter.setSelectedItem(wanted.contains(String.valueOf(wasSelected))
                ? wasSelected : ALL_DEPARTMENTS);
    }

    // ------------------------------------------------------------------
    // Create and edit
    // ------------------------------------------------------------------

    private void editSelected() {
        String id = table.selectedValue(ID_COLUMN);
        if (id == null) {
            UiErrors.showWarning(this, "No course selected",
                    "Select a course in the table, then choose Edit.");
            return;
        }
        UiErrors.runAsync(this, "Could not open the course",
                () -> services.courses().findById(id),
                existing -> showCourseForm(existing, ""));
    }

    /**
     * Opens the add form, having first asked the service for an id to offer.
     *
     * <p>The suggestion is read on a worker because it is a query: asking
     * for it while building the form would put a database round trip on the
     * event thread, which is what makes a Swing window stop repainting.
     *
     * <p>A failure is deliberately swallowed. The suggestion is only a
     * convenience - the field is editable and the service still refuses a
     * duplicate - so an empty field and a log line are a better outcome than
     * refusing to open the form at all.
     */
    private void openAddForm() {
        UiErrors.runAsync(this, "Could not suggest a course id",
                () -> {
                    try {
                        return services.courses().suggestNextId();
                    } catch (AppException e) {
                        LOGGER.log(Level.WARNING,
                                "Could not suggest a course id; leaving it blank.", e);
                        return "";
                    }
                },
                suggested -> showCourseForm(null, suggested));
    }

    /**
     * @param suggestedId the id to offer on the add form, read on a worker
     */
    private void showCourseForm(Course existing, String suggestedId) {
        boolean editing = existing != null;
        String lockedId = editing ? existing.getCourseId() : null;

        FormDialog form = new FormDialog(windowOf(this),
                editing ? "Edit course" : "Add course",
                editing ? "Save changes" : "Add course",
                values -> {
                    Course course = buildCourse(lockedId, values);
                    if (editing) {
                        services.courses().update(course);
                    } else {
                        services.courses().create(course);
                    }
                });

        if (editing) {
            form.addLockedField("Course ID", existing.getCourseId());
        } else {
            form.addField("Course ID", suggestedId, 12, FormDialog.identifierRule());
        }
        form.addField("Course code", editing ? existing.getCourseCode() : "", 12,
                FormDialog.requiredRule());
        form.addField("Course name", editing ? existing.getCourseName() : "", 30,
                FormDialog.requiredRule());
        form.addField("Credits", editing ? UiSupport.number(existing.getCredits()) : "3", 6,
                (label, text) -> Validator.requireMarks(label, text, Constants.MAX_CREDITS));
        form.addField("Department", editing ? existing.getDepartment() : "", 22,
                FormDialog.requiredRule());
        form.addField("Semester", editing ? String.valueOf(existing.getSemester()) : "1", 6,
                FormDialog.semesterRule());
        form.addOptionalField("Description",
                plain(existing == null ? null : existing.getDescription()), 40);
        form.addNote("Credits must be between 0 and " + UiSupport.number(Constants.MAX_CREDITS)
                + ". Use Assign faculty to choose who teaches the course.");

        form.showDialog();
        if (form.wasSaved()) {
            setStatus(editing ? "Updated " + lockedId + "." : "Added a new course.");
            reload();
            refreshOtherPages(MainFrame.ENROLLMENTS, MainFrame.ATTENDANCE,
                    MainFrame.RESULTS, MainFrame.PROFILES, MainFrame.SEARCH);
        }
    }

    private Course buildCourse(String lockedId, Map<String, String> values) throws AppException {
        String id = lockedId != null ? lockedId : FormDialog.required(values, "Course ID");
        // The lecturer is deliberately not part of this form; it is set
        // through assignFaculty and carried over here.
        return new Course(
                id,
                FormDialog.required(values, "Course code"),
                FormDialog.required(values, "Course name"),
                FormDialog.requiredDouble(values, "Credits"),
                null,
                FormDialog.required(values, "Department"),
                FormDialog.requiredSemester(values, "Semester"),
                FormDialog.optional(values, "Description"));
    }

    private static String plain(String value) {
        return value == null || UiSupport.NONE.equals(value) ? "" : value;
    }

    // ------------------------------------------------------------------
    // Faculty assignment
    // ------------------------------------------------------------------

    /**
     * Assigns a lecturer to the selected course.
     *
     * <p>The dropdown offers every faculty member plus an explicit "not
     * assigned" entry, so unassigning is reachable without deleting and
     * recreating the course. The option list is built from the summaries the
     * service provides rather than from a free-text field, which is what
     * makes an invalid faculty id impossible to enter.
     */
    private void showAssignForm() {
        String courseId = table.selectedValue(ID_COLUMN);
        if (courseId == null) {
            UiErrors.showWarning(this, "No course selected",
                    "Select a course in the table, then choose Assign faculty.");
            return;
        }
        UiErrors.runAsync(this, "Could not open the course", () -> {
            Course course = services.courses().findById(courseId);
            List<IdName> faculty = services.faculty().findSummaries();
            return new AssignmentSetup(course, faculty);
        }, this::showAssignDialog);
    }

    /** The course and the lecturer list the assignment dialog needs. */
    private record AssignmentSetup(Course course, List<IdName> faculty) {
    }

    private void showAssignDialog(AssignmentSetup setup) {
        List<String> options = new ArrayList<>();
        options.add(UNASSIGNED);
        for (IdName summary : setup.faculty()) {
            options.add(summary.id() + " — " + summary.label());
        }

        FormDialog form = new FormDialog(windowOf(this), "Assign faculty",
                "Assign",
                values -> {
                    String choice = FormDialog.required(values, "Faculty member");
                    String facultyId = UNASSIGNED.equals(choice)
                            ? null : choice.substring(0, choice.indexOf(' '));
                    services.courses().assignFaculty(setup.course().getCourseId(), facultyId);
                });

        form.addLockedField("Course", setup.course().getCourseCode() + " — "
                + setup.course().getCourseName());
        JComboBox<String> combo = form.addComboField("Faculty member", options);
        if (setup.course().hasFaculty()) {
            combo.setSelectedItem(setup.course().getFacultyId() + " — "
                    + facultyNames.getOrDefault(setup.course().getFacultyId(),
                    setup.course().getFacultyId()));
        } else {
            combo.setSelectedItem(UNASSIGNED);
        }
        form.addNote("Choosing “" + UNASSIGNED + "” clears the assignment without "
                + "changing anything else about the course.");

        form.showDialog();
        if (form.wasSaved()) {
            setStatus("Updated the faculty member for "
                    + setup.course().getCourseCode() + ".");
            reload();
            refreshOtherPages(MainFrame.FACULTY, MainFrame.SEARCH);
        }
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    private void deleteSelected() {
        String courseId = table.selectedValue(ID_COLUMN);
        if (courseId == null) {
            UiErrors.showWarning(this, "No course selected",
                    "Select a course in the table, then choose Delete.");
            return;
        }
        // Two steps rather than one, because the confirmation is a Swing
        // dialog and a Swing dialog has to be shown on the event thread -
        // asking for it from inside the worker would show it off the event
        // thread, where it can deadlock against the thread that owns it.
        UiErrors.runAsync(this, "Could not read what deleting " + courseId + " would affect",
                () -> {
                    int enrolled = services.courses().countEnrolled(courseId);
                    return enrolled == 0
                            ? "Delete course " + courseId + "?\n\nThis cannot be undone."
                            : "Delete course " + courseId + "?\n\n"
                                    + enrolled + (enrolled == 1 ? " student is" : " students are")
                                    + " enrolled in it.\n\n"
                                    + "Their enrolments, attendance and results for this course will"
                                    + " be removed too. Remove the enrolments first if you want to"
                                    + " keep them.\n\nThis cannot be undone.";
                },
                message -> {
                    if (UiErrors.confirmDetailed(this, "Delete course", message)) {
                        deleteNow(courseId);
                    }
                });
    }

    private void deleteNow(String courseId) {
        UiErrors.runAsync(this, "Could not delete " + courseId,
                () -> {
                    services.courses().delete(courseId);
                    return Boolean.TRUE;
                },
                deleted -> {
                    table.clearSelection();
                    setStatus("Deleted " + courseId + ".");
                    reload();
                    refreshOtherPages(MainFrame.FACULTY, MainFrame.ENROLLMENTS,
                            MainFrame.ATTENDANCE, MainFrame.RESULTS, MainFrame.PROFILES,
                            MainFrame.SEARCH);
                });
    }

    /** A course, for a screen that needs to name one in a confirmation. */
    static String describe(Course course) {
        return course.getCourseCode() + " — " + course.getCourseName();
    }
}
