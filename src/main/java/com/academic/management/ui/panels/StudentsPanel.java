package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.model.Gender;
import com.academic.management.model.Student;
import com.academic.management.model.StudentSearchCriteria;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.service.StudentService;
import com.academic.management.ui.MainFrame;
import com.academic.management.ui.common.DataTable;
import com.academic.management.ui.common.DisplayTableModel;
import com.academic.management.ui.common.FormDialog;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.ui.common.UiSupport;
import com.academic.management.util.AppLogger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every student, with add, edit and delete.
 *
 * <h2>How the form is built</h2>
 * {@link #showStudentForm(Student)} builds both the add and the edit dialog.
 * They differ in exactly two ways - whether the id is editable, and whether
 * the service call is {@code create} or {@code update} - and having one
 * builder is what stops a field being added to the create form and forgotten
 * in the edit form, which is the usual way an "edit" silently drops a column.
 *
 * <h2>Why delete needs a real impact report</h2>
 * Deleting a student cascades to their enrolments and results, because those
 * rows mean nothing without them. That is correct behaviour, but it is not
 * what a user expects from a bare "are you sure?", so
 * {@link StudentService#describeDeletion(String)} supplies the exact counts
 * and the confirmation states them.
 */
public final class StudentsPanel extends PagePanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = AppLogger.getLogger(StudentsPanel.class);

    private static final String[] HEADERS = {
            "Student ID", "Name", "Email", "Department", "Semester", "Gender",
            "Date of birth", "Phone", "Admission date", "Years admitted"};

    private static final int[] WIDTHS = {100, 170, 200, 130, 80, 80, 110, 110, 120, 110};

    /** Columns used when reading a selected row. */
    private static final int ID_COLUMN = 0;
    private static final int NAME_COLUMN = 1;
    private static final int EMAIL_COLUMN = 2;
    private static final int DEPARTMENT_COLUMN = 3;
    private static final int SEMESTER_COLUMN = 4;

    private static final String ALL_DEPARTMENTS = "All departments";

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model)
            .fit(NAME_COLUMN, EMAIL_COLUMN, DEPARTMENT_COLUMN)
            .numeric(SEMESTER_COLUMN);

    private final JTextField searchField = Theme.styleInput(new JTextField());
    private final JComboBox<String> departmentFilter = Theme.styleSelect(new JComboBox<>());

    /**
     * The centre of the page, holding either the table or the empty state.
     *
     * <p>Held as a field rather than built inside {@code buildBody} because
     * {@link #showRows} has to swap its child on every render.
     */
    private final JPanel tableArea = new JPanel(new BorderLayout());

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

    public StudentsPanel(ServiceRegistry services) {
        super("Students", "Add, edit and remove student records. The search covers the id, "
                + "the name and the email address.");
        this.services = services;

        JButton add = Theme.button("Add student", Theme.PRIMARY);
        add.addActionListener(event -> openAddForm());
        addToolbarButton(add);

        JButton edit = Theme.secondaryButton("Edit selected");
        edit.addActionListener(event -> editSelected());
        addToolbarButton(edit);

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
        searchField.setPreferredSize(new Dimension(280, 30));
        searchField.setToolTipText("Type to filter. Matches the id, the name or the email.");
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

        departmentFilter.setPreferredSize(new Dimension(200, 30));
        departmentFilter.addItem(ALL_DEPARTMENTS);
        departmentFilter.addActionListener(event -> {
            Object selected = departmentFilter.getSelectedItem();
            department = ALL_DEPARTMENTS.equals(selected) ? "" : String.valueOf(selected);
            reload();
        });

        JPanel filters = row(filterLabel("Search:", searchField),
                filterLabel("Department:", departmentFilter), glue());
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
        List<Student> students = search();
        loadDepartmentOptions();
        List<String[]> rows = new ArrayList<>(students.size());
        for (Student student : students) {
            rows.add(new String[]{
                    student.getStudentId(),
                    student.getName(),
                    student.getEmail(),
                    student.getDepartment(),
                    String.valueOf(student.getSemester()),
                    UiSupport.gender(student.getGender()),
                    UiSupport.ageFrom(student.getDateOfBirth()),
                    UiSupport.orNone(student.getPhone()),
                    UiSupport.date(student.getAdmissionDate()),
                    student.getAdmissionDate() == null
                            ? UiSupport.NONE
                            : student.getYearsSinceAdmission() + " yr"});
        }
        return rows;
    }

    /**
     * Runs the search.
     *
     * <p>Id, name and email are searched as three separate queries and merged
     * by id, because the criteria fields combine with AND: one criteria
     * object carrying the term in both the id and the name would return
     * nothing for a student who matches only one of them, which is not what
     * "search students" means to a user. The department filter is then
     * applied to the merged result.
     */
    private List<Student> search() throws AppException {
        List<Student> matched;
        if (searchTerm.isEmpty()) {
            matched = new ArrayList<>(services.students().search(StudentSearchCriteria.all()));
        } else {
            Map<String, Student> merged = new LinkedHashMap<>();
            for (Student student : services.students().search(
                    StudentSearchCriteria.byId(searchTerm))) {
                merged.put(student.getStudentId(), student);
            }
            for (Student student : services.students().search(
                    StudentSearchCriteria.byName(searchTerm))) {
                merged.putIfAbsent(student.getStudentId(), student);
            }
            StudentSearchCriteria byEmail = new StudentSearchCriteria();
            byEmail.setName(searchTerm);
            byEmail.setIncludeEmailMatch(true);
            for (Student student : services.students().search(byEmail)) {
                merged.putIfAbsent(student.getStudentId(), student);
            }
            matched = new ArrayList<>(merged.values());
        }
        if (!department.isEmpty()) {
            String wanted = department;
            matched.removeIf(student -> !wanted.equals(student.getDepartment()));
        }
        matched.sort(Comparator.comparing(Student::getName, String.CASE_INSENSITIVE_ORDER));
        return matched;
    }

    @Override
    protected void render(List<String[]> rows) {
        applyDepartmentOptions();
        StringBuilder status = new StringBuilder();
        status.append(UiSupport.count(rows.size(), "student", "students"));
        if (!searchTerm.isEmpty()) {
            status.append(" matching '").append(searchTerm).append('\'');
        }
        if (!department.isEmpty()) {
            status.append(" in ").append(department);
        }
        setStatus(status.toString());
        showRows(tableArea, table.scroll(), rows.size(),
                rows.isEmpty() && searchTerm.isEmpty() && department.isEmpty()
                        ? "No students yet. Use Add student to create the first record."
                        : "No student matches the current filters.");
    }

    /**
     * Reads the departments that exist, on the worker thread.
     *
     * <p>The dropdown is built from what is stored rather than from a fixed
     * list of possible department names, which would be mostly entries that
     * match nothing.
     *
     * <p>A failure here leaves the previous list in place rather than
     * failing the load: the table itself has already reported the problem,
     * and emptying the filter would be a second, confusing symptom of the
     * same failure.
     */
    private void loadDepartmentOptions() {
        try {
            departmentOptions = new ArrayList<>(services.students().findDistinctDepartments());
        } catch (AppException e) {
            LOGGER.log(Level.WARNING, "Could not refresh the department filter.", e);
        }
    }

    /**
     * Rebuilds the department dropdown from the options read by the worker.
     *
     * <p>Rebuilding only when the set has genuinely changed is what stops the
     * dropdown closing itself while the user is using it - a dropdown that is
     * cleared and refilled on every keystroke elsewhere in the page is
     * impossible to operate.
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
        for (String name : wanted) {
            departmentFilter.addItem(name);
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
            UiErrors.showWarning(this, "No student selected",
                    "Select a student in the table, then choose Edit.");
            return;
        }
        UiErrors.runAsync(this, "Could not open the student",
                () -> services.students().findById(id),
                existing -> showStudentForm(existing, ""));
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
        UiErrors.runAsync(this, "Could not suggest a student id",
                () -> {
                    try {
                        return services.students().suggestNextId();
                    } catch (AppException e) {
                        LOGGER.log(Level.WARNING,
                                "Could not suggest a student id; leaving it blank.", e);
                        return "";
                    }
                },
                suggested -> showStudentForm(null, suggested));
    }

    /**
     * Shows the add or the edit form.
     *
     * @param existing the record to edit, or null to create a new one
     * @param suggestedId the id to offer on the add form, read on a worker
     */
    private void showStudentForm(Student existing, String suggestedId) {
        boolean editing = existing != null;
        String lockedId = editing ? existing.getStudentId() : null;

        FormDialog form = new FormDialog(windowOf(this),
                editing ? "Edit student" : "Add student",
                editing ? "Save changes" : "Add student",
                values -> {
                    Student student = buildStudent(lockedId, values);
                    if (editing) {
                        services.students().update(student);
                    } else {
                        services.students().create(student);
                    }
                });

        if (editing) {
            form.addLockedField("Student ID", existing.getStudentId());
        } else {
            form.addField("Student ID", suggestedId, 12, FormDialog.identifierRule());
        }
        form.addField("Name", editing ? existing.getName() : "", 26, FormDialog.requiredRule());
        form.addField("Email", editing ? existing.getEmail() : "", 26, FormDialog.emailRule());
        form.addDateField("Date of birth",
                editing ? UiSupport.dateForEditing(existing.getDateOfBirth())
                        : "01 Jan 2004",
                FormDialog.dateOfBirthRule());

        JComboBox<String> gender = form.addComboField("Gender", genderLabels());
        if (editing) {
            gender.setSelectedItem(existing.getGender().getLabel());
        }

        form.addField("Department", editing ? existing.getDepartment() : "", 22,
                FormDialog.requiredRule());
        form.addField("Semester", editing ? String.valueOf(existing.getSemester()) : "1", 6,
                FormDialog.semesterRule());
        form.addOptionalField("Phone", plain(existing == null ? null : existing.getPhone()), 14);
        form.addOptionalField("Address", plain(existing == null ? null : existing.getAddress()), 30);
        form.addDateField("Admission date",
                editing ? UiSupport.dateForEditing(existing.getAdmissionDate())
                        : UiSupport.todayForEditing(),
                (label, text) -> UiSupport.parseFlexibleDate(label, text));
        form.addOptionalField("Guardian contact",
                plain(existing == null ? null : existing.getGuardianContact()), 14);
        form.addNote("A date may be typed as 15 Jan 2004, 2004-01-15, or today.");

        form.showDialog();
        if (form.wasSaved()) {
            setStatus(editing ? "Updated " + lockedId + "." : "Added a new student.");
            reload();
            refreshOtherPages(MainFrame.COURSES, MainFrame.ENROLLMENTS,
                    MainFrame.ATTENDANCE, MainFrame.RESULTS, MainFrame.PROFILES,
                    MainFrame.SEARCH);
        }
    }

    /**
     * Builds a student from the form's values.
     *
     * <p>The model constructor does the real validation - a blank name, a
     * malformed email, an out-of-range semester - so this method only
     * converts strings into the types the constructor wants.
     */
    private Student buildStudent(String lockedId, Map<String, String> values)
            throws AppException {
        String id = lockedId != null ? lockedId : FormDialog.required(values, "Student ID");
        return new Student(
                id,
                FormDialog.required(values, "Name"),
                FormDialog.requiredDateOfBirth(values, "Date of birth"),
                genderFrom(values.get("Gender")),
                FormDialog.requiredEmail(values, "Email"),
                FormDialog.optionalPhone(values, "Phone"),
                FormDialog.optional(values, "Address"),
                FormDialog.required(values, "Department"),
                FormDialog.requiredSemester(values, "Semester"),
                UiSupport.parseFlexibleDate("Admission date", values.get("Admission date")),
                FormDialog.optional(values, "Guardian contact"));
    }

    /** An existing optional value for the form, without the em dash. */
    private static String plain(String value) {
        return value == null || UiSupport.NONE.equals(value) ? "" : value;
    }

    private static List<String> genderLabels() {
        List<String> labels = new ArrayList<>();
        for (Gender gender : Gender.values()) {
            labels.add(gender.getLabel());
        }
        return labels;
    }

    private static Gender genderFrom(String label) {
        return Arrays.stream(Gender.values())
                .filter(gender -> gender.getLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElseGet(() -> Gender.fromLabel(label));
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    private void deleteSelected() {
        String id = table.selectedValue(ID_COLUMN);
        if (id == null) {
            UiErrors.showWarning(this, "No student selected",
                    "Select a student in the table, then choose Delete.");
            return;
        }
        // Two steps rather than one, because the confirmation is a Swing
        // dialog and a Swing dialog has to be shown on the event thread -
        // asking for it from inside the worker would show it off the event
        // thread, where it can deadlock against the thread that owns it.
        UiErrors.runAsync(this, "Could not read what deleting " + id + " would affect",
                () -> services.students().describeDeletion(id).describe(),
                impact -> {
                    if (UiErrors.confirmDetailed(this, "Delete student", impact)) {
                        deleteNow(id);
                    }
                });
    }

    private void deleteNow(String id) {
        UiErrors.runAsync(this, "Could not delete " + id,
                () -> {
                    services.students().delete(id);
                    return Boolean.TRUE;
                },
                deleted -> {
                    table.clearSelection();
                    setStatus("Deleted " + id + ".");
                    reload();
                    refreshOtherPages(MainFrame.COURSES, MainFrame.ENROLLMENTS,
                            MainFrame.ATTENDANCE, MainFrame.RESULTS, MainFrame.PROFILES,
                            MainFrame.SEARCH);
                });
    }
}
