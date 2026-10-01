package com.academic.management.ui;

import com.academic.management.exception.ValidationException;
import com.academic.management.model.User;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.common.FormDialog;
import com.academic.management.util.AppLogger;
import com.academic.management.util.Validator;

import javax.swing.JFrame;
import javax.swing.JPasswordField;
import java.util.logging.Logger;

/**
 * Changes the signed-in user's own password.
 *
 * <h2>Why this is a {@link FormDialog} and not a dialog of its own</h2>
 * It used to be one. That meant a second set of field styles, a second
 * validation approach, and a second save path - and it had already drifted:
 * this dialog disabled its fields while saving and reported a refusal in a
 * separate dialog, where every other edit form keeps the form on screen and
 * puts the problem in a banner above the fields. Two ways to ask the same
 * question is one more way for the wrong one to be answered.
 *
 * <p>So this class is now a declaration and nothing else: which three fields,
 * which rules, and what to do when they are all valid. The live validation,
 * the banner, the frozen fields, the busy save button and the rule that the
 * values are read on the event thread before the worker starts all come from
 * the shared dialog, so a password change behaves like every other save in
 * the application.
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
 * confirmation is compared here rather than in the service, since "these two
 * do not match" is a form error and not a rule about passwords. What a
 * password must be - its length, its difference from the old one - is still
 * the service's business, and the form does not second-guess it.
 */
public final class ChangePasswordDialog {

    private static final Logger LOGGER = AppLogger.getLogger(ChangePasswordDialog.class);

    private static final String CURRENT = "Current password";
    private static final String REPLACEMENT = "New password";
    private static final String CONFIRMATION = "Confirm new password";

    private final transient FormDialog dialog;

    public ChangePasswordDialog(JFrame owner, ServiceRegistry services, User user) {
        this.dialog = new FormDialog(owner, "Change password", "Change password", values -> {
            services.authentication().changePassword(
                    user.getUsername(),
                    FormDialog.required(values, CURRENT),
                    FormDialog.required(values, REPLACEMENT));
            LOGGER.info("Password changed from the account menu for " + user.getUsername());
        });

        dialog.addPasswordField(CURRENT, 24, FormDialog.requiredRule());
        JPasswordField replacement = dialog.addPasswordField(
                REPLACEMENT, 24, FormDialog.requiredRule());
        dialog.addNote("At least 6 characters, and different from the current one.");
        dialog.addPasswordField(CONFIRMATION, 24, (label, typed) -> {
            Validator.requireText(label, typed);
            // Safe to read the other field here: a rule is only ever run
            // from revalidate(), which is called from the document listener
            // and from the save callbacks - all of them on the event thread.
            String other = new String(replacement.getPassword()).trim();
            if (!typed.equals(other)) {
                throw new ValidationException("The two new passwords do not match.");
            }
        });
    }

    /** Shows the dialog, blocking until it closes. */
    public void showDialog() {
        dialog.showDialog();
    }
}