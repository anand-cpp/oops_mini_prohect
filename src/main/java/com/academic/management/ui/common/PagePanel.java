package com.academic.management.ui.common;

import com.academic.management.exception.AppException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * The frame every screen shares: a title, an optional subtitle, a row of
 * action buttons on the right, a content area, and a status line.
 *
 * <h2>Why a base class</h2>
 * Six screens each needing a header, a toolbar and a footer would otherwise
 * each hand-roll the same three-panel layout, and the spacing would drift
 * between them. More importantly, each would hand-roll the
 * load-asynchronously-then-fill-the-table sequence, and that is exactly the
 * kind of code that works on the happy path and drops an exception on the
 * floor when the database is down.
 *
 * <h2>How loading works</h2>
 * A subclass implements {@link #load()} and {@link #render(List)}, and
 * this class owns the threading: {@link #reload()} runs {@code load} on a
 * worker thread, shows a glass pane so the window stays visibly alive, then
 * hands the result to {@code render} on the event thread. Subclasses
 * therefore never touch a Swing component off the event thread and never
 * have to write a try/catch, because {@link UiErrors#runAsync} already
 * reports the failure to the user and the log.
 */
public abstract class PagePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
    private final JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel statusLabel = new JLabel(" ");
    private final transient List<JButton> toolbarButtons = new ArrayList<>();

    /** True while a load is running, so a second one is not started. */
    private transient boolean loading;

    /** Whether the page has written a status line of its own for this load. */
    private transient boolean statusOverridden;

    /**
     * @param title    the page title
     * @param subtitle one line explaining what the page is for
     */
    @SuppressWarnings("this-escape")
    protected PagePanel(String title, String subtitle) {
        // The lint warning is inherent to extending Swing: setLayout,
        // setBackground and setBorder are overridable, so a subclass could
        // in principle see them called before its own fields exist. None of
        // the pages override them - they build their content in setContent,
        // which they call for themselves - so the escape cannot reach any
        // uninitialised state, and suppressing it is better than
        // restructuring the hierarchy to satisfy a lint rule.
        setLayout(new BorderLayout());
        setBackground(Theme.PAGE);
        setBorder(Theme.padding(Theme.PAD_LARGE));

        titleLabel = Theme.heading(title);
        subtitleLabel = Theme.caption(subtitle);

        JPanel heading = new JPanel(new BorderLayout(0, 2));
        heading.setOpaque(false);
        heading.add(titleLabel, BorderLayout.NORTH);
        heading.add(subtitleLabel, BorderLayout.SOUTH);

        toolbar.setOpaque(false);
        actionBar.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        header.setOpaque(false);
        header.setBorder(Theme.padding(0, 0, Theme.PAD, 0));
        header.add(heading, BorderLayout.NORTH);

        JPanel actionsRow = new JPanel(new BorderLayout(Theme.GAP, 0));
        actionsRow.setOpaque(false);
        actionsRow.add(toolbar, BorderLayout.WEST);
        actionsRow.add(actionBar, BorderLayout.EAST);
        header.add(actionsRow, BorderLayout.CENTER);

        statusLabel.setFont(Theme.SMALL);
        statusLabel.setForeground(Theme.TEXT_MUTED);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(Theme.padding(Theme.GAP, 0, 0, 0));
        footer.add(statusLabel, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    // Decorating the page
    // ------------------------------------------------------------------

    /** Adds a button to the left of the toolbar, in the order declared. */
    protected void addToolbarButton(JButton button) {
        toolbarButtons.add(button);
        toolbar.add(button);
    }

    /** Adds a button to the right-hand side of the toolbar. */
    protected void addActionButton(JButton button) {
        actionBar.add(button);
    }

    /** Puts a component in the content area, which fills the page. */
    protected void setContent(JComponent component) {
        content.removeAll();
        content.setBackground(Theme.PAGE);
        content.add(component, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    /** Changes the page title at run time, for example after a filter. */
    protected void setPageTitle(String title) {
        titleLabel.setText(title);
    }

    /** The page title, which the frame shows in its own header. */
    public String title() {
        return titleLabel.getText();
    }

    protected void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
    }

    /**
     * Writes a line into the footer, for a row count or a last action.
     *
     * <p>Calling this marks the line as the page's own, which stops
     * {@link #reload()} from replacing it with the generic record count.
     * A page that filters, or that has something more useful to say than
     * "14 records" - "14 students, 3 in Computer Science" - would otherwise
     * have its own message overwritten a few milliseconds after writing it.
     */
    protected void setStatus(String text) {
        statusOverridden = true;
        statusLabel.setText(text == null ? " " : text);
    }

    /** The panel action buttons are added to. */
    protected JPanel actionBar() {
        return actionBar;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Reads everything this page shows.
     *
     * @return the rows to render, already formatted for display
     * @throws AppException if the database cannot be read
     */
    protected abstract List<String[]> load() throws AppException;

    /**
     * Refreshes the page, on a worker thread.
     *
     * <p>A load already in flight is not started a second time. The frame
     * refreshes several pages after one save, and a user can click Refresh
     * while a page is loading; without this guard two loads race and the
     * slower one wins, so the table can end up showing the data from before
     * the change the user just made. Dropping the newer request would be
     * wrong too, which is why the in-flight load is kept and the extra
     * request is simply ignored - the first one is already reading the
     * post-change state in every case where the second would matter.
     */
    public final void reload() {
        if (loading) {
            return;
        }
        loading = true;
        statusOverridden = false;
        setControlsEnabled(false);
        UiErrors.runAsync(this, "Could not load " + titleLabel.getText().toLowerCase(),
                this::load,
                rows -> {
                    finishLoad();
                    render(rows);
                    afterLoad();
                    if (!statusOverridden) {
                        setStatus(UiSupport.count(rows.size(), "record", "records")
                                + (rows.isEmpty() ? " — nothing to show yet" : ""));
                    }
                },
                this::finishLoad);
    }

    /**
     * Runs on the event thread once a load has finished.
     *
     * <p>For a page whose {@link #load()} also produced something that is not
     * a table row - a set of calculated figures, a summary object - that
     * value is stashed in a field by {@code load} and applied here. It is
     * safe to hand a value over this way because the worker writes it before
     * the result is posted to the event queue, which is what publishes it.
     * The alternative, re-querying from the event thread, would be a second
     * round trip and could return different numbers from the rows just
     * rendered.
     */
    protected void afterLoad() {
        // Overridden by pages that show more than rows.
    }

    /** Clears the in-flight flag and re-enables the page's controls. */
    private void finishLoad() {
        loading = false;
        setControlsEnabled(true);
    }

    /**
     * Fills the page from loaded rows.
     *
     * <p>Called on the event thread with the result of {@link #load()}.
     * The default implementation does nothing, which is what a page that
     * rebuilds its whole content wants; a page showing a table overrides
     * it to call {@link #fillTable}.
     */
    protected void render(List<String[]> rows) {
        // Overridden by table pages.
    }

    /**
     * Replaces a table's rows and reports the count.
     *
     * @param model the table's model
     * @param rows  the rows to show
     */
    protected void fillTable(DisplayTableModel model, List<String[]> rows) {
        model.replaceAll(rows);
    }

    /**
     * Records a count in the footer, for a page that shows figures rather
     * than rows.
     */
    protected void setLoadedStatus(String text) {
        setStatus(text);
    }

    /**
     * A placeholder for an empty result, so "no records" never looks like a
     * broken table.
     */
    protected static JPanel emptyState(String message) {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setBackground(Theme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                Theme.padding(Theme.PAD_LARGE * 2)));
        JLabel label = new JLabel(message);
        label.setFont(Theme.BODY);
        label.setForeground(Theme.TEXT_MUTED);
        panel.add(label);
        return panel;
    }

    /** A vertical run of components with a consistent gap. */
    protected static JPanel column(Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component component : components) {
            if (component instanceof JComponent swing) {
                swing.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            panel.add(component);
            panel.add(Box.createVerticalStrut(Theme.GAP));
        }
        return panel;
    }

    /** A horizontal run of components with a consistent gap. */
    protected static JPanel row(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        panel.setOpaque(false);
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    /** Fills the remaining width of a toolbar with empty space. */
    protected static Component glue() {
        return Box.createHorizontalGlue();
    }

    /**
     * Enables or disables a page's controls while it is busy, so the user
     * cannot start a second action while one is running.
     */
    protected void setControlsEnabled(boolean enabled) {
        for (JButton button : toolbarButtons) {
            button.setEnabled(enabled);
        }
        for (Component component : actionBar.getComponents()) {
            if (component instanceof JButton button) {
                button.setEnabled(enabled);
            }
        }
    }

    /** The window this component is inside, or null when not yet shown. */
    protected static Window windowOf(Component component) {
        return component == null ? null
                : SwingUtilities.getWindowAncestor(component);
    }

    /**
     * Refreshes the other pages whose numbers this page's edit has changed.
     *
     * <p>Adding a student does not only change the students table: the
     * dashboard counts, the profile page and the search index are all stale
     * the moment the write succeeds. Refreshing them here means the user
     * never lands on a page that disagrees with what they just did.
     *
     * <p>Must be called on the event thread, from a success callback. A null
     * window is normal in a test and is ignored rather than failing the save
     * that has already succeeded.
     */
    protected final void refreshOtherPages(String... pageKeys) {
        if (windowOf(this) instanceof com.academic.management.ui.MainFrame frame) {
            frame.refreshAfterChange(pageKeys);
        }
    }

    /** A fixed-size spacer used to keep a card from collapsing. */
    protected static Dimension size(int width, int height) {
        return new Dimension(width, height);
    }
}
