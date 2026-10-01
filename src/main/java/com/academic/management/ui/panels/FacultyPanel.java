package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.model.Faculty;
import com.academic.management.model.FacultySearchCriteria;
import com.academic.management.model.Gender;
import com.academic.management.service.FacultyService;
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

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every faculty member, with add, edit and delete.
 *
 * <h2>Why deleting a lecturer needs a warning</h2>
 * A course carries a foreign key to the faculty member teaching it, and the
 * database refuses to drop a lecturer who is still assigned. Rather than
 * letting the user discover that by typing and reading a SQL error,
 * {@link FacultyService#countCoursesTaught(String)} is used to explain the
 * position before they try, and the same check runs again in the service.
 */
public final class FacultyPanel extends PagePanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = AppLogger.getLogger(FacultyPanel.class);

    private static final String[] HEADERS = {
            "Faculty ID", "Name", "Email", "Department", "Designation",
            "Gender", "Date of birth", "Phone", "Office", "Joined", "Service"};

    private static final int[] WIDTHS = {100, 170, 200, 130, 140, 80, 120, 110, 110, 110, 90};

    private static final int ID_COLUMN = 0;
    private static final int SERVICE_COLUMN = 10;

    private static final String ALL_DEPARTMENTS = "All departments";
    private static final String ALL_DESIGNATIONS = "All designations";

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model).numeric(SERVICE_COLUMN);

    private final JTextField searchField = Theme.styleInput(new JTextField());
    private final JComboBox<String> departmentFilter = new JComboBox<>();
    private final JComboBox<String> designationFilter = new JComboBox<>();

    private String searchTerm = "";
    private String department = "";
    private String designation = "";

    /**
     * Filter values read by the worker and applied by the renderer.
     *
     * <p>Written on the worker thread and read on the event thread, which is
     * safe because the write happens before the load's result is posted to
     * the event queue - that posting is what publishes it.
     */
    private transient List<String> departmentOptions = new ArrayList<>();
    private transient List<String> designationOptions = new ArrayList<>();

    public FacultyPanel(ServiceRegistry services) {
        super("Faculty", "Add, edit and remove faculty records, and see who teaches what.");
        this.services = services;

        JButton add = Theme.button("Add faculty", Theme.PRIMARY);
        add.addActionListener(event -> openAddForm());
        addToolbarButton(add);

        JButton edit = Theme.secondaryButton("Edit selected");
        edit.addActionListener(event -> editSelected());
        addToolbarButton(edit);

        JButton taught = Theme.secondaryButton("Courses taught");
        taught.addActionListener(event -> showCoursesTaught());
        addToolbarButton(taught);

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
        searchField.setToolTipText("Type to filter. Matches the id and the name.");
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

        configureFilter(departmentFilter, ALL_DEPARTMENTS, value -> {
            department = ALL_DEPARTMENTS.equals(value) ? "" : value;
        });
        configureFilter(designationFilter, ALL_DESIGNATIONS, value -> {
            designation = ALL_DESIGNATIONS.equals(value) ? "" : value;
        });

        JPanel filters = row(Theme.caption("Search:"), searchField,
                Theme.caption("Department:"), departmentFilter,
                Theme.caption("Designation:"), designationFilter, glue());
        filters.setBorder(Theme.padding(0, 0, Theme.GAP, 0));

        JPanel body = new JPanel(new BorderLayout(0, Theme.GAP));
        body.setOpaque(false);
        body.add(filters, BorderLayout.NORTH);
        body.add(table.scroll(), BorderLayout.CENTER);
        return body;
    }

    private void configureFilter(JComboBox<String> combo, String allLabel,
                                 java.util.function.Consumer<String> onChange) {
        combo.setFont(Theme.BODY);
        combo.setPreferredSize(new Dimension(170, 30));
        combo.addItem(allLabel);
        combo.addActionListener(event -> {
            onChange.accept(String.valueOf(combo.getSelectedItem()));
            reload();
        });
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
        List<Faculty> members = search();
        loadFilterOptions();
        List<String[]> rows = new ArrayList<>(members.size());
        for (Faculty faculty : members) {
            rows.add(new String[]{
                    faculty.getFacultyId(),
                    faculty.getName(),
                    faculty.getEmail(),
                    faculty.getDepartment(),
                    faculty.getDesignation(),
                    UiSupport.gender(faculty.getGender()),
                    UiSupport.ageFrom(faculty.getDateOfBirth()),
                    UiSupport.orNone(faculty.getPhone()),
                    UiSupport.orNone(faculty.getOfficeLocation()),
                    UiSupport.date(faculty.getJoiningDate()),
                    faculty.getJoiningDate() == null
                            ? UiSupport.NONE
                            : faculty.getYearsOfService() + " yr"});
        }
        return rows;
    }

    /**
     * Runs the search.
     *
     * <p>Id and name are queried separately and merged, for the same reason
     * as on the students page: the criteria combine with AND, so a single
     * object carrying the term in both fields would exclude anyone who
     * matches only one of them.
     */
    private List<Faculty> search() throws AppException {
        List<Faculty> matched;
        if (searchTerm.isEmpty()) {
            matched = new ArrayList<>(services.faculty().search(FacultySearchCriteria.all()));
        } else {
            Map<String, Faculty> merged = new LinkedHashMap<>();
            for (Faculty faculty : services.faculty().search(
                    FacultySearchCriteria.byId(searchTerm))) {
                merged.put(faculty.getFacultyId(), faculty);
            }
            for (Faculty faculty : services.faculty().search(
                    FacultySearchCriteria.byName(searchTerm))) {
                merged.putIfAbsent(faculty.getFacultyId(), faculty);
            }
            matched = new ArrayList<>(merged.values());
        }
        if (!department.isEmpty()) {
            String wanted = department;
            matched.removeIf(faculty -> !wanted.equals(faculty.getDepartment()));
        }
        if (!designation.isEmpty()) {
            String wanted = designation;
            matched.removeIf(faculty -> !wanted.equals(faculty.getDesignation()));
        }
        matched.sort(Comparator.comparing(Faculty::getName, String.CASE_INSENSITIVE_ORDER));
        return matched;
    }

    @Override
    protected void render(List<String[]> rows) {
        applyFilters();
        StringBuilder status = new StringBuilder(UiSupport.count(rows.size(),
                "faculty member", "faculty members"));
        if (!searchTerm.isEmpty()) {
            status.append(" matching '").append(searchTerm).append('\'');
        }
        setStatus(status.toString());
    }

    /**
     * Reads the values behind both dropdowns, on the worker thread.
     *
     * <p>A failure leaves the previous lists alone rather than failing the
     * load: the table has already reported the problem, and emptying the
     * filters would be a second confusing symptom of the same failure.
     */
    private void loadFilterOptions() {
        try {
            departmentOptions = new ArrayList<>(
                    services.faculty().findDistinctDepartments());
            designationOptions = new ArrayList<>(
                    services.faculty().findDistinctDesignations());
        } catch (AppException e) {
            LOGGER.log(Level.WARNING, "Could not refresh the faculty filters.", e);
        }
    }

    /**
     * Rebuilds the dropdowns, but only when a set has actually changed, so a
     * dropdown does not close under the user's cursor on every keystroke.
     */
    private void applyFilters() {
        refill(departmentFilter, ALL_DEPARTMENTS, departmentOptions);
        refill(designationFilter, ALL_DESIGNATIONS, designationOptions);
    }

    private void refill(JComboBox<String> combo, String allLabel, List<String> values) {
        java.util.LinkedHashSet<String> wanted = new java.util.LinkedHashSet<>();
        wanted.add(allLabel);
        wanted.addAll(values);
        java.util.LinkedHashSet<String> present = new java.util.LinkedHashSet<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            present.add(combo.getItemAt(i));
        }
        if (present.equals(wanted)) {
            return;
        }
        Object wasSelected = combo.getSelectedItem();
        combo.removeAllItems();
        for (String value : wanted) {
            combo.addItem(value);
        }
        combo.setSelectedItem(wanted.contains(String.valueOf(wasSelected))
                ? wasSelected : allLabel);
    }

    // ------------------------------------------------------------------
    // Create and edit
    // ------------------------------------------------------------------

    private void editSelected() {
        String id = table.selectedValue(ID_COLUMN);
        if (id == null) {
            UiErrors.showWarning(this, "No faculty member selected",
                    "Select a faculty member in the table, then choose Edit.");
            return;
        }
        UiErrors.runAsync(this, "Could not open the faculty record",
                () -> services.faculty().findById(id),
                existing -> showFacultyForm(existing, ""));
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
        UiErrors.runAsync(this, "Could not suggest a faculty id",
                () -> {
                    try {
                        return services.faculty().suggestNextId();
                    } catch (AppException e) {
                        LOGGER.log(Level.WARNING,
                                "Could not suggest a faculty id; leaving it blank.", e);
                        return "";
                    }
                },
                suggested -> showFacultyForm(null, suggested));
    }

    /**
     * @param suggestedId the id to offer on the add form, read on a worker
     */
    private void showFacultyForm(Faculty existing, String suggestedId) {
        boolean editing = existing != null;
        String lockedId = editing ? existing.getFacultyId() : null;

        FormDialog form = new FormDialog(windowOf(this),
                editing ? "Edit faculty" : "Add faculty",
                editing ? "Save changes" : "Add faculty",
                values -> {
                    Faculty faculty = buildFaculty(lockedId, values);
                    if (editing) {
                        services.faculty().update(faculty);
                    } else {
                        services.faculty().create(faculty);
                    }
                });

        if (editing) {
            form.addLockedField("Faculty ID", existing.getFacultyId());
        } else {
            form.addField("Faculty ID", suggestedId, 12, FormDialog.identifierRule());
        }
        form.addField("Name", editing ? existing.getName() : "", 26, FormDialog.requiredRule());
        form.addField("Email", editing ? existing.getEmail() : "", 26, FormDialog.emailRule());
        form.addDateField("Date of birth",
                editing ? UiSupport.dateForEditing(existing.getDateOfBirth()) : "01 Jan 1990",
                FormDialog.dateOfBirthRule());

        JComboBox<String> gender = form.addComboField("Gender", genderLabels());
        if (editing) {
            gender.setSelectedItem(existing.getGender().getLabel());
        }

        form.addField("Department", editing ? existing.getDepartment() : "", 22,
                FormDialog.requiredRule());
        form.addField("Designation",
                editing ? existing.getDesignation() : "Lecturer", 26, FormDialog.requiredRule());
        form.addOptionalField("Phone", plain(existing == null ? null : existing.getPhone()), 14);
        form.addOptionalField("Address", plain(existing == null ? null : existing.getAddress()), 30);
        form.addOptionalField("Office",
                plain(existing == null ? null : existing.getOfficeLocation()), 16);
        form.addDateField("Joining date",
                editing ? UiSupport.dateForEditing(existing.getJoiningDate())
                        : UiSupport.todayForEditing(),
                (label, text) -> UiSupport.parseFlexibleDate(label, text));

        form.showDialog();
        if (form.wasSaved()) {
            setStatus(editing ? "Updated " + lockedId + "." : "Added a new faculty member.");
            reload();
            refreshOtherPages(MainFrame.COURSES, MainFrame.SEARCH);
        }
    }

    private Faculty buildFaculty(String lockedId, Map<String, String> values)
            throws AppException {
        String id = lockedId != null ? lockedId : FormDialog.required(values, "Faculty ID");
        return new Faculty(
                id,
                FormDialog.required(values, "Name"),
                FormDialog.requiredDateOfBirth(values, "Date of birth"),
                genderFrom(values.get("Gender")),
                FormDialog.requiredEmail(values, "Email"),
                FormDialog.optionalPhone(values, "Phone"),
                FormDialog.optional(values, "Address"),
                FormDialog.required(values, "Department"),
                FormDialog.required(values, "Designation"),
                FormDialog.optional(values, "Office"),
                UiSupport.parseFlexibleDate("Joining date", values.get("Joining date")));
    }

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
    // Courses taught
    // ------------------------------------------------------------------

    /**
     * Lists the courses a faculty member teaches, on demand.
     *
     * <h2>Why this is a dialog and not a column</h2>
     * A course is many-to-many with a faculty member from the point of view
     * of this screen, and joining the list into the main table would either
     * multiply every faculty row by the number of courses they teach or leave
     * a count where the user needs the actual course codes. Showing them on
     * demand keeps the table one row per person and still gives the answer.
     */
    private void showCoursesTaught() {
        String id = table.selectedValue(ID_COLUMN);
        if (id == null) {
            UiErrors.showWarning(this, "No faculty member selected",
                    "Select a faculty member in the table, then choose Courses taught.");
            return;
        }
        UiErrors.runAsync(this, "Could not load the courses",
                () -> services.faculty().findCoursesTaught(id),
                courses -> {
                    StringBuilder text = new StringBuilder();
                    if (courses.isEmpty()) {
                        text.append(id).append(" is not teaching any course yet.");
                    } else {
                        text.append(id).append(" teaches ").append(courses.size())
                                .append(courses.size() == 1 ? " course:" : " courses:");
                        for (com.academic.management.model.Course course : courses) {
                            text.append("\n  •  ").append(course.getCourseCode())
                                    .append("  ").append(course.getCourseName())
                                    .append("  (Sem ").append(course.getSemester())
                                    .append(", ").append(UiSupport.credits(course.getCredits()))
                                    .append(')');
                        }
                    }
                    UiErrors.info(this, "Courses taught", text.toString());
                });
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    private void deleteSelected() {
        String id = table.selectedValue(ID_COLUMN);
        if (id == null) {
            UiErrors.showWarning(this, "No faculty member selected",
                    "Select a faculty member in the table, then choose Delete.");
            return;
        }
        // Two steps rather than one, because the confirmation is a Swing
        // dialog and a Swing dialog has to be shown on the event thread -
        // asking for it from inside the worker would show it off the event
        // thread, where it can deadlock against the thread that owns it.
        UiErrors.runAsync(this, "Could not read what deleting " + id + " would affect",
                () -> {
                    int taught = services.faculty().countCoursesTaught(id);
                    return taught == 0
                            ? "Delete faculty member " + id + "?\n\nThis cannot be undone."
                            : "Delete faculty member " + id + "?\n\n"
                                    + taught + (taught == 1 ? " course is" : " courses are")
                                    + " still assigned to them.\n\n"
                                    + "Reassign those courses on the Courses page first, or the "
                                    + "database will refuse the delete.\n\nThis cannot be undone.";
                },
                message -> {
                    if (UiErrors.confirmDetailed(this, "Delete faculty member", message)) {
                        deleteNow(id);
                    }
                });
    }

    private void deleteNow(String id) {
        UiErrors.runAsync(this, "Could not delete " + id,
                () -> {
                    services.faculty().delete(id);
                    return Boolean.TRUE;
                },
                deleted -> {
                    table.clearSelection();
                    setStatus("Deleted " + id + ".");
                    reload();
                    refreshOtherPages(MainFrame.COURSES, MainFrame.SEARCH);
                });
    }
}
