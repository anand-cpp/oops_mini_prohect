package com.academic.management.ui;

import com.academic.management.model.User;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.ui.panels.AttendancePanel;
import com.academic.management.ui.panels.CoursesPanel;
import com.academic.management.ui.panels.DashboardPanel;
import com.academic.management.ui.panels.EnrollmentsPanel;
import com.academic.management.ui.panels.FacultyPanel;
import com.academic.management.ui.panels.ProfilesPanel;
import com.academic.management.ui.panels.ResultsPanel;
import com.academic.management.ui.panels.SearchPanel;
import com.academic.management.ui.panels.StudentsPanel;
import com.academic.management.util.AppLogger;
import com.academic.management.util.Constants;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The signed-in window: a navigation rail, a header, and one card per page.
 *
 * <h2>Why a CardLayout and not one window per page</h2>
 * Swapping visible components inside one frame is what keeps the unsaved
 * filter text, the scroll position and the selected row of a page alive
 * while the user visits another page and comes back. Opening a window per
 * page would lose all of that and would leave a trail of windows behind.
 *
 * <h2>How the rail works</h2>
 * The rail is a small list of custom-painted buttons rather than a
 * {@code JList}, because the selected state has to be a filled pill on the
 * navy background, and reaching for that with a list renderer means fighting
 * the cell border and focus painting on every theme.
 */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = AppLogger.getLogger(MainFrame.class);

    /**
     * Page keys, public because a page that changes shared data has to say
     * which other pages are now stale.
     */
    public static final String DASHBOARD = "dashboard";
    public static final String STUDENTS = "students";
    public static final String FACULTY = "faculty";
    public static final String COURSES = "courses";
    public static final String ENROLLMENTS = "enrollments";
    public static final String ATTENDANCE = "attendance";
    public static final String RESULTS = "results";
    public static final String PROFILES = "profiles";
    public static final String SEARCH = "search";

    private static final int RAIL_WIDTH = 232;
    private static final int HEADER_HEIGHT = 64;

    private final transient ServiceRegistry services;
    private final transient User user;
    private final transient CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final transient Map<String, PagePanel> pages = new LinkedHashMap<>();
    private final transient List<NavButton> navButtons = new ArrayList<>();

    private String current = DASHBOARD;

    public MainFrame(ServiceRegistry services, User user) {
        super(Constants.APP_NAME + " — " + user.getWelcomeName());
        this.services = services;
        this.user = user;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(Theme.WINDOW);
        setMinimumSize(new Dimension(1180, 720));
        setLocationRelativeTo(null);
        setIconImage(WindowIcons.appIcon());

        buildPages();
        setContentPane(buildLayout());
        showPage(DASHBOARD);
        installCloseHandling();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private void buildPages() {
        pages.put(DASHBOARD, new DashboardPanel(services, this));
        pages.put(STUDENTS, new StudentsPanel(services));
        pages.put(FACULTY, new FacultyPanel(services));
        pages.put(COURSES, new CoursesPanel(services));
        pages.put(ENROLLMENTS, new EnrollmentsPanel(services));
        pages.put(ATTENDANCE, new AttendancePanel(services));
        pages.put(RESULTS, new ResultsPanel(services));
        pages.put(PROFILES, new ProfilesPanel(services));
        pages.put(SEARCH, new SearchPanel(services));
    }

    private JPanel buildLayout() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAGE);
        root.add(buildRail(), BorderLayout.WEST);
        root.add(buildCentre(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildCentre() {
        JPanel centre = new JPanel(new BorderLayout());
        centre.setBackground(Theme.PAGE);
        centre.add(buildHeader(), BorderLayout.NORTH);
        content.setBackground(Theme.PAGE);
        pages.forEach((key, page) -> content.add(page, key));
        centre.add(content, BorderLayout.CENTER);
        return centre;
    }

    // ------------------------------------------------------------------
    // The navigation rail
    // ------------------------------------------------------------------

    private JPanel buildRail() {
        JPanel rail = new JPanel();
        rail.setBackground(Theme.NAVY);
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setPreferredSize(new Dimension(RAIL_WIDTH, 0));
        rail.setBorder(Theme.padding(0, 0, Theme.PAD, 0));

        JLabel brand = new JLabel("  Academic");
        brand.setFont(Theme.H3);
        brand.setForeground(Theme.TEXT_ON_NAVY);
        brand.setBorder(Theme.padding(Theme.PAD_LARGE, 0, 4, 0));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        rail.add(brand);

        JLabel subtitle = new JLabel("  Management Suite");
        subtitle.setFont(Theme.SMALL);
        subtitle.setForeground(new Color(0x76, 0x86, 0xA8));
        subtitle.setBorder(Theme.padding(0, 0, Theme.PAD, 0));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        rail.add(subtitle);

        addNavButton(rail, DASHBOARD, "Dashboard");
        addNavButton(rail, STUDENTS, "Students");
        addNavButton(rail, FACULTY, "Faculty");
        addNavButton(rail, COURSES, "Courses");
        addNavButton(rail, ENROLLMENTS, "Enrolments");
        addNavButton(rail, ATTENDANCE, "Attendance");
        addNavButton(rail, RESULTS, "Results");
        addNavButton(rail, PROFILES, "Academic Profiles");
        addNavButton(rail, SEARCH, "Search");

        // Pushes the account block to the bottom of the rail.
        rail.add(Box.createVerticalGlue());

        JPanel account = new JPanel();
        account.setBackground(new Color(0x1A, 0x24, 0x3A));
        account.setLayout(new BoxLayout(account, BoxLayout.Y_AXIS));
        account.setBorder(Theme.padding(Theme.PAD, Theme.PAD_LARGE, Theme.PAD, Theme.PAD_LARGE));
        account.setAlignmentX(Component.LEFT_ALIGNMENT);
        account.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JLabel name = new JLabel(user.getWelcomeName());
        name.setFont(Theme.BODY_BOLD);
        name.setForeground(Theme.TEXT_ON_NAVY);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        account.add(name);

        JLabel role = new JLabel(user.getRole().getLabel());
        role.setFont(Theme.SMALL);
        role.setForeground(new Color(0x76, 0x86, 0xA8));
        role.setBorder(Theme.padding(0, 0, Theme.GAP, 0));
        role.setAlignmentX(Component.LEFT_ALIGNMENT);
        account.add(role);

        JPanel accountActions = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.LEFT, 0, 0));
        accountActions.setOpaque(false);
        accountActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountActions.add(railAction("Change password", this::changePassword));
        accountActions.add(railAction("Sign out", this::signOut));
        account.add(accountActions);

        rail.add(account);
        return rail;
    }

    private void addNavButton(JPanel rail, String key, String label) {
        NavButton button = new NavButton(key, label);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.addActionListener(event -> showPage(key));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                if (!key.equals(current)) {
                    button.setHovered(true);
                    button.repaint();
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                button.setHovered(false);
                button.repaint();
            }
        });
        navButtons.add(button);
        rail.add(button);
    }

    private JButton railAction(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(Theme.SMALL);
        button.setForeground(new Color(0xA8, 0xB6, 0xD0));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(Theme.padding(4, 0, 4, Theme.GAP));
        button.addActionListener(event -> action.run());
        return button;
    }

    // ------------------------------------------------------------------
    // The header
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.CARD);
        header.setPreferredSize(new Dimension(0, HEADER_HEIGHT));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                Theme.padding(0, Theme.PAD_LARGE, 0, Theme.PAD_LARGE)));

        JLabel title = new JLabel(pageTitle());
        title.setFont(Theme.H3);
        title.setForeground(Theme.TEXT);
        header.add(title, BorderLayout.WEST);

        JPanel right = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, Theme.GAP, 0));
        right.setOpaque(false);
        right.add(Theme.caption("Signed in as " + user.getRole().getLabel()));
        right.add(Theme.badge(Theme.version(), Theme.PRIMARY));
        header.add(right, BorderLayout.EAST);

        // The label is kept so the title can change with the page.
        this.headerTitle = title;
        return header;
    }

    private JLabel headerTitle;

    /**
     * The starting header text.
     *
     * <p>Read from the dashboard page rather than repeated, so the header
     * and the page cannot disagree about what the page is called. The pages
     * are built before the header, so the lookup is safe here.
     */
    private String pageTitle() {
        PagePanel dashboard = pages.get(DASHBOARD);
        return dashboard == null ? Constants.APP_NAME : dashboard.title();
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /**
     * Shows a page and refreshes it.
     *
     * <p>Reloading on every visit is deliberate. The data is live in MySQL
     * and may have been changed by another window or by the previous
     * screen's own edits, so showing a cached table would mean the
     * application quietly disagrees with the database.
     */
    public void showPage(String key) {
        PagePanel page = pages.get(key);
        if (page == null) {
            LOGGER.warning("Ignoring request for unknown page '" + key + "'.");
            return;
        }
        current = key;
        cards.show(content, key);
        for (NavButton button : navButtons) {
            boolean selected = button.pageKey.equals(key);
            button.setActive(selected);
            button.repaint();
        }
        if (headerTitle != null) {
            headerTitle.setText(page.title());
        }
        page.reload();
    }

    /**
     * Refreshes the pages an action could have made stale.
     *
     * <p>Deliberately not "refresh everything". Reloading all nine pages
     * fires nine concurrent queries, and because they finish in an arbitrary
     * order the shared progress glass pane is hidden by whichever finishes
     * first while the rest are still running. Naming the affected pages also
     * means the page the user is looking at refreshes once, rather than
     * twice, when it is one of them.
     *
     * <p>The dashboard is always included, because a save that changes a
     * headline count and a dashboard still showing the old one is the
     * disagreement this is here to prevent.
     *
     * @param pageKeys the pages to refresh
     */
    public void refreshAfterChange(String... pageKeys) {
        java.util.LinkedHashSet<String> targets = new java.util.LinkedHashSet<>();
        targets.add(DASHBOARD);
        java.util.Collections.addAll(targets, pageKeys);
        for (String key : targets) {
            refreshPage(key);
        }
    }

    /**
     * Refreshes a page other than the visible one, and shows it if asked.
     *
     * <p>Used by a workflow that finishes somewhere else: deleting a student
     * from their academic profile should leave the students table
     * consistent, but taking the user away from the profile to do it would
     * be worse than refreshing it silently.
     */
    public void refreshPage(String key) {
        PagePanel page = pages.get(key);
        if (page != null) {
            page.reload();
        }
    }

    /** The page key currently on screen. */
    public String currentPage() {
        return current;
    }

    // ------------------------------------------------------------------
    // Account actions
    // ------------------------------------------------------------------

    private void changePassword() {
        ChangePasswordDialog dialog = new ChangePasswordDialog(this, services, user);
        dialog.setVisible(true);
    }

    /**
     * Returns to the sign-in screen.
     *
     * <p>Any unsaved filter text or a half-typed search is discarded, which
     * is the right call: the next person at the machine should not inherit
     * it, and the alternative - clearing every page by hand - is more code
     * for a worse experience.
     */
    private void signOut() {
        if (!UiErrors.confirm(this, "Sign out",
                "Sign out of " + Constants.APP_NAME + "?")) {
            return;
        }
        LOGGER.info("User '" + user.getUsername() + "' signed out.");
        pages.clear();
        new LoginFrame(services).showLogin();
        dispose();
    }

    /**
     * Confirms before closing, so a half-finished edit is not lost without
     * warning.
     */
    private void installCloseHandling() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (UiErrors.confirm(MainFrame.this, "Exit",
                        "Close " + Constants.APP_NAME + "?")) {
                    LOGGER.info("Application closing; all data is already in MySQL.");
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // The rail button
    // ------------------------------------------------------------------

    /**
     * A rail entry that paints its own selected and hovered states.
     *
     * <p>Painted rather than assembled from borders and background colours
     * so the pill can be inset from the rail's edge, which is what stops
     * eleven buttons from reading as one solid blue block.
     */
    private static final class NavButton extends JButton {

        private static final long serialVersionUID = 1L;

        private final String pageKey;
        private boolean active;
        private boolean hovered;

        private NavButton(String pageKey, String text) {
            super(text);
            this.pageKey = pageKey;
            setFont(Theme.BODY);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(LEFT);
            setBorder(Theme.padding(10, Theme.PAD_LARGE, 10, 0));
        }

        /**
         * Marks this entry as the visible page.
         *
         * <p>Named to avoid {@link JButton#setSelected}: that method
         * already means "this button is the selected one in its button
         * group", and overriding it would make the painted state depend on
         * the button group's own selection model.
         */
        private void setActive(boolean active) {
            this.active = active;
        }

        private void setHovered(boolean hovered) {
            this.hovered = hovered;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g.setColor(Theme.NAVY_SELECTED);
                    g.fill(new RoundRectangle2D.Float(0, 2, getWidth() - 6,
                            getHeight() - 4, 10, 10));
                    g.setColor(Color.WHITE);
                } else if (hovered) {
                    g.setColor(Theme.NAVY_HOVER);
                    g.fill(new RoundRectangle2D.Float(0, 2, getWidth() - 6,
                            getHeight() - 4, 10, 10));
                    g.setColor(Theme.TEXT_ON_NAVY);
                } else {
                    g.setColor(new Color(0xA8, 0xB6, 0xD0));
                }
                g.setFont(getFont());
                g.drawString(getText(), Theme.PAD_LARGE + 4,
                        getHeight() / 2 + g.getFontMetrics().getAscent() / 2 - 2);
            } finally {
                g.dispose();
            }
        }
    }
}
