package com.academic.management.ui.common;

import com.academic.management.exception.AppException;
import com.academic.management.util.AppLogger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Window;
import java.util.Map;
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
 */
public final class UiErrors {

    private static final Logger LOGGER = AppLogger.getLogger(UiErrors.class);

    private UiErrors() {
        // static helpers only
    }

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

    /** Shows a plain message in an error dialog. */
    public static void show(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrap(message), title, JOptionPane.ERROR_MESSAGE);
    }

    /** Shows a plain message in a warning dialog. */
    public static void showWarning(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrap(message), title,
                JOptionPane.WARNING_MESSAGE);
    }

    /** Confirms a destructive action. */
    public static boolean confirm(Component parent, String title, String message) {
        int choice = JOptionPane.showConfirmDialog(parent, wrap(message), title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /** Confirms an action whose consequences are spelled out in detail. */
    public static boolean confirmDetailed(Component parent, String title, String message) {
        JOptionPane optionPane = new JOptionPane(wrap(message), JOptionPane.WARNING_MESSAGE,
                JOptionPane.YES_NO_OPTION);
        optionPane.setBorder(Theme.padding(4));
        javax.swing.JDialog dialog = optionPane.createDialog(parent, title);
        dialog.setModal(true);
        dialog.setSize(Theme.WIDE_DIALOG);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        Object value = optionPane.getValue();
        return value instanceof Integer number && number == JOptionPane.YES_OPTION;
    }

    /** Reports success. */
    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrap(message), title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Asks for a single line of text.
     *
     * @return the entered value, or null when the user cancelled
     */
    public static String prompt(Component parent, String title, String message, String initial) {
        javax.swing.JTextField field = Theme.styleInput(new javax.swing.JTextField(initial));
        field.setColumns(24);
        Object[] options = {"OK", "Cancel"};
        javax.swing.JOptionPane pane = new javax.swing.JOptionPane(
                new Object[]{message, field}, JOptionPane.QUESTION_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);
        javax.swing.JDialog dialog = pane.createDialog(parent, title);
        dialog.setModal(true);
        dialog.setSize(Theme.WIDE_DIALOG);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        Object value = pane.getValue();
        if (value instanceof Integer number && number == 0) {
            String typed = field.getText().trim();
            return typed.isEmpty() ? null : typed;
        }
        return null;
    }

    /**
     * Asks for a yes-or-no question with a typed confirmation.
     *
     * <p>Used for the one destructive action where a stray click is
     * genuinely costly: dropping the whole schema is not undoable from
     * inside the application.
     *
     * @return true only when the user typed {@code expected} exactly
     */
    public static boolean confirmTyped(Component parent, String title, String message,
                                       String expected) {
        javax.swing.JTextField field = Theme.styleInput(new javax.swing.JTextField());
        field.setColumns(20);
        javax.swing.JLabel warning = Theme.caption("Type " + expected + " to confirm.");
        Object[] contents = {message, field, warning};
        Object[] options = {"Proceed", "Cancel"};
        javax.swing.JOptionPane pane = new javax.swing.JOptionPane(contents,
                JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null,
                options, options[1]);
        javax.swing.JDialog dialog = pane.createDialog(parent, title);
        dialog.setModal(true);
        dialog.setSize(Theme.WIDE_DIALOG);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        Object value = pane.getValue();
        boolean proceed = value instanceof Integer number && number == 0;
        return proceed && expected.equalsIgnoreCase(field.getText().trim());
    }

    /**
     * Keeps a long multi-line message readable in a dialog by inserting
     * soft line breaks, so a long path or SQL fragment cannot stretch the
     * dialog off the screen.
     */
    private static Object wrap(String message) {
        if (message == null) {
            return "";
        }
        StringBuilder wrapped = new StringBuilder(message.length() + 16);
        int lineLength = 0;
        for (String word : message.split("\\s+")) {
            if (lineLength > 0 && lineLength + word.length() > 70) {
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
        Window window = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
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

    /**
     * Marks the window as busy by showing a glass pane with a progress
     * message, so the user gets feedback instead of a window that appears
     * to have stopped responding.
     *
     * <p>The glass pane is only installed for a frame. A dialog has its own
     * modality, so it already blocks input, and casting it to a frame would
     * throw at exactly the moment an error is being reported - which is the
     * one moment where an exception is least welcome. The reference is also
     * remembered rather than re-read, so two overlapping calls cannot each
     * install a pane and leave one of them permanently visible.
     */
    private static void setBusy(Window window, boolean busy) {
        if (!(window instanceof javax.swing.JFrame frame)) {
            return;
        }
        javax.swing.JRootPane rootPane = frame.getRootPane();
        if (busy) {
            javax.swing.JComponent pane = BUSY_PANES.get(frame);
            if (pane == null) {
                pane = busyPane();
                BUSY_PANES.put(frame, pane);
                rootPane.setGlassPane(pane);
            }
            pane.setVisible(true);
        } else {
            javax.swing.JComponent pane = BUSY_PANES.get(frame);
            if (pane != null) {
                pane.setVisible(false);
            }
        }
    }

    /**
     * The glass pane installed on each frame.
     *
     * <p>A {@link java.util.WeakHashMap} so a disposed frame does not keep
     * its pane - and through it its component tree - reachable.
     */
    private static final Map<javax.swing.JFrame, javax.swing.JComponent> BUSY_PANES =
            new java.util.WeakHashMap<>();

    private static javax.swing.JComponent busyPane() {
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout());
        panel.setBackground(new java.awt.Color(255, 255, 255, 200));

        javax.swing.JLabel label = Theme.caption("Working…");
        label.setFont(Theme.H3);
        label.setForeground(Theme.TEXT);
        label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        javax.swing.JPanel centre = new javax.swing.JPanel(new java.awt.GridBagLayout());
        centre.setOpaque(false);
        centre.add(label);

        javax.swing.JProgressBar bar = new javax.swing.JProgressBar();
        bar.setIndeterminate(true);
        bar.setPreferredSize(new java.awt.Dimension(220, 6));
        bar.setBorder(Theme.padding(0));

        javax.swing.JPanel card = Theme.card();
        card.setLayout(new java.awt.BorderLayout(Theme.GAP, Theme.GAP));
        card.setBorder(Theme.padding(Theme.PAD_LARGE));
        card.add(centre, java.awt.BorderLayout.CENTER);
        card.add(bar, java.awt.BorderLayout.SOUTH);
        card.setPreferredSize(new java.awt.Dimension(280, 120));

        javax.swing.JPanel wrapper = new javax.swing.JPanel(new java.awt.GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(card);
        panel.add(wrapper, java.awt.BorderLayout.CENTER);
        return panel;
    }
}
