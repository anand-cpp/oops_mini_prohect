package com.academic.management.ui.common;

import com.academic.management.exception.AppException;
import com.academic.management.util.AppLogger;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The one place the UI turns a failure into something a person can read.
 *
 * <h2>Why the technical message is never shown</h2>
 * {@link AppException} carries two strings: a user-facing sentence and the
 * developer context that makes the log useful. Showing the second to
 * somebody trying to fix a typo in an email address teaches them nothing;
 * logging it and losing it teaches them nothing either, which is why both
 * happen here and the dialog gets only the first. Centralising it also
 * means no screen can forget to log an exception, which is the usual way a
 * swallowed {@code SQLException} ends up on the floor.
 *
 * <h2>Why the dialogs are built here rather than delegated</h2>
 * {@code JOptionPane} draws itself with the look and feel's own colours,
 * which is the one part of the application no screen gets to style: an error
 * is exactly the moment that looks out of place. Every dialog below is
 * therefore assembled from the same {@link Theme} tokens as the pages - the
 * same card, the same type scale, the same buttons - so a failure looks like
 * part of the product rather than like the operating system intruding.
 *
 * <p>The severity is carried by a word as well as by a colour. A coloured
 * stripe on its own would fail anybody who cannot tell one colour from
 * another, so every dialog names its severity in text above the title.
 *
 * <h2>Why the busy pane waits</h2>
 * The glass pane used to appear the instant a query started and vanish when
 * it finished, which meant a slow lookup looked identical to the local
 * filter that answered in twenty milliseconds - a flash of a modal overlay
 * for something the user never noticed was slow. It is now held back for
 * {@value #BUSY_DELAY_MS} milliseconds, so it appears only for work that is
 * genuinely slow and a fast query is silent.
 */
public final class UiErrors {

    private static final Logger LOGGER = AppLogger.getLogger(UiErrors.class);

    /**
     * How long a query has to run before the window says so, in milliseconds.
     *
     * <p>Long enough that a query returning from a warm buffer - which is
     * most of them - never shows the overlay at all, short enough that a real
     * round trip still reports itself while the user is still watching.
     */
    private static final int BUSY_DELAY_MS = 250;

    /**
     * How wide the message column is, in logical pixels.
     *
     * <p>Fixed rather than left to the label, so every dialog in the
     * application opens at the same width and the buttons do not jump about
     * from one message length to the next.
     */
    private static final int MESSAGE_WIDTH = 500;

    /** Where a message wraps, in characters. Chosen to suit the width above. */
    private static final int WRAP_AT = 70;

    /** Tallest the message area is allowed to grow, before it starts to scroll. */
    private static final int MESSAGE_HEIGHT_CAP = 320;

    /** Action name for the escape binding, so it cannot clash with a button. */
    private static final String DISMISS = "ui-dismiss";

    private UiErrors() {
        // static helpers only
    }

    // ------------------------------------------------------------------
    // Reporting a failure
    // ------------------------------------------------------------------

    /**
     * Reports a failure to the user and logs the detail.
     *
     * @param parent the component the dialog is centred on, may be null
     * @param title  the dialog title, phrased as a question where possible
     * @param error  the failure to report
     */
    public static void show(Component parent, String title, AppException error) {
        if (error == null) {
            show(parent, title, "Something went wrong, and no further detail is available.");
            return;
        }
        LOGGER.log(Level.WARNING, title + " -> " + error, error);
        show(parent, title, error.getMessage());
    }

    /** Reports an unexpected runtime failure, which is always a bug. */
    public static void showUnexpected(Component parent, String title, Throwable error) {
        LOGGER.log(Level.SEVERE, title + " -> unexpected failure", error);
        String message = error == null || error.getMessage() == null
                ? "An unexpected internal error occurred."
                : "An unexpected internal error occurred: " + error.getMessage();
        show(parent, title, message + "\n\nThe full details have been written to the log file.");
    }

    // ------------------------------------------------------------------
    // The dialogs callers ask for
    // ------------------------------------------------------------------

    /** Shows a plain message in an error dialog. */
    public static void show(Component parent, String title, String message) {
        modal(parent, title, message, Severity.ERROR, null, null);
    }

    /** Shows a plain message in a warning dialog. */
    public static void showWarning(Component parent, String title, String message) {
        modal(parent, title, message, Severity.WARNING, null, null);
    }

    /** Reports success. */
    public static void info(Component parent, String title, String message) {
        modal(parent, title, message, Severity.INFO, null, null);
    }

    /** Confirms a destructive action. */
    public static boolean confirm(Component parent, String title, String message) {
        return modal(parent, title, message,
                Severity.INFO, affirmative(title, false), null);
    }

    /**
     * Confirms an action whose consequences are spelled out in detail.
     *
     * <p>The same dialog as {@link #confirm}, flagged destructive so the
     * affirmative button is the danger colour and says what it will do. The
     * detail is what makes a cascade confirmable at all: "are you sure?" on a
     * delete that also removes a student's enrolments and results is not a
     * meaningful question, but one that names the three counts is.
     */
    public static boolean confirmDetailed(Component parent, String title, String message) {
        return modal(parent, title, message,
                Severity.WARNING, affirmative(title, true), null);
    }

    /**
     * Asks for a single line of text.
     *
     * @return the entered value, or null when the user cancelled or left it
     *         blank
     */
    public static String prompt(Component parent, String title, String message, String initial) {
        JTextField field = Theme.styleInput(new JTextField(initial));
        field.setColumns(24);
        field.getAccessibleContext().setAccessibleName(title);
        if (!modal(parent, title, message, Severity.INFO, "OK", field)) {
            return null;
        }
        String typed = field.getText().trim();
        return typed.isEmpty() ? null : typed;
    }

    // ------------------------------------------------------------------
    // One dialog, built from the theme
    // ------------------------------------------------------------------

    /**
     * Builds and shows one modal dialog.
     *
     * <p>Every dialog the application shows goes through here, which is what
     * stops them drifting apart: one card, one type scale, one button row, one
     * escape behaviour, one place where the default button is set.
     *
     * <p>A dialog with something to confirm takes the focus and treats Enter
     * as yes; a message dialog does not, because a stray Enter on an error
     * somebody has not finished reading should not dismiss it.
     *
     * @param severity    how the dialog names and colours itself
     * @param affirmative the confirming button's label, or null for a dialog
     *                    with nothing to confirm
     * @param field       an input to show under the message, or null
     * @return whether the user chose the affirmative button
     */
    private static boolean modal(Component parent, String title, String message,
                                 Severity severity, String affirmative, JTextField field) {
        boolean confirming = affirmative != null;
        JButton yes = confirming
                ? (severity == Severity.WARNING
                        ? Theme.dangerButton(affirmative)
                        : Theme.button(affirmative, Theme.PRIMARY))
                : null;
        JButton no = confirming
                ? Theme.secondaryButton("Cancel")
                : Theme.secondaryButton("Close");

        JDialog dialog = new JDialog(ownerOf(parent), title,
                JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        boolean[] accepted = {false};
        if (yes != null) {
            yes.addActionListener(event -> {
                accepted[0] = true;
                dialog.dispose();
            });
        }
        no.addActionListener(event -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.setOpaque(false);
        buttons.add(no);
        if (yes != null) {
            buttons.add(yes);
        }

        JPanel body = new JPanel(new BorderLayout(0, Theme.PAD));
        body.setOpaque(false);
        body.add(heading(title, severity), BorderLayout.NORTH);
        body.add(messageArea(message, field), BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAGE);
        root.setBorder(Theme.padding(Theme.PAD));
        root.add(body, BorderLayout.CENTER);

        JRootPane pane = dialog.getRootPane();
        pane.setDefaultButton(yes);
        pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), DISMISS);
        pane.getActionMap().put(DISMISS, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                dialog.dispose();
            }
        });

        dialog.setContentPane(root);
        dialog.pack();
        // Never narrower than a standard dialog, whatever the message says.
        dialog.setMinimumSize(new Dimension(Math.max(dialog.getWidth(), Theme.DIALOG.width),
                dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return accepted[0];
    }

    /**
     * The label for the button that carries the action out.
     *
     * <p>Taken from the title, because every title at a confirming call site is
     * already the imperative that button should say - "Delete course" rather
     * than "OK" or "Yes". A title too long to be a button label falls back to a
     * word that still says what kind of choice this is.
     */
    private static String affirmative(String title, boolean destructive) {
        String trimmed = title == null ? "" : title.trim();
        if (!trimmed.isEmpty() && trimmed.length() <= 28) {
            return trimmed;
        }
        return destructive ? "Delete" : "Continue";
    }

    /**
     * The title row: a coloured stripe beside the severity and the title.
     *
     * <p>The severity is written out rather than left to the colour, and the
     * title is the strongest thing in the dialog because it is what the user
     * needs to read to know what has just happened.
     */
    private static JPanel heading(String title, Severity severity) {
        JLabel severityLabel = Theme.caption(severity.word);
        severityLabel.setFont(Theme.BODY_BOLD);
        severityLabel.setForeground(severity.color);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.H3);
        titleLabel.setForeground(Theme.TEXT);

        JPanel words = new JPanel(new BorderLayout(0, 2));
        words.setOpaque(false);
        words.add(severityLabel, BorderLayout.NORTH);
        words.add(titleLabel, BorderLayout.CENTER);

        JPanel row = new JPanel(new BorderLayout(Theme.GAP, 0));
        row.setOpaque(false);
        row.add(stripe(severity.color), BorderLayout.WEST);
        row.add(words, BorderLayout.CENTER);
        return row;
    }

    private static JPanel stripe(Color color) {
        JPanel bar = new JPanel();
        bar.setBackground(color);
        bar.setOpaque(true);
        bar.setPreferredSize(new Dimension(4, 0));
        return bar;
    }

    /**
     * The message, in a fixed-width area that scrolls only if it has to.
     *
     * <p>The fixed width is what keeps every dialog the same size. The scroll
     * pane is there for the one case the wrap cannot solve - a stack trace
     * fragment in an unexpected error - so the ordinary case never shows a
     * scrollbar.
     */
    private static JComponent messageArea(String message, JTextField field) {
        JLabel label = new JLabel("<html><body style='width:" + MESSAGE_WIDTH + "px'>"
                + html(wrap(message)) + "</body></html>");
        label.setFont(Theme.BODY);
        label.setForeground(Theme.TEXT);

        JPanel inner = new JPanel(new BorderLayout(0, Theme.GAP));
        inner.setOpaque(false);
        inner.add(label, BorderLayout.CENTER);
        if (field != null) {
            field.setPreferredSize(new Dimension(MESSAGE_WIDTH, 30));
            inner.add(field, BorderLayout.SOUTH);
        }

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(MESSAGE_WIDTH + Theme.GAP,
                Math.min(inner.getPreferredSize().height + Theme.GAP, MESSAGE_HEIGHT_CAP)));
        return scroll;
    }

    /** The window a dialog should be owned by, or null when there is none. */
    private static Window ownerOf(Component parent) {
        return parent == null ? null : SwingUtilities.getWindowAncestor(parent);
    }

    /** How a dialog names itself, and in which colour. */
    private enum Severity {

        ERROR("Error", Theme.DANGER),
        WARNING("Warning", Theme.WARNING),
        INFO("Information", Theme.SUCCESS);

        private final String word;
        private final Color color;

        Severity(String word, Color color) {
            this.word = word;
            this.color = color;
        }
    }

    /**
     * Keeps a long multi-line message readable in a dialog by inserting
     * soft line breaks, so a long path or SQL fragment cannot stretch the
     * dialog off the screen.
     */
    private static String wrap(String message) {
        if (message == null) {
            return "";
        }
        StringBuilder wrapped = new StringBuilder(message.length() + 16);
        int lineLength = 0;
        for (String word : message.split("\\s+")) {
            if (lineLength > 0 && lineLength + word.length() > WRAP_AT) {
                wrapped.append('\n');
                lineLength = 0;
            } else if (lineLength > 0) {
                wrapped.append(' ');
                lineLength++;
            }
            wrapped.append(word);
            lineLength += word.length();
        }
        return wrapped.toString();
    }

    /**
     * Escapes the message for the HTML label that renders it.
     *
     * <p>An apostrophe in a student's name, or a {@code <} out of a SQL
     * fragment, would otherwise be read as markup and either swallowed or
     * turned into part of the layout.
     */
    private static String html(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    // ------------------------------------------------------------------
    // Running work off the event thread
    // ------------------------------------------------------------------

    /**
     * Runs a database-backed action off the event dispatch thread and hands
     * the result to a callback on the event thread.
     *
     * <h2>Why this exists</h2>
     * Every service call reaches MySQL. Calling one directly from an
     * action listener freezes the whole window until the round trip
     * finishes, which on a slow query looks like a crashed application.
     * This runs the work on a background thread, shows a glass pane so the
     * window stays visibly alive, and marshals both the callback and any
     * dialog back onto the event thread - because touching Swing from a
     * worker thread is the kind of bug that appears once a month and is
     * impossible to reproduce on demand.
     *
     * <p>Passing the loaded value to a callback rather than a bare
     * {@code Runnable} is what keeps the result private to this call: a
     * page cannot load rows into a field a second click can overwrite.
     *
     * @param parent   the component used to centre dialogs
     * @param title    the dialog title if the work fails
     * @param work     the database call; may throw {@link AppException}
     * @param onSuccess run on the event thread with the work's result
     * @param <T>      what the work produces
     */
    public static <T> void runAsync(Component parent, String title, Work<T> work,
                                    java.util.function.Consumer<T> onSuccess) {
        runAsync(parent, title, work, onSuccess, null);
    }

    /**
     * Runs work off the event thread, with a callback for the failure case
     * as well as the success case.
     *
     * <p>The failure callback exists because a screen often has to undo
     * something the dialog already did - clearing a password field after a
     * rejected sign-in, re-enabling a button - and the generic error dialog
     * has no way to know what that is.
     *
     * @param onFailure run on the event thread when the work throws
     */
    public static <T> void runAsync(Component parent, String title, Work<T> work,
                                    java.util.function.Consumer<T> onSuccess,
                                    Runnable onFailure) {
        Window window = ownerOf(parent);
        setBusy(window, true);
        Thread worker = new Thread(() -> {
            try {
                T result = work.run();
                SwingUtilities.invokeLater(() -> {
                    setBusy(window, false);
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                });
            } catch (AppException e) {
                SwingUtilities.invokeLater(() -> {
                    setBusy(window, false);
                    if (onFailure != null) {
                        onFailure.run();
                    }
                    show(parent, title, e);
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    setBusy(window, false);
                    if (onFailure != null) {
                        onFailure.run();
                    }
                    showUnexpected(parent, title, e);
                });
            }
        }, "academic-ui-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Runs a database-backed action with no result, for a save or a delete.
     *
     * @see #runAsync(Component, String, Work, java.util.function.Consumer)
     */
    public static void runAsync(Component parent, String title, VoidWork work) {
        runAsync(parent, title, () -> {
            work.run();
            return null;
        }, null);
    }

    /** A unit of work that reads through a service and produces a value. */
    @FunctionalInterface
    public interface Work<T> {
        T run() throws AppException;
    }

    /** A unit of work that only writes. */
    @FunctionalInterface
    public interface VoidWork {
        void run() throws AppException;
    }

    // ------------------------------------------------------------------
    // The busy glass pane
    // ------------------------------------------------------------------

    /**
     * Per-frame state for the busy indicator.
     *
     * <p>A count rather than a flag, because a page can legitimately have two
     * queries in flight at once - a save that also refreshes five other pages
     * - and a flag would hide the pane while the second was still running.
     */
    private static final class BusyState {

        private final Timer show = new Timer(BUSY_DELAY_MS, event -> reveal());

        private int pending;
        private JComponent pane;

        private BusyState() {
            show.setRepeats(false);
        }

        private void reveal() {
            if (pending > 0 && pane != null) {
                pane.setVisible(true);
            }
        }

        private void hide() {
            if (pane != null) {
                pane.setVisible(false);
            }
        }
    }

    /**
     * The glass pane installed on each frame.
     *
     * <p>A {@link WeakHashMap} so a disposed frame does not keep its pane -
     * and through it its component tree - reachable.
     */
    private static final Map<JFrame, BusyState> BUSY_STATES = new WeakHashMap<>();

    /**
     * Marks the window as busy, revealing the glass pane only if the work is
     * still running after {@value #BUSY_DELAY_MS} milliseconds.
     *
     * <p>The pane is only installed for a frame. A dialog has its own
     * modality, so it already blocks input, and casting it to a frame would
     * throw at exactly the moment an error is being reported - which is the
     * one moment where an exception is least welcome. The state is also
     * remembered rather than re-read, so two overlapping calls cannot each
     * install a pane and leave one of them permanently visible.
     */
    private static void setBusy(Window window, boolean busy) {
        if (!(window instanceof JFrame frame)) {
            return;
        }
        BusyState state = BUSY_STATES.get(frame);
        if (state == null) {
            state = new BusyState();
            state.pane = busyPane();
            frame.getRootPane().setGlassPane(state.pane);
            BUSY_STATES.put(frame, state);
        }
        if (busy) {
            state.pending++;
            if (state.pending == 1) {
                state.show.restart();
            }
        } else if (state.pending > 0) {
            state.pending--;
            if (state.pending == 0) {
                state.show.stop();
                state.hide();
            }
        }
    }

    private static JComponent busyPane() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(255, 255, 255, 200));

        JLabel label = Theme.caption("Working…");
        label.setFont(Theme.H3);
        label.setForeground(Theme.TEXT);
        label.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel centre = new JPanel(new GridBagLayout());
        centre.setOpaque(false);
        centre.add(label);

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(220, 6));
        bar.setBorder(Theme.padding(0));

        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(Theme.GAP, Theme.GAP));
        card.setBorder(Theme.padding(Theme.PAD_LARGE));
        card.add(centre, BorderLayout.CENTER);
        card.add(bar, BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(280, 120));

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(card);
        panel.add(wrapper, BorderLayout.CENTER);
        return panel;
    }
}