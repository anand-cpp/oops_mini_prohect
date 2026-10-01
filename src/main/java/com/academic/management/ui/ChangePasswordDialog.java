package com.academic.management.ui;

import com.academic.management.model.User;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.util.AppLogger;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Changes the signed-in user's own password.
 *
 * <h2>Why the current password is required</h2>
 * This dialog runs inside an already-authenticated session, so in principle
 * the user has proved who they are. Requiring the current password anyway
 * means a walk-up attacker who gets the application open cannot lock the
 * real owner out permanently. The service verifies it either way; this
 * screen is only making the requirement visible up front.
 *
 * <p>The new password is entered twice because a typo in a password field
 * with no visibility is otherwise discovered only at the next sign-in. The
 * confirmation field is checked here rather than in the service, since
 * "these two do not match" is a form error and not a rule about passwords.
 */
public final class ChangePasswordDialog extends javax.swing.JDialog {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = AppLogger.getLogger(ChangePasswordDialog.class);

    private final transient ServiceRegistry services;
    private final transient User user;
    private final JPasswordField currentField = Theme.styleInput(new JPasswordField());
    private final JPasswordField newField = Theme.styleInput(new JPasswordField());
    private final JPasswordField confirmField = Theme.styleInput(new JPasswordField());
    private final javax.swing.JButton saveButton = Theme.button("Change password",
            Theme.PRIMARY);

    public ChangePasswordDialog(JFrame owner, ServiceRegistry services, User user) {
        super(owner, "Change password", ModalityType.APPLICATION_MODAL);
        this.services = services;
        this.user = user;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(buildContent());
        wireEnterToSave();
        getRootPane().setDefaultButton(saveButton);
        pack();
        setSize(Theme.DIALOG);
        setMinimumSize(Theme.DIALOG);
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.CARD);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                Theme.padding(Theme.PAD_LARGE)));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        javax.swing.JLabel heading = new javax.swing.JLabel("Change password");
        heading.setFont(Theme.H2);
        heading.setForeground(Theme.TEXT);
        form.add(heading, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(4, 0, Theme.PAD_LARGE, 0);
        form.add(Theme.caption("Signed in as " + user.getUsername() + " ("
                + user.getRole().getLabel() + ")"), constraints);

        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.insets = new Insets(0, 0, 4, 0);
        form.add(Theme.fieldLabel("Current password"), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, Theme.PAD, 0);
        currentField.setPreferredSize(new Dimension(240, 30));
        form.add(currentField, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 4, 0);
        form.add(Theme.fieldLabel("New password"), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 4, 0);
        form.add(Theme.ruleLabel("At least 6 characters, and different from the current one."),
                constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, Theme.PAD, 0);
        newField.setPreferredSize(new Dimension(240, 30));
        form.add(newField, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 4, 0);
        form.add(Theme.fieldLabel("Confirm new password"), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, Theme.PAD, 0);
        confirmField.setPreferredSize(new Dimension(240, 30));
        form.add(confirmField, constraints);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.setOpaque(false);
        javax.swing.JButton cancel = Theme.secondaryButton("Cancel");
        cancel.addActionListener(event -> dispose());
        buttons.add(cancel);
        buttons.add(saveButton);
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(0, 0, 0, 0);
        form.add(buttons, constraints);

        saveButton.addActionListener(event -> save());
        return form;
    }

    private void wireEnterToSave() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
                        "save");        getRootPane().getActionMap().put("save", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                save();
            }
        });
    }

    /**
     * Validates the two new fields against each other, then hands the change
     * to the service.
     *
     * <p>The service call is on a worker thread; until it returns the
     * fields are disabled, because a second attempt would be a second
     * hash of the same new value and would be reported as "the new password
     * must be different" for no good reason.
     */
    private void save() {
        char[] current = currentField.getPassword();
        char[] replacement = newField.getPassword();
        char[] confirmation = confirmField.getPassword();

        if (current.length == 0 || replacement.length == 0) {
            UiErrors.show(this, "Change password", "Enter your current password and a new one.");
            return;
        }
        if (!java.util.Arrays.equals(replacement, confirmation)) {
            UiErrors.showWarning(this, "Change password",
                    "The two new passwords do not match.");
            confirmField.setText("");
            confirmField.requestFocusInWindow();
            return;
        }

        String currentText = new String(current);
        String newText = new String(replacement);
        java.util.Arrays.fill(current, '\0');
        java.util.Arrays.fill(replacement, '\0');
        java.util.Arrays.fill(confirmation, '\0');

        setBusy(true);
        UiErrors.runAsync(this, "Could not change the password", () -> {
            services.authentication().changePassword(user.getUsername(), currentText, newText);
            return Boolean.TRUE;
        }, done -> {
            setBusy(false);
            LOGGER.info("Password changed from the account menu for " + user.getUsername());
            UiErrors.info(this, "Password changed",
                    "Your password has been changed. Use it the next time you sign in.");
            dispose();
        }, () -> {
            setBusy(false);
            clearFields();
            currentField.requestFocusInWindow();
        });
    }

    private void clearFields() {
        currentField.setText("");
        newField.setText("");
        confirmField.setText("");
    }

    private void setBusy(boolean busy) {
        saveButton.setEnabled(!busy);
        saveButton.setText(busy ? "Changing…" : "Change password");
        currentField.setEnabled(!busy);
        newField.setEnabled(!busy);
        confirmField.setEnabled(!busy);
    }
}
