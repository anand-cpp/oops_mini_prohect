package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.model.AcademicProfile;
import com.academic.management.model.CoursePerformance;
import com.academic.management.model.Grade;
import com.academic.management.model.IdName;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.common.DataTable;
import com.academic.management.ui.common.DisplayTableModel;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.ui.common.UiSupport;
import com.academic.management.util.Constants;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * One student's whole academic record, calculated rather than stored.
 *
 * <h2>Why this screen exists</h2>
 * Every other page shows one table of one entity. This one answers the
 * question none of them can: how is this particular student doing, across
 * every course at once. The figures come from
 * {@link com.academic.management.model.AcademicProfile}, which the service
 * builds from the enrolments, attendance and results - so there is no
 * summary row in the database to fall out of date with the rows it
 * summarises.
 *
 * <h2>Why the standing verdict is shown with its reason</h2>
 * "Not in good standing" on its own is not actionable. The banner under the
 * cards quotes {@link AcademicProfile#getStandingSummary()}, which names
 * the reason - a course needing re-examination, or attendance below the
 * requirement - so a tutor can act on it without reading the source.
 *
 * <h2>Why a button rather than opening the profile on selection</h2>
 * Building a profile is several queries. Refreshing on every dropdown
 * movement would make the page feel slow and would fire a query for each
 * arrow key press, so the student is chosen and then opened.
 */
public final class ProfilesPanel extends PagePanel {

    private static final long serialVersionUID = 1L;

    private static final String[] HEADERS = {
            "Course", "Code", "Sem", "Credits", "Faculty", "Enrolment",
            "Held", "Attended", "Attendance", "Result", "Grade", "Points", "Verdict"};

    private static final int[] WIDTHS = {200, 90, 60, 70, 150, 100, 60, 80, 90, 80, 60, 70, 90};

    private static final int CREDITS_COLUMN = 3;
    private static final int HELD_COLUMN = 6;
    private static final int ATTENDED_COLUMN = 7;
    private static final int POINTS_COLUMN = 11;
    private static final int VERDICT_COLUMN = 12;

    private static final String NO_STUDENT = "— no student chosen —";

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model)
            .numeric(CREDITS_COLUMN, HELD_COLUMN, ATTENDED_COLUMN, POINTS_COLUMN)
            .verdict(VERDICT_COLUMN);

    private final JComboBox<String> studentPicker = new JComboBox<>();
    private final JPanel cards = new JPanel(new GridLayout(1, 4, Theme.GAP, 0));
    private final JLabel standing = Theme.caption(" ");

    private String studentId = "";

    /**
     * The profile the current rows came from.
     *
     * <p>Written by {@link #load()} on the worker thread and read by
     * {@link #afterLoad()} on the event thread. Safe because the worker
     * finishes writing before {@code load} returns, and the result is
     * published to the event queue afterwards.
     */
    private transient AcademicProfile profile;

    public ProfilesPanel(ServiceRegistry services) {
        super("Academic Profiles", "One student's courses, attendance and results, with the"
                + " overall standing worked out from them.");
        this.services = services;

        JButton open = Theme.button("Open profile", Theme.PRIMARY);
        open.addActionListener(event -> openChosenProfile());
        addToolbarButton(open);

        JButton pickerRefresh = Theme.secondaryButton("Refresh student list");
        pickerRefresh.addActionListener(event -> loadPicker());
        addToolbarButton(pickerRefresh);

        JButton refresh = Theme.secondaryButton("Refresh");
        refresh.addActionListener(event -> reload());
        addActionButton(refresh);

        setContent(buildBody());
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildBody() {
        studentPicker.setFont(Theme.BODY);
        studentPicker.setPreferredSize(new Dimension(340, 30));
        studentPicker.addActionListener(event -> {
            studentId = idOfOption(studentPicker.getSelectedItem());
        });

        cards.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            cards.add(figureCard("—", " "));
        }

        JPanel banner = Theme.card();
        banner.setLayout(new BorderLayout());
        standing.setFont(Theme.BODY_BOLD);
        banner.add(standing, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, Theme.GAP));
        top.setOpaque(false);
        top.add(row(Theme.caption("Student:"), studentPicker,
                Theme.caption("Choose a student, then press Open profile.")),
                BorderLayout.NORTH);
        top.add(cards, BorderLayout.CENTER);
        top.add(banner, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, Theme.GAP));
        body.setOpaque(false);
        body.add(top, BorderLayout.NORTH);
        body.add(table.scroll(), BorderLayout.CENTER);
        return body;
    }

    /**
     * A summary figure: a big number, a caption and a note.
     *
     * <p>The three parts are separate so the number can be the large one
     * while the caption and note stay small - the number is what the eye
     * should land on first.
     */
    private static JComponent figureCard(String figure, String note) {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 2));
        card.setPreferredSize(new Dimension(190, 86));

        JLabel value = new JLabel(figure);
        value.setFont(Theme.H2);
        value.setForeground(Theme.TEXT);
        value.setBorder(Theme.padding(0, 0, 0, 0));

        card.add(value, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(Theme.caption(" "), BorderLayout.WEST);
        footer.add(Theme.caption(note), BorderLayout.EAST);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Builds the profile and the per-course rows.
     *
     * <p>The profile is built once and kept, because the summary cards, the
     * standing banner and the table all describe the same object. Building it
     * a second time for the cards would double the queries and risk the two
     * views showing figures from different moments.
     */
    @Override
    protected List<String[]> load() throws AppException {
        if (studentId.isEmpty()) {
            profile = null;
            return List.of();
        }
        profile = services.profiles().buildProfile(studentId);

        List<String[]> rows = new ArrayList<>();
        for (CoursePerformance course : profile.getCourses()) {
            rows.add(new String[]{
                    course.getCourseDisplayName(),
                    course.getCourseCode(),
                    String.valueOf(course.getCourseSemester()),
                    UiSupport.number(course.getCredits()),
                    UiSupport.orNone(course.getFacultyName()),
                    course.getEnrollmentStatus(),
                    String.valueOf(course.getClassesHeld()),
                    String.valueOf(course.getClassesAttended()),
                    UiSupport.percent(course.getAttendancePercentage()),
                    UiSupport.percent(course.getResultPercentage()),
                    UiSupport.orNone(course.getGrade()),
                    course.getGradePoint() == null
                            ? UiSupport.NONE : UiSupport.number(course.getGradePoint()),
                    verdictOf(course)});
        }
        return rows;
    }

    /** Pass or Fail for one course, or a dash while it is still ungraded. */
    private static String verdictOf(CoursePerformance course) {
        if (!course.hasResult()) {
            return UiSupport.NONE;
        }
        return Boolean.TRUE.equals(course.getPassed()) ? "Pass" : "Fail";
    }

    @Override
    protected void render(List<String[]> rows) {
        if (studentId.isEmpty()) {
            setStatus("Choose a student from the list to see their profile.");
            return;
        }
        setStatus(profile.getStudent().getName() + " — "
                + UiSupport.count(rows.size(), "course", "courses")
                + ", generated " + UiSupport.date(profile.getGeneratedAt()));
    }

    /**
     * Applies the four headline figures and the standing verdict.
     *
     * <p>Runs after {@link #render} so the cards describe the rows that were
     * just painted, and sets the status line last so its wording survives.
     */
    @Override
    protected void afterLoad() {
        cards.removeAll();
        if (profile == null) {
            for (int i = 0; i < 4; i++) {
                cards.add(figureCard("—", " "));
            }
            standing.setText("No profile open. Choose a student to begin.");
            standing.setForeground(Theme.TEXT_MUTED);
            cards.revalidate();
            cards.repaint();
            return;
        }
        cards.add(figureCard(UiSupport.percent(profile.getOverallPercentage()),
                profile.getOverallGrade() == null
                        ? "not graded yet" : "Grade " + profile.getOverallGrade().getCode()));
        cards.add(figureCard(UiSupport.number(profile.getGpa()),
                "GPA of " + UiSupport.number(Grade.A_PLUS.getGradePoint())));
        cards.add(figureCard(UiSupport.percent(profile.getOverallAttendancePercentage()),
                (int) Constants.ATTENDANCE_REQUIRED_PERCENT + "% required"));
        cards.add(figureCard(UiSupport.number(profile.getTotalCredits()),
                UiSupport.count(profile.getCourseCount(), "course", "courses")));
        cards.revalidate();
        cards.repaint();

        standing.setText(profile.getStudent().getName() + " — " + profile.getStandingSummary());
        standing.setForeground(profile.isInGoodStanding() ? Theme.SUCCESS : Theme.DANGER);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void openChosenProfile() {
        String id = studentId;
        if (id.isEmpty()) {
            UiErrors.showWarning(this, "No student chosen",
                    "Choose a student from the dropdown first.");
            return;
        }
        if (loadPickerIfEmpty()) {
            return;
        }
        reload();
    }

    /**
     * Fills the dropdown, keeping the current choice if it is still there.
     *
     * <p>Not part of {@link #load()}: the dropdown is an input to the load,
     * and rebuilding it from inside the load that consumes it would discard
     * the selection on every refresh.
     */
    private void loadPicker() {
        UiErrors.runAsync(this, "Could not load the students", () -> {
            List<IdName> students = services.students().findSummaries();
            return new Picker(students, studentId);
        }, this::applyPicker);
    }

    private void applyPicker(Picker picker) {
        if (picker.students().isEmpty()) {
            studentPicker.removeAllItems();
            studentPicker.addItem(NO_STUDENT);
            studentPicker.setEnabled(false);
            studentId = "";
            setStatus("There are no students yet. Add one on the Students page.");
            return;
        }
        studentPicker.setEnabled(true);
        studentPicker.removeAllItems();
        for (IdName summary : picker.students()) {
            studentPicker.addItem(summary.id() + " — " + summary.label());
        }
        select(picker.previous());
        setStatus("Choose a student, then press Open profile.");
    }

    /** Restores the previous choice, or falls back to the first student. */
    private void select(String id) {
        if (id != null && !id.isEmpty()) {
            for (int index = 0; index < studentPicker.getItemCount(); index++) {
                if (id.equals(idOfOption(studentPicker.getItemAt(index)))) {
                    studentPicker.setSelectedIndex(index);
                    return;
                }
            }
        }
        studentPicker.setSelectedIndex(0);
    }

    /**
     * Fills the list on the way in, so the page is never empty on arrival.
     *
     * @return true when the list was empty and is being filled now, in which
     *         case the caller should not start a load against nothing
     */
    private boolean loadPickerIfEmpty() {
        if (studentPicker.getItemCount() > 0) {
            return false;
        }
        loadPicker();
        return true;
    }

    private record Picker(List<IdName> students, String previous) {
    }

    /**
     * The id part of a dropdown entry.
     *
     * <p>Entries are built as {@code "STU001 — Aisha Khan"}, so the id is
     * everything before the first dash. {@code indexOf} rather than
     * {@code split} because the name may itself contain one.
     */
    private static String idOfOption(Object option) {
        if (option == null) {
            return "";
        }
        String text = String.valueOf(option);
        int dash = text.indexOf(" — ");
        String id = dash < 0 ? text : text.substring(0, dash);
        return NO_STUDENT.equals(id) ? "" : id.trim();
    }
}
