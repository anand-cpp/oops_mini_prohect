package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.exception.ValidationException;
import com.academic.management.model.Course;
import com.academic.management.model.Grade;
import com.academic.management.model.IdName;
import com.academic.management.model.Result;
import com.academic.management.service.ResultService;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.MainFrame;
import com.academic.management.ui.common.DataTable;
import com.academic.management.ui.common.DisplayTableModel;
import com.academic.management.ui.common.FormDialog;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.ui.common.UiSupport;
import com.academic.management.util.Constants;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
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
 * Results, published per student per course and graded by the shared policy.
 *
 * <h2>Why the form shows the grade as it is typed</h2>
 * The grade is not typed in - it is derived from the two marks by
 * {@link com.academic.management.model.GradingPolicy}. That makes it very
 * easy to enter 38 out of 40 internal and not notice that the total lands
 * below the pass mark. {@link ResultService#preview} exists to compute the
 * outcome without saving, so the form displays the total, the percentage and
 * the letter grade live, and the operator sees the consequence of a change
 * before committing it. The preview is the same construction the save uses,
 * so what is shown is what will be stored.
 *
 * <h2>Why there is no editable grade column</h2>
 * A grade the user could type would be a second source of truth that could
 * disagree with the marks. The grade column is rendered from the policy and
 * is read-only, which is the same reason the whole model layer exists.
 */
public final class ResultsPanel extends PagePanel {

    private static final long serialVersionUID = 1L;

    private static final String[] HEADERS = {
            "Student ID", "Student", "Course ID", "Course", "Internal", "External",
            "Total", "Percentage", "Grade", "Points", "Verdict", "Remarks"};

    private static final int[] WIDTHS = {100, 170, 100, 220, 90, 90, 80, 100, 70, 80, 90, 160};

    private static final int STUDENT_ID_COLUMN = 0;
    private static final int COURSE_ID_COLUMN = 2;
    private static final int INTERNAL_COLUMN = 4;
    private static final int EXTERNAL_COLUMN = 5;
    private static final int TOTAL_COLUMN = 6;
    private static final int POINTS_COLUMN = 9;
    private static final int VERDICT_COLUMN = 10;

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model)
            .numeric(INTERNAL_COLUMN, EXTERNAL_COLUMN, TOTAL_COLUMN, POINTS_COLUMN)
            .verdict(VERDICT_COLUMN);

    private final JTextField searchField = Theme.styleInput(new JTextField());
    private final JComboBox<String> gradeFilter = new JComboBox<>();

    private String searchTerm = "";
    private String grade = "";

    private transient Map<String, String> studentNames = Map.of();

    public ResultsPanel(ServiceRegistry services) {
        super("Results", "Publish the internal and external marks for an enrolled student. "
                + "The total, the percentage and the grade are worked out for you.");
        this.services = services;

        JButton publish = Theme.button("Publish result", Theme.PRIMARY);
        publish.addActionListener(event -> showResultForm(null));
        addToolbarButton(publish);

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

        gradeFilter.setFont(Theme.BODY);
        gradeFilter.setPreferredSize(new Dimension(150, 30));
        gradeFilter.addItem("All grades");
        for (Grade value : Grade.orderedByThreshold()) {
            gradeFilter.addItem(value.getCode());
        }
        gradeFilter.addActionListener(event -> {
            Object selected = gradeFilter.getSelectedItem();
            grade = selected == null || "All grades".equals(selected) ? "" : String.valueOf(selected);
            reload();
        });

        JPanel filters = row(Theme.caption("Search:"), searchField,
                Theme.caption("Grade:"), gradeFilter,
                Theme.caption("Pass mark: " + UiSupport.number(Constants.PASS_PERCENTAGE) + "%"),
                glue());
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
        studentNames = loadStudentNames();
        List<String[]> rows = new ArrayList<>();
        for (ResultService.ResultRow record : services.results().findAllRows()) {
            if (!grade.isEmpty() && !grade.equals(record.gradeCode())) {
                continue;
            }
            if (!matchesSearch(record)) {
                continue;
            }
            rows.add(new String[]{
                    record.studentId(),
                    record.studentName(),
                    record.courseId(),
                    record.courseName(),
                    UiSupport.marks(record.internalMarks()),
                    UiSupport.marks(record.externalMarks()),
                    UiSupport.marks(record.totalMarks()),
                    UiSupport.percent(Double.valueOf(record.percentage())),
                    record.gradeCode(),
                    UiSupport.number(record.gradePoint()),
                    UiSupport.passFail(record.pass()),
                    UiSupport.orNone(record.remarks())});
        }
        rows.sort((left, right) -> {
            int byStudent = left[0].compareToIgnoreCase(right[0]);
            return byStudent != 0 ? byStudent : left[2].compareToIgnoreCase(right[2]);
        });
        return rows;
    }

    private boolean matchesSearch(ResultService.ResultRow record) {
        if (searchTerm.isEmpty()) {
            return true;
        }
        String needle = searchTerm.toLowerCase(Locale.ENGLISH);
        return record.studentId().toLowerCase(Locale.ENGLISH).contains(needle)
                || record.studentName().toLowerCase(Locale.ENGLISH).contains(needle)
                || record.courseId().toLowerCase(Locale.ENGLISH).contains(needle)
                || record.courseName().toLowerCase(Locale.ENGLISH).contains(needle);
    }

    private Map<String, String> loadStudentNames() throws AppException {
        Map<String, String> names = new LinkedHashMap<>();
        for (IdName summary : services.students().findSummaries()) {
            names.put(summary.id(), summary.label());
        }
        return names;
    }

    @Override
    protected void render(List<String[]> rows) {
        long passed = rows.stream()
                .filter(row -> "Pass".equals(row[VERDICT_COLUMN]))
                .count();
        StringBuilder status = new StringBuilder(
                UiSupport.count(rows.size(), "result", "results"));
        if (!rows.isEmpty()) {
            status.append(" — ").append(passed).append(" passed, ")
                    .append(rows.size() - passed).append(" failed");
        }
        if (!searchTerm.isEmpty()) {
            status.append(", matching '").append(searchTerm).append('\'');
        }
        setStatus(status.toString());
    }

    // ------------------------------------------------------------------
    // Publish and edit
    // ------------------------------------------------------------------

    private void editSelected() {
        String studentId = table.selectedValue(STUDENT_ID_COLUMN);
        String courseId = table.selectedValue(COURSE_ID_COLUMN);
        if (studentId == null || courseId == null) {
            UiErrors.showWarning(this, "No result selected",
                    "Select a result in the table, then choose Edit.");
            return;
        }
        UiErrors.runAsync(this, "Could not open the result",
                () -> services.results().findByStudentAndCourse(studentId, courseId),
                this::showResultForm);
    }

    private void showResultForm(Result existing) {
        String lockedStudent = existing == null ? null : existing.getStudentId();
        String lockedCourse = existing == null ? null : existing.getCourseId();

        UiErrors.runAsync(this, "Could not open the results form",
                () -> services.students().findSummaries(),
                students -> showResultDialog(students, existing, lockedStudent, lockedCourse));
    }

    private void showResultDialog(List<IdName> students, Result existing,
                                  String lockedStudent, String lockedCourse) {
        boolean editing = existing != null;
        if (!editing && students.isEmpty()) {
            UiErrors.showWarning(this, "No students yet",
                    "Add a student on the Students page before publishing a result.");
            return;
        }
        List<String> studentOptions = new ArrayList<>();
        for (IdName summary : students) {
            studentOptions.add(summary.id() + " — " + summary.label());
        }

        FormDialog form = new FormDialog(windowOf(this),
                editing ? "Edit result" : "Publish result",
                editing ? "Save changes" : "Publish",
                values -> services.results().publishOn(
                        studentIdOf(values, lockedStudent),
                        courseIdOf(values, lockedCourse),
                        FormDialog.requiredDouble(values, "Internal marks"),
                        FormDialog.requiredDouble(values, "External marks"),
                        UiSupport.parseFlexibleDate("Result date", values.get("Result date")),
                        FormDialog.optional(values, "Remarks")));

        JComboBox<String> studentCombo;
        JComboBox<String> courseCombo;
        if (editing) {
            form.addLockedField("Student", existing.getStudentId());
            form.addLockedField("Course", existing.getCourseId());
            studentCombo = null;
            courseCombo = null;
        } else {
            studentCombo = form.addComboField("Student", studentOptions);
            courseCombo = form.addComboField("Course", new ArrayList<>(), choiceRule());
            form.addNote("Only the courses this student is enrolled on and that have not"
                    + " been graded yet are offered.");
            studentCombo.addActionListener(event -> {
                if (form.isShowing()) {
                    loadGradableCourses(studentCombo, courseCombo);
                }
            });
        }

        // The live grade readout. Updated on every keystroke so the operator
        // sees the outcome of the marks before pressing Publish.
        JLabel readout = new JLabel(" ");
        readout.setFont(Theme.H3);
        readout.setForeground(Theme.TEXT);
        form.addReadout(readout);

        form.addField("Internal marks",
                editing ? UiSupport.number(existing.getInternalMarks())
                        : UiSupport.number(Constants.MAX_INTERNAL_MARKS / 2),
                FormDialog.internalMarksRule());
        form.addField("External marks",
                editing ? UiSupport.number(existing.getExternalMarks())
                        : UiSupport.number(Constants.MAX_EXTERNAL_MARKS / 2),
                FormDialog.externalMarksRule());
        form.addDateField("Result date",
                editing ? UiSupport.dateForEditing(existing.getResultDate())
                        : UiSupport.todayForEditing(),
                (label, text) -> UiSupport.parseFlexibleDate(label, text));
        form.addOptionalField("Remarks", editing ? orEmpty(existing.getRemarks()) : "", 40);

        Runnable updateReadout = () -> previewInto(readout, form);
        form.onAnyChange(updateReadout);
        updateReadout.run();

        if (courseCombo != null) {
            loadGradableCourses(studentCombo, courseCombo);
        }

        form.showDialog();
        if (form.wasSaved()) {
            setStatus(editing ? "Result updated." : "Result published.");
            reload();
            refreshOtherPages(MainFrame.PROFILES, MainFrame.SEARCH);
        }
    }

    private static String orEmpty(String value) {
        return value == null || UiSupport.NONE.equals(value) ? "" : value;
    }

    /**
     * Rejects a dropdown entry that is a message rather than a choice.
     *
     * <p>Two messages can appear in the course dropdown, and both have to be
     * rejected by the form's own rule. "— no ungraded course —" is the resting
     * state when there is nothing to grade; "Loading the courses…" is there
     * for the moment before the list arrives. The plain required rule would
     * accept either sentence as a valid answer, and the save would then fail
     * with an unknown course id - or, worse, be offered with the save button
     * already live while the real list was still on its way.
     */
    private static FormDialog.Rule choiceRule() {
        return (label, text) -> {
            if (text == null || text.isBlank() || text.startsWith("—")
                    || LOADING_COURSES.equals(text)) {
                throw new ValidationException(label, "Choose a value from the list.");
            }
        };
    }

    /**
     * The student this form is for: the fixed one when editing, otherwise
     * the id chosen from the dropdown.
     */
    private static String studentIdOf(Map<String, String> values, String locked)
            throws AppException {
        return locked != null ? locked : idBeforeDash(FormDialog.required(values, "Student"));
    }

    /** The course, in the same terms as {@link #studentIdOf}. */
    private static String courseIdOf(Map<String, String> values, String locked)
            throws AppException {
        return locked != null ? locked : idBeforeDash(FormDialog.required(values, "Course"));
    }

    /**
     * Shows what the entered marks would produce.
     *
     * <p>Calls the service's own {@code preview}, which is the same
     * construction the save uses, so what the readout shows is what will be
     * stored. A calculation error - half a mark, an out-of-range value -
     * leaves the readout showing the last valid outcome and lets the form's
     * own rules explain the problem, rather than replacing the readout with a
     * validation message nobody asked for while typing.
     */
    private void previewInto(JLabel readout, FormDialog form) {
        Map<String, String> values = form.readValues();
        try {
            double internal = FormDialog.requiredDouble(values, "Internal marks");
            double external = FormDialog.requiredDouble(values, "External marks");
            Result preview = services.results().preview(null, null, internal, external);
            readout.setText("Total " + UiSupport.marks(preview.getTotalMarks())
                    + " of " + UiSupport.number(Constants.TOTAL_MARKS)
                    + "   " + UiSupport.percent(preview.getPercentage())
                    + "   Grade " + preview.getGradeCode()
                    + "   " + UiSupport.passFail(preview.isPass()));
            readout.setForeground(preview.isPass() ? Theme.SUCCESS : Theme.DANGER);
        } catch (AppException | NumberFormatException e) {
            readout.setText("Enter both marks to see the grade.");
            readout.setForeground(Theme.TEXT_MUTED);
        }
    }

    /**
     * Fills the course dropdown with the courses this student can still be
     * graded on.
     *
     * <p>Enrolled courses minus the ones that already have a result.
     * {@code findGradableCourses} returns every course the student is
     * enrolled on, including the ones already graded, so the exclusion has to
     * be made here - otherwise the dropdown offers a course whose result
     * exists, and publishing again silently replaces it. Both queries run on
     * the worker thread.
     */
    private void loadGradableCourses(JComboBox<String> studentCombo,
                                     JComboBox<String> courseCombo) {
        Object selected = studentCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        String studentId = idBeforeDash(String.valueOf(selected));
        courseCombo.setEnabled(false);
        courseCombo.removeAllItems();
        courseCombo.addItem(LOADING_COURSES);

        UiErrors.runAsync(this, "Could not list the courses",
                () -> gradableCourseLabels(studentId),
                options -> setOptions(courseCombo, options),
                () -> {
                    courseCombo.setEnabled(true);
                    courseCombo.removeAllItems();
                    courseCombo.addItem(NO_COURSE);
                });
    }

    /**
     * The course options for a student, as label strings.
     *
     * <p>Excluding the already-graded courses is the service's job, not this
     * screen's: {@code findGradableCourses} is defined as the courses a result
     * may still be entered for, and filtering again here would leave two
     * places to keep in step.
     *
     * @return the courses still to be graded, or a single
     *         {@link #NO_COURSE} entry when there are none
     */
    private List<String> gradableCourseLabels(String studentId) throws AppException {
        List<String> labels = new ArrayList<>();
        for (Course course : services.results().findGradableCourses(studentId)) {
            labels.add(course.getCourseId() + " — " + course.getCourseName());
        }
        if (labels.isEmpty()) {
            return List.of(NO_COURSE);
        }
        return labels;
    }

    /** Shown in the course dropdown when there is nothing left to grade. */
    private static final String NO_COURSE = "— no ungraded course —";

    /** Shown in the course dropdown while the real list is on its way. */
    private static final String LOADING_COURSES = "Loading the courses…";

    /**
     * Replaces a dropdown's contents, keeping the first entry selected.
     *
     * <p>Replacing the model rather than calling {@code removeAllItems} means
     * the combo raises its selection event once, so the form re-validates
     * against the new contents instead of reporting a problem about an entry
     * that has just gone.
     */
    private static void setOptions(JComboBox<String> combo, List<String> options) {
        combo.setModel(new javax.swing.DefaultComboBoxModel<>(
                options.toArray(new String[0])));
        combo.setEnabled(true);
        if (combo.getItemCount() > 1) {
            combo.setSelectedIndex(0);
        }
        combo.revalidate();
        combo.repaint();
    }

    private static String idBeforeDash(String option) {
        int dash = option.indexOf(" — ");
        return dash < 0 ? option : option.substring(0, dash);
    }

    // ------------------------------------------------------------------
    // Course summary
    // ------------------------------------------------------------------

    private void showCourseSummary() {
        String courseId = table.selectedValue(COURSE_ID_COLUMN);
        if (courseId == null) {
            UiErrors.showWarning(this, "No course selected",
                    "Select a row in the table, then choose Course summary.");
            return;
        }
        UiErrors.runAsync(this, "Could not summarise the course",
                () -> services.results().summariseCourse(courseId),
                summary -> {
                    String message = "Result summary for " + courseId + "\n\n"
                            + UiSupport.count(summary.students(), "student", "students")
                            + " graded, " + summary.passed() + " passed.\n"
                            + "Pass rate: " + UiSupport.percent(
                            Double.valueOf(summary.passRate())) + "\n"
                            + "Class average: " + UiSupport.percent(
                            Double.valueOf(summary.averagePercent())) + "\n"
                            + "Highest: " + UiSupport.percent(
                            Double.valueOf(summary.highestPercent()));
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
            UiErrors.showWarning(this, "No result selected",
                    "Select a result in the table, then choose Delete.");
            return;
        }
        if (!UiErrors.confirm(this, "Delete result",
                "Delete the result for " + studentId + " in " + courseId
                        + "?\n\nThe student stays enrolled and the attendance for the course"
                        + " is kept.")) {
            return;
        }
        UiErrors.runAsync(this, "Could not delete the result", () -> {
            services.results().delete(studentId, courseId);
            return Boolean.TRUE;
        }, deleted -> {
            table.clearSelection();
            setStatus("Deleted the result for " + studentId + " / " + courseId + ".");
            reload();
        });
    }
}
