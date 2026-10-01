package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.model.Grade;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.MainFrame;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiSupport;
import com.academic.management.util.Constants;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The landing page: how much of everything there is, plus the rules the rest
 * of the application works by.
 *
 * <h2>What this screen answers</h2>
 * "Is the data there?" - the six counts tell an operator whether the schema
 * was loaded and the sample data is present. "How is it doing?" - the two
 * averages summarise academic performance. And "by what rules?" - the
 * grading scale and the attendance requirement, which explain most of the
 * verdicts shown on other pages and are otherwise buried in code.
 *
 * <h2>Why the six counts are one band and not six cards</h2>
 * They were six identically sized white cards with six different accent
 * colours, which made the page read as six unrelated widgets competing with
 * the cards below them - and the colours themselves said nothing: a purple
 * for faculty and a red for results implied a status that the numbers do not
 * carry. They are now a single navy band across the top, because they are one
 * answer ("how much is there") rather than six, with the same accent on every
 * one and the figure itself the only thing large on the screen.
 *
 * <h2>Why the figures come from one call</h2>
 * {@link ServiceRegistry#loadDashboard()} fetches all eight in one go. Eight
 * separate calls from this panel would read the database eight times, and
 * the totals would be taken at eight different moments - so a record added
 * while the page was loading could make the counts disagree with each other.
 */
public final class DashboardPanel extends PagePanel {

    private static final long serialVersionUID = 1L;

    /** The six headline counts, in the order they are shown. */
    private static final int FIGURE_COUNT = 6;

    private final transient ServiceRegistry services;
    private final transient MainFrame frame;

    private final JPanel figureRow = new JPanel(new GridLayout(1, FIGURE_COUNT, 0, 0));
    private final JLabel resultAverage = Theme.caption(" ");
    private final JLabel attendanceAverage = Theme.caption(" ");

    /**
     * The figures from the most recent load.
     *
     * <p>Written on the worker thread and read on the event thread, which is
     * safe because the value is published by the event-queue post that
     * carries the load result.
     */
    private transient ServiceRegistry.Dashboard figures;

    public DashboardPanel(ServiceRegistry services, MainFrame frame) {
        super("Dashboard", "Live figures read from MySQL whenever this page is shown.");
        this.services = services;
        this.frame = frame;

        JButton manageStudents = Theme.button("Manage students", Theme.PRIMARY);
        manageStudents.addActionListener(event -> frame.showPage("students"));
        addToolbarButton(manageStudents);

        JButton enrol = Theme.secondaryButton("Enrol a student");
        enrol.addActionListener(event -> frame.showPage("enrollments"));
        addToolbarButton(enrol);

        JButton recordAttendance = Theme.secondaryButton("Record attendance");
        recordAttendance.addActionListener(event -> frame.showPage("attendance"));
        addActionButton(recordAttendance);

        JButton publishResults = Theme.secondaryButton("Publish results");
        publishResults.addActionListener(event -> frame.showPage("results"));
        addActionButton(publishResults);

        JButton refresh = Theme.secondaryButton("Refresh");
        refresh.addActionListener(event -> reload());
        addActionButton(refresh);

        setContent(buildBody());
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JComponent buildBody() {
        figureRow.setBackground(Theme.NAVY);
        figureRow.setBorder(Theme.padding(Theme.PAD_LARGE, 0, Theme.PAD_LARGE, 0));
        // Placeholders keep the grid's column count correct before the first
        // load finishes; load() replaces them with real figures.
        for (int i = 0; i < FIGURE_COUNT; i++) {
            figureRow.add(new JPanel());
        }

        JPanel body = new JPanel(new BorderLayout(0, Theme.PAD));
        body.setOpaque(false);
        body.add(figureRow, BorderLayout.NORTH);

        // The cards below the band are sized to their content and centred in
        // what is left, rather than stretched down the viewport - a two-line
        // card made three hundred pixels tall by a grid reads as a mistake,
        // and it pushed the grading scale off the bottom of the screen.
        JPanel below = new JPanel(new GridBagLayout());
        below.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, Theme.PAD, 0);
        below.add(buildAverages(), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        below.add(buildRules(), constraints);

        body.add(below, BorderLayout.CENTER);
        return body;
    }

    /**
     * The two figures that are calculated rather than counted.
     *
     * <p>They sit next to each other because they are the two numbers that
     * decide whether a student is in good standing, and seeing them as a pair
     * makes "marks are fine, attendance is not" immediately legible.
     *
     * <p>The figure is one step down from the counts in the band above,
     * because it is a summary of a summary: the counts are what is on screen,
     * these are what it adds up to.
     */
    private JComponent buildAverages() {
        JPanel row = new JPanel(new GridLayout(1, 2, Theme.GAP, 0));
        row.setOpaque(false);
        row.add(averageCard("Average result", resultAverage,
                "The mean of every published result, out of "
                        + UiSupport.number(Constants.TOTAL_MARKS) + " marks."));
        row.add(averageCard("Average attendance", attendanceAverage,
                "Classes attended over classes held, across every course."));
        return row;
    }

    private JComponent averageCard(String heading, JLabel value, String explanation) {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, Theme.GAP));
        card.add(Theme.sectionLabel(heading), BorderLayout.NORTH);
        value.setFont(Theme.H2);
        value.setForeground(Theme.TEXT);
        card.add(value, BorderLayout.CENTER);
        card.add(Theme.caption(explanation), BorderLayout.SOUTH);
        return card;
    }

    /**
     * The two rules that decide the verdicts everywhere else.
     *
     * <p>Rendered from {@link Grade} and {@link Constants} rather than typed
     * out, so changing the grading policy is reflected here without anyone
     * remembering to edit this card.
     */
    private JComponent buildRules() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, Theme.GAP));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(Theme.sectionLabel("Grading scale and requirements"), BorderLayout.WEST);
        heading.add(Theme.caption("The rules applied to every grade in this application."),
                BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        JPanel rules = new JPanel(new GridLayout(3, 1, 0, 4));
        rules.setOpaque(false);
        rules.add(Theme.ruleLabel(gradeScale()));
        rules.add(Theme.ruleLabel("A course is passed at "
                + UiSupport.number(Constants.PASS_PERCENTAGE) + "%. Internal marks are out of "
                + UiSupport.number(Constants.MAX_INTERNAL_MARKS) + " and external marks out of "
                + UiSupport.number(Constants.MAX_EXTERNAL_MARKS) + "."));
        rules.add(Theme.ruleLabel("Attendance of at least "
                + UiSupport.number(Constants.ATTENDANCE_REQUIRED_PERCENT)
                + "% counts as regular, and a student must be regular in every course to be"
                + " in good standing."));
        card.add(rules, BorderLayout.CENTER);
        return card;
    }

    /** The grade bands as one line, ordered from highest to lowest. */
    private static String gradeScale() {
        StringBuilder scale = new StringBuilder();
        for (Grade grade : Grade.orderedByThreshold()) {
            if (scale.length() > 0) {
                scale.append("     ");
            }
            scale.append(grade.getCode()).append(" ≥ ")
                    .append(UiSupport.number(grade.getMinimumPercentage())).append('%');
        }
        return scale.toString();
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Reads the figures on a worker thread.
     *
     * <p>Nothing here touches a Swing component. The eight figures are
     * stashed in {@link #figures} and the six counts are also returned as
     * rows, so the base class's row count has something to report.
     */
    @Override
    protected List<String[]> load() throws AppException {
        figures = services.loadDashboard();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Students", String.valueOf(figures.students())});
        rows.add(new String[]{"Faculty", String.valueOf(figures.faculty())});
        rows.add(new String[]{"Courses", String.valueOf(figures.courses())});
        rows.add(new String[]{"Enrolments", String.valueOf(figures.enrolments())});
        rows.add(new String[]{"Attendance records", String.valueOf(figures.attendanceRecords())});
        rows.add(new String[]{"Results", String.valueOf(figures.resultRecords())});
        return rows;
    }

    /** Applies the stashed figures, on the event thread. */
    @Override
    protected void afterLoad() {
        ServiceRegistry.Dashboard loaded = figures;
        if (loaded == null) {
            return;
        }
        figureRow.removeAll();
        figureRow.add(figureCell("Students", loaded.students(), 0));
        figureRow.add(figureCell("Faculty", loaded.faculty(), 1));
        figureRow.add(figureCell("Courses", loaded.courses(), 1));
        figureRow.add(figureCell("Enrolments", loaded.enrolments(), 1));
        figureRow.add(figureCell("Attendance", loaded.attendanceRecords(), 1));
        figureRow.add(figureCell("Results", loaded.resultRecords(), 1));
        figureRow.revalidate();
        figureRow.repaint();

        // Boxed so the one-decimal overload of percent is chosen rather than
        // the whole-number one, which is meant for inline figures.
        resultAverage.setText(loaded.resultRecords() == 0
                ? "No results yet"
                : UiSupport.percent(Double.valueOf(loaded.averageResult())));
        attendanceAverage.setText(loaded.attendanceRecords() == 0
                ? "No attendance yet"
                : UiSupport.percent(Double.valueOf(loaded.averageAttendance())));

        setStatus("Read from MySQL at " + UiSupport.timestamp(LocalDateTime.now()));
    }

    /**
     * One headline figure, on the navy band.
     *
     * <p>The accent bar is the same on all six. It marks the band as the
     * application's own summary rather than as six separate categories, and
     * it is the one blue in the palette that clears 3:1 against navy.
     *
     * @param position the cell's index, used only to draw the divider that
     *                 separates it from the cell before
     */
    private JComponent figureCell(String caption, long value, int position) {
        JPanel cell = new JPanel(new BorderLayout(Theme.GAP, 0));
        cell.setBackground(Theme.NAVY);
        cell.setOpaque(true);
        cell.setBorder(position == 0
                ? Theme.padding(0, Theme.PAD_LARGE, 0, Theme.GAP)
                : BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.NAVY_HOVER),
                        Theme.padding(0, Theme.PAD_LARGE, 0, Theme.GAP)));

        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        JLabel label = Theme.captionOnDark(caption, Theme.TEXT_ON_NAVY_MUTED);
        JLabel figure = new JLabel(String.valueOf(value));
        figure.setFont(Theme.H1);
        figure.setForeground(Theme.TEXT_ON_NAVY);
        text.add(label, BorderLayout.NORTH);
        text.add(figure, BorderLayout.CENTER);

        cell.add(Theme.accentBar(Theme.ACCENT_ON_NAVY), BorderLayout.WEST);
        cell.add(text, BorderLayout.CENTER);
        return cell;
    }
}