package com.academic.management.ui.common;

import com.academic.management.exception.AppException;
import com.academic.management.exception.ValidationException;
import com.academic.management.util.AppLogger;
import com.academic.management.util.Constants;
import com.academic.management.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A modal form dialog.
 *
 * <h2>Why one class rather than one per entity</h2>
 * Every entity screen needs the same eight things: a titled dialog, a grid
 * of labelled fields, validation that names the offending field instead of
 * throwing up a dialog, a save button that stays disabled until the form is
 * valid, and a cancel that throws the work away. Writing that out eleven
 * times means eleven chances to wire the save button to the wrong method,
 * and a form that only discovers it is invalid after the user has typed
 * everything.
 *
 * <h2>Live validation</h2>
 * Each field owns a {@link Rule} and is re-checked on every keystroke, so a
 * field turns red while the problem is still visible and Save stays disabled
 * until every rule passes. That makes an invalid form un-submittable rather
 * than merely likely to fail, and it reuses the same {@link Validator} calls
 * the service layer makes, so a value the form accepts cannot be rejected a
 * moment later by the domain.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FormDialog dialog = new FormDialog(frame, "Add student", "Save student", values -> {
 *     Student student = Student.create(
 *             FormDialog.required(values, "Roll number"),
 *             FormDialog.required(values, "Full name"),
 *             FormDialog.requiredDateOfBirth(values, "Date of birth"),
 *             ...);
 *     registry.studentService().add(student);
 * });
 * dialog.addField("Roll number", "", 12, FormDialog.requiredRule());
 * dialog.addField("Full name", "", 24, FormDialog.requiredRule());
 * dialog.addField("Date of birth", "2004-01-15", 10, FormDialog.dateOfBirthRule());
 * dialog.showDialog();
 * }</pre>
 */
public final class FormDialog extends javax.swing.JDialog {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = AppLogger.getLogger(FormDialog.class);

    /** The background of the inline validation banner. */
    private static final Color BANNER_BACKGROUND = new Color(0xFD, 0xEC, 0xEA);

    /** Fields in the order they were declared, which is the order shown. */
    private final transient Map<JComponent, Field> fields = new LinkedHashMap<>();
    private final transient Theme.Form form = new Theme.Form();
    private final transient List<Runnable> changeListeners = new java.util.ArrayList<>();
    private final JPanel banner = new JPanel(new BorderLayout());
    private final javax.swing.JButton saveButton = Theme.button("Save", Theme.PRIMARY);

    /** The save button's own text, restored if the write has to be retried. */
    private final String saveLabel;

    private boolean saved;
    private boolean saving;

    /**
     * @param owner     the parent window, may be a frame or another dialog
     * @param title     the dialog title, naming the entity
     * @param saveLabel the text on the save button
     * @param onSave    builds the model objects and persists them
     */
    public FormDialog(Window owner, String title, String saveLabel, SaveAction onSave) {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        this.saveLabel = saveLabel;
        this.saveButton.setText(saveLabel);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // The banner is the form's first child, so a problem is reported
        // above the fields rather than inside a dialog that hides them.
        banner.setVisible(false);
        form.addNote(banner);
        form.addFiller();

        javax.swing.JButton cancelButton = Theme.secondaryButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.setBackground(Theme.CARD);
        buttons.add(cancelButton);
        buttons.add(saveButton);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Theme.CARD);
        content.setBorder(Theme.padding(Theme.PAD_LARGE));
        content.add(form.getPanel(), BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(saveButton);
        saveButton.addActionListener(event -> attemptSave(onSave));
        cancelButton.addActionListener(event -> dispose());
    }

    // ------------------------------------------------------------------
    // Field declaration
    // ------------------------------------------------------------------

    /**
     * Adds a required text field.
     *
     * @param label the label, which is also the name used in error messages
     *              and as the key in the map passed to {@link SaveAction}
     * @param initial the starting text, empty for a new record
     * @param columns the field width in characters
     * @param rule the live validation rule
     * @return the field, so a caller can read or adjust it directly
     */
    public JTextField addField(String label, String initial, int columns, Rule rule) {
        JTextField field = Theme.styleInput(new JTextField(initial, columns));
        declare(label, field, rule, false);
        return field;
    }

    /** Adds a required text field with the default width. */
    public JTextField addField(String label, String initial, Rule rule) {
        return addField(label, initial, 24, rule);
    }

    /**
     * Adds an optional text field. The label reads "(optional)" on screen
     * but is still the plain label as a key, so the save action does not
     * have to know which fields were optional.
     */
    public JTextField addOptionalField(String label, String initial, int columns) {
        JTextField field = Theme.styleInput(new JTextField(initial, columns));
        declare(label, field, optionalRule(), true);
        return field;
    }

    /** Adds a read-only field, for a value the user must not change. */
    public JTextField addLockedField(String label, String value) {
        JTextField field = Theme.styleInput(new JTextField(value, 20));
        field.setEditable(false);
        field.setBackground(new Color(0xF1, 0xF3, 0xF6));
        field.setForeground(Theme.TEXT_MUTED);
        declare(label, field, null, false);
        return field;
    }

    /**
     * Adds a date field, pre-labelled with the expected format because an
     * ambiguous date is the single most common thing to get wrong here.
     *
     * <p>The hint is added to the visible label only. The key in the values
     * map stays the bare label, so a save action reads
     * {@code values.get("Admission date")} rather than having to know that
     * the screen happens to show {@code "Admission date (YYYY-MM-DD)"}.
     * Mixing the two would mean every caller that wanted a date had to
     * guess the exact hint text.
     */
    public JTextField addDateField(String label, String initialIsoDate) {
        return addDateField(label, initialIsoDate,
                (fieldLabel, text) -> Validator.requireDate(
                        label, text == null || text.isBlank() ? null : text));
    }

    /**
     * Adds a date field with a rule of the caller's choosing, for a date
     * that has extra conditions - a date of birth, say.
     */
    public JTextField addDateField(String label, String initialText, Rule rule) {
        JTextField field = Theme.styleInput(new JTextField(initialText, 14));
        declare(label, field, rule, false, label + " (YYYY-MM-DD)");
        return field;
    }

    /**
     * Adds a dropdown of choices, such as a department or a course.
     *
     * <p>The first entry is preselected, so a form that is opened and
     * accepted without touching the dropdown still produces a valid record
     * rather than a null foreign key.
     */
    public JComboBox<String> addComboField(String label, List<String> options, Rule rule) {
        JComboBox<String> combo = new JComboBox<>(options.toArray(new String[0]));
        combo.setFont(Theme.BODY);
        combo.setBackground(Color.WHITE);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_STRONG),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)));
        declare(label, combo, rule, false);
        return combo;
    }

    /** Adds a dropdown with no validation, for a filter or display. */
    public JComboBox<String> addComboField(String label, List<String> options) {
        return addComboField(label, options, requiredRule());
    }

    /**
     * Adds a row of two related short fields side by side, such as classes
     * held and classes attended.
     *
     * <p>Pairing those two matters: they are read together and the second
     * cannot exceed the first, so putting them on one row makes the
     * relationship obvious in a way two stacked rows do not.
     */
    public void addPair(String leftLabel, String leftInitial, Rule leftRule,
                        String rightLabel, String rightInitial, Rule rightRule) {
        JTextField left = Theme.styleInput(new JTextField(leftInitial, 8));
        JTextField right = Theme.styleInput(new JTextField(rightInitial, 8));
        watch(left);
        watch(right);
        form.addPair(leftLabel, left, rightLabel, right);
        fields.put(left, new Field(leftLabel, left, leftRule));
        fields.put(right, new Field(rightLabel, right, rightRule));
    }

    /** Adds a read-only explanatory line inside the form. */
    public void addNote(String text) {
        form.addNote(Theme.caption(text));
    }

    /**
     * Adds a live readout to the form: a line the screen fills in as the user
     * types, for a value the user does not type.
     *
     * <p>Used for a computed result - the grade implied by a pair of marks -
     * which is the one thing a form cannot ask for with a text field because
     * it is not the user's to enter. The initial text is a single space
     * rather than empty so the row keeps its height before the first
     * keystroke, rather than making the dialog jump.
     */
    public void addReadout(JComponent readout) {
        form.addNote(readout);
    }

    /**
     * Runs a callback whenever any declared field changes.
     *
     * <p>Attached to every field as it is declared, so a listener cannot
     * forget to cover a field it added later. Used by the results form to
     * keep its grade readout in step with the marks.
     */
    public void onAnyChange(Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * Records a field, attaches its rule and wires the change listener that
     * drives live validation.
     */
    private void declare(String label, JComponent component, Rule rule, boolean optional) {
        declare(label, component, rule, optional,
                optional ? label + " (optional)" : label);
    }

    /**
     * Records a field with an explicit visible label.
     *
     * @param label         the key used in the values map and in messages
     * @param component     the field
     * @param rule          its live validation rule, or null for no check
     * @param optional      whether to mark it optional in the visible label
     * @param displayedAs   the text shown beside the field
     */
    private void declare(String label, JComponent component, Rule rule, boolean optional,
                         String displayedAs) {
        fields.put(component, new Field(label, component, rule));
        watch(component);
        form.add(displayedAs, component);
    }

    /** Re-validates the form whenever a field's contents change. */
    private void watch(JComponent component) {
        if (component instanceof JTextField textField) {
            textField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent event) {
                    changed();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent event) {
                    changed();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent event) {
                    changed();
                }
            });
        } else if (component instanceof JComboBox<?> combo) {
            combo.addActionListener(event -> changed());
        }
    }

    /**
     * The single place a field change is handled.
     *
     * <p>Re-validation and the change listeners run in one method so a
     * listener can rely on the form already being up to date, and neither can
     * be wired without the other.
     */
    private void changed() {
        revalidate();
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    /** Attaches a rule to a field the caller built itself. */
    public <T extends JComponent> T withRule(T component, Rule rule) {
        Field field = fields.get(component);
        if (field != null) {
            field.rule = rule;
        }
        return component;
    }

    // ------------------------------------------------------------------
    // Live validation
    // ------------------------------------------------------------------

    /**
     * Re-runs every rule and updates the field borders, the banner and the
     * enabled state of the save button.
     *
     * <p>Only the first problem is shown. Listing every failing field at
     * once is more information but also more noise, and on a form with a
     * dozen fields the user is fixing one thing at a time anyway.
     */
    public void revalidate() {
        boolean allValid = true;
        String firstProblem = null;
        for (Field field : fields.values()) {
            String problem = field.problem();
            if (problem == null) {
                markValid(field.component);
            } else {
                markInvalid(field.component);
                if (firstProblem == null) {
                    firstProblem = problem;
                }
            }
            allValid &= problem == null;
        }
        // A write in flight owns the button, so a background revalidation -
        // triggered by the fields being cleared as the save starts - cannot
        // re-enable it and let a second submit through.
        saveButton.setEnabled(allValid && !saving);
        showBanner(firstProblem);
    }

    private void markInvalid(JComponent component) {
        if (component instanceof JTextField textField && textField.isEditable()) {
            textField.setBorder(Theme.errorBorder());
        }
    }

    private void markValid(JComponent component) {
        if (component instanceof JTextField textField && textField.isEditable()) {
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER_STRONG),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        }
    }

    /** Shows the first problem, or hides the banner when the form is valid. */
    private void showBanner(String message) {
        if (message == null) {
            banner.setVisible(false);
            return;
        }
        banner.removeAll();
        JLabel label = new JLabel(message);
        label.setFont(Theme.SMALL);
        label.setForeground(Theme.DANGER);

        JPanel inner = new JPanel(new BorderLayout());
        inner.setBackground(BANNER_BACKGROUND);
        inner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.DANGER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        inner.add(label, BorderLayout.CENTER);
        banner.add(inner, BorderLayout.CENTER);
        banner.setVisible(true);
        banner.revalidate();
        banner.repaint();
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    /**
     * Attempts the save.
     *
     * <p><b>The save runs on a worker thread.</b> Every
     * {@link SaveAction} ends in a database write, and doing that on the
     * event thread freezes the whole application - including this dialog -
     * for the length of the round trip.
     *
     * <p>The values are read <em>before</em> the worker starts, on the event
     * thread, and the fields are then disabled. Both halves matter. Reading
     * them on the worker would mean calling {@code getText()} on a Swing
     * component from a thread that does not own it, which is undefined
     * behaviour and can return a value that was never on screen; and leaving
     * the fields live would let the user keep editing a form whose write has
     * already started, so whatever they typed would be silently discarded
     * when the dialog closes.
     *
     * <p>A business-rule refusal is shown in the banner rather than in a
     * dialog, because a dialog hides the form the user is still filling in;
     * a duplicate email should be reported without losing the other values.
     * An unexpected runtime failure is a bug, so it is logged in full and
     * the user is pointed at the log instead of shown a stack trace. In both
     * cases the fields come back, so the save can be retried.
     */
    private void attemptSave(SaveAction onSave) {
        if (saving) {
            return;
        }
        saving = true;
        saveButton.setEnabled(false);
        saveButton.setText("Saving…");

        // On the event thread, so this is a legitimate read of the
        // components, and it is the value the user was actually looking at.
        final Map<String, String> values = readValues();
        // Only the fields that were live are frozen, and only those are
        // thawed again: a control the screen had already disabled for its
        // own reasons must stay disabled.
        final List<JComponent> frozen = new java.util.ArrayList<>();
        for (JComponent component : fields.keySet()) {
            if (component.isEnabled()) {
                frozen.add(component);
                component.setEnabled(false);
            }
        }

        Thread worker = new Thread(() -> {
            try {
                onSave.save(values);
                SwingUtilities.invokeLater(() -> {
                    saving = false;
                    saved = true;
                    dispose();
                });
            } catch (AppException e) {
                LOGGER.log(Level.WARNING, "Form save refused: " + e, e);
                SwingUtilities.invokeLater(() -> {
                    saving = false;
                    for (JComponent component : frozen) {
                        component.setEnabled(true);
                    }
                    saveButton.setText(saveLabel);
                    revalidate();
                    showBanner(e.getMessage());
                });
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Form save failed", e);
                SwingUtilities.invokeLater(() -> {
                    saving = false;
                    for (JComponent component : frozen) {
                        component.setEnabled(true);
                    }
                    saveButton.setText(saveLabel);
                    revalidate();
                    showBanner("An unexpected internal error occurred. The details were written "
                            + "to the log file.");
                });
            }
        }, "academic-form-save");
        worker.setDaemon(true);
        worker.start();
    }

    /** Reads every field, keyed by the label it was declared with. */
    public Map<String, String> readValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field field : fields.values()) {
            values.put(field.label, readValue(field.component));
        }
        return values;
    }

    /**
     * Sets the text of a declared field.
     *
     * <p>For a field whose value the screen can work out for itself, such as
     * defaulting a semester to the one the student is already in. Goes
     * through the component rather than a parallel map so the visible field
     * and the value used on save cannot drift apart.
     *
     * @return true when a field with that label exists
     */
    public boolean setFieldText(String label, String value) {
        for (Field field : fields.values()) {
            if (field.label.equals(label) && field.component instanceof JTextField textField) {
                textField.setText(value);
                revalidate();
                return true;
            }
        }
        return false;
    }

    private static String readValue(JComponent component) {
        if (component instanceof JTextField textField) {
            return textField.getText().trim();
        }
        if (component instanceof JComboBox<?> combo) {
            Object selected = combo.getSelectedItem();
            return selected == null ? "" : selected.toString();
        }
        return "";
    }

    /** True when the user completed the form rather than cancelling. */
    public boolean wasSaved() {
        return saved;
    }

    /**
     * Sizes, centres and shows the dialog, blocking until it closes.
     *
     * <p>Called rather than {@code setVisible} so callers do not have to
     * remember to pack after declaring their fields.
     */
    public void showDialog() {
        revalidate();
        pack();
        setSize(Math.max(Theme.WIDE_DIALOG.width, getWidth()),
                Math.max(Theme.WIDE_DIALOG.height, getHeight()));
        setMinimumSize(getSize());
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    // ------------------------------------------------------------------
    // Reading helpers, so the save action does no string handling
    // ------------------------------------------------------------------

    /** Reads a required text value. */
    public static String required(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireText(label, values.get(label));
    }

    /** Reads an optional text value, returning null when it is blank. */
    public static String optional(Map<String, String> values, String label) {
        String value = values.get(label);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Reads a required integer. */
    public static int requiredInt(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireInt(label, values.get(label));
    }

    /** Reads a required decimal. */
    public static double requiredDouble(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireDouble(label, values.get(label));
    }

    /** Reads a required date in {@code YYYY-MM-DD} form. */
    public static java.time.LocalDate requiredDate(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireDate(label, values.get(label));
    }

    /** Reads a date of birth, applying the plausible-age rule. */
    public static java.time.LocalDate requiredDateOfBirth(Map<String, String> values,
                                                          String label) throws AppException {
        return Validator.requireDateOfBirth(label, values.get(label));
    }

    /** Reads a semester, applying the allowed range. */
    public static int requiredSemester(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireSemester(label, values.get(label));
    }

    /** Reads internal marks, applying the 0..{@code MAX_INTERNAL_MARKS} range. */
    public static double requiredInternalMarks(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireMarks(label, values.get(label),
                Constants.MAX_INTERNAL_MARKS);
    }

    /** Reads external marks, applying the 0..{@code MAX_EXTERNAL_MARKS} range. */
    public static double requiredExternalMarks(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireMarks(label, values.get(label),
                Constants.MAX_EXTERNAL_MARKS);
    }

    /** Reads a required identifier such as a roll number or employee code. */
    public static String requiredIdentifier(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireIdentifier(label, values.get(label));
    }

    /** Reads a required email address. */
    public static String requiredEmail(Map<String, String> values, String label)
            throws AppException {
        return Validator.requireEmail(label, values.get(label));
    }

    /** Reads an optional phone number, returning null when it is blank. */
    public static String optionalPhone(Map<String, String> values, String label)
            throws AppException {
        return Validator.optionalPhone(label, optional(values, label));
    }

    // ------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------

    /**
     * One field's validation rule.
     *
     * <p>The label travels as an argument rather than being captured so
     * one rule instance can be reused across every field of the same kind.
     */
    @FunctionalInterface
    public interface Rule {
        void check(String label, String text) throws AppException;
    }

    /** Requires a non-blank value. */
    public static Rule requiredRule() {
        return Validator::requireText;
    }

    /** Requires a valid email address. */
    public static Rule emailRule() {
        return Validator::requireEmail;
    }

    /** Requires an identifier of 3-20 letters, digits, '-' or '_'. */
    public static Rule identifierRule() {
        return Validator::requireIdentifier;
    }

    /** Requires a whole number. */
    public static Rule intRule() {
        return Validator::requireInt;
    }

    /** Requires a decimal number. */
    public static Rule doubleRule() {
        return Validator::requireDouble;
    }

    /** Requires a date of birth implying a plausible age. */
    public static Rule dateOfBirthRule() {
        return Validator::requireDateOfBirth;
    }

    /** Requires a semester within the allowed range. */
    public static Rule semesterRule() {
        return Validator::requireSemester;
    }

    /** Requires marks within the internal maximum. */
    public static Rule internalMarksRule() {
        return (label, text) -> Validator.requireMarks(label, text,
                Constants.MAX_INTERNAL_MARKS);
    }

    /** Requires marks within the external maximum. */
    public static Rule externalMarksRule() {
        return (label, text) -> Validator.requireMarks(label, text,
                Constants.MAX_EXTERNAL_MARKS);
    }

    /** Accepts anything, but keeps optional text within the column width. */
    public static Rule optionalRule() {
        return (label, text) -> Validator.optionalText(label, text, 255);
    }

    /** Accepts any value, for a field that is displayed but not edited. */
    public static Rule anyRule() {
        return (label, text) -> {
            // deliberately no check
        };
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /** The work performed when Save is pressed. */
    @FunctionalInterface
    public interface SaveAction {
        void save(Map<String, String> values) throws AppException;
    }

    /** A field, its label and its rule, kept together. */
    private static final class Field {

        private final String label;
        private final JComponent component;
        private Rule rule;

        private Field(String label, JComponent component, Rule rule) {
            this.label = label;
            this.component = component;
            this.rule = rule;
        }

        /** @return null when valid, or the sentence explaining the failure */
        private String problem() {
            if (rule == null) {
                return null;
            }
            try {
                rule.check(label, readValue(component));
                return null;
            } catch (AppException e) {
                return e.getMessage();
            } catch (RuntimeException e) {
                return "That value is not valid.";
            }
        }
    }
}
