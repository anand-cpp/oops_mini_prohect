package com.academic.management.ui;

import com.academic.management.exception.AppException;
import com.academic.management.model.User;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiErrors;
import com.academic.management.util.AppLogger;
import com.academic.management.util.Constants;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The sign-in screen.
 *
 * <h2>Why authentication is a frame, not a panel</h2>
 * Nothing else is reachable until someone signs in, so the login window owns
 * the whole application rather than sitting inside it. A user who closes it
 * closes the program, which is the honest behaviour: there is no session to
 * return to, and no partial state to restore.
 *
 * <h2>What this class deliberately does not do</h2>
 * It does not hash anything, count failed attempts or judge password
 * length. All of that belongs to
 * {@link com.academic.management.service.AuthenticationService}; this screen
 * collects two strings and shows whatever that service says. The typed
 * password is passed to the service, cleared from the field, and never
 * logged - only the outcome is.
 */
public final class LoginFrame extends javax.swing.JFrame {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = AppLogger.getLogger(LoginFrame.class);

    private static final Dimension FORM_FIELD = new Dimension(300, 32);
    private static final ColorRule TEXT_ON_NAVY_MUTED = new ColorRule(0xA8, 0xB6, 0xD0);
    private static final ColorRule TEXT_ON_NAVY_FAINT = new ColorRule(0x76, 0x86, 0xA8);

    private final transient ServiceRegistry services;
    private final JTextField usernameField = Theme.styleInput(new JTextField());
    private final JPasswordField passwordField = Theme.styleInput(new JPasswordField());
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton signInButton = Theme.button("Sign in", Theme.PRIMARY);

    public LoginFrame(ServiceRegistry services) {
        super(Constants.APP_NAME);
        this.services = services;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(new Dimension(880, 520));
        setMinimumSize(new Dimension(880, 520));
        setLocationRelativeTo(null);
        setIconImage(WindowIcons.appIcon());
        setContentPane(buildContent());
        wireKeyboard();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAGE);
        root.add(buildBrandPanel(), BorderLayout.WEST);
        root.add(buildFormPanel(), BorderLayout.CENTER);
        return root;
    }

    /**
     * The coloured half of the window.
     *
     * <p>It carries the product name and the demo credentials, because the
     * commonest first-run problem with an application like this is not
     * knowing which sign-in to use.
     */
    private JPanel buildBrandPanel() {
        JPanel brand = new JPanel(new GridBagLayout());
        brand.setBackground(Theme.NAVY);
        brand.setPreferredSize(new Dimension(360, 420));
        brand.setBorder(Theme.padding(Theme.PAD_LARGE * 2));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel(Constants.APP_NAME);
        title.setFont(Theme.H1);
        title.setForeground(Theme.TEXT_ON_NAVY);
        brand.add(title, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(Theme.PAD, 0, 0, 0);
        brand.add(Theme.captionOnDark("Students, faculty, courses, attendance and results",
                TEXT_ON_NAVY_MUTED.color()), constraints);

        constraints.gridy++;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.insets = new Insets(Theme.PAD_LARGE, 0, Theme.PAD_LARGE, 0);
        brand.add(Theme.badge("Swing · JDBC · MySQL", new java.awt.Color(0x6E, 0xD3, 0xA6)),
                constraints);

        constraints.gridy++;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, 0);
        brand.add(Theme.captionOnDark(Constants.APP_VERSION, TEXT_ON_NAVY_FAINT.color()),
                constraints);
        return brand;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Theme.PAGE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.CARD);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                Theme.padding(Theme.PAD_LARGE * 2)));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JLabel heading = new JLabel("Sign in");
        heading.setFont(Theme.H2);
        heading.setForeground(Theme.TEXT);
        form.add(heading, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(4, 0, Theme.PAD_LARGE, 0);
        form.add(Theme.caption("Use the account issued to you by the administrator."), constraints);

        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.insets = new Insets(0, 0, 4, 0);
        form.add(Theme.fieldLabel("Username"), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, Theme.PAD, 0);
        usernameField.setPreferredSize(FORM_FIELD);
        form.add(usernameField, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 4, 0);
        form.add(Theme.fieldLabel("Password"), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, Theme.GAP, 0);
        passwordField.setPreferredSize(FORM_FIELD);
        form.add(passwordField, constraints);

        errorLabel.setFont(Theme.SMALL);
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setHorizontalAlignment(SwingConstants.LEFT);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, Theme.PAD, 0);
        form.add(errorLabel, constraints);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.setOpaque(false);
        JButton exit = Theme.secondaryButton("Exit");
        exit.addActionListener(event -> dispose());
        buttons.add(exit);
        buttons.add(signInButton);
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(0, 0, 0, 0);
        form.add(buttons, constraints);

        signInButton.addActionListener(event -> signIn());
        wrapper.add(form);
        return wrapper;
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /**
     * Enter signs in from either field, and Escape closes the window.
     *
     * <p>Enter-to-submit is not decoration: the login form is the one place
     * a user types into a password field in a desktop application, and
     * requiring the mouse to reach a button there is needless friction.
     */
    private void wireKeyboard() {
        bind("signIn", KeyEvent.VK_ENTER, () -> signIn());
        bind("exit", KeyEvent.VK_ESCAPE, this::dispose);
    }

    private void bind(String actionKey, int keyCode, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0), actionKey);
        getRootPane().getActionMap().put(actionKey, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    /**
     * Verifies the credentials and opens the main window.
     *
     * <p>The authentication call runs on a worker thread because it is a
     * database round trip, and running it inline would freeze the window.
     * The password field is cleared on both the success and the failure
     * path, so a rejected attempt never leaves it on screen for the next
     * person at the machine to read.
     */
    private void signIn() {
        String username = usernameField.getText().trim();
        char[] typed = passwordField.getPassword();
        if (username.isEmpty() || typed.length == 0) {
            showError("Enter both a username and a password.");
            return;
        }
        // The service takes a String because that is what the hashing
        // helper is declared in terms of; the char[] is zeroed immediately
        // so the original keyboard buffer does not linger either.
        String password = new String(typed);
        java.util.Arrays.fill(typed, '\0');

        errorLabel.setText(" ");
        setBusy(true);

        UiErrors.runAsync(this, "Could not sign in",
                () -> services.authentication().authenticate(username, password),
                user -> {
                    setBusy(false);
                    passwordField.setText("");
                    onAuthenticated(user);
                },
                () -> {
                    setBusy(false);
                    passwordField.setText("");
                    passwordField.requestFocusInWindow();
                });
    }

    /** Opens the main window and closes this one. */
    private void onAuthenticated(User user) {
        LOGGER.info("Signed in as " + user.getUsername() + " (" + user.getRole() + ")");
        new MainFrame(services, user).setVisible(true);
        dispose();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        passwordField.setText("");
        passwordField.requestFocusInWindow();
    }

    private void setBusy(boolean busy) {
        signInButton.setEnabled(!busy);
        signInButton.setText(busy ? "Signing in…" : "Sign in");
    }

    /**
     * Warns on the login screen if the user table is empty, which means the
     * schema exists but the sample data never was loaded and there is no
     * account to sign in with.
     *
     * <p>Runs off the event thread. Called from {@link #showLogin()} rather
     * than the constructor for two reasons: a database round trip on the
     * event thread freezes the whole window at launch, and passing a
     * half-built frame to an asynchronous call is how a callback ends up
     * seeing fields that are still null.
     */
    private void warnIfNoAccount() {
        UiErrors.runAsync(this, "Could not check for user accounts",
                () -> {
                    try {
                        return services.authentication().hasAnyAccount();
                    } catch (AppException e) {
                        // A courtesy check only, and the sign-in attempt will
                        // report a real failure. Answering "there are
                        // accounts" leaves the screen alone rather than
                        // showing a dialog the user cannot act on.
                        LOGGER.log(Level.WARNING,
                                "Could not check whether any accounts exist.", e);
                        return true;
                    }
                },
                hasAnyAccount -> {
                    if (!hasAnyAccount) {
                        showError("No user accounts exist. "
                                + "Load database/sample_data.sql, then restart.");
                    }
                });
    }

    /**
     * Shows the window with the username field focused.
     *
     * <p>Named {@code showLogin} rather than {@code show} because
     * {@code java.awt.Window} has a deprecated {@code show()} of its own;
     * a method that silently shadowed a deprecated one from a supertype is
     * a trap for the next person to change this call.
     */
    public void showLogin() {
        setVisible(true);
        usernameField.requestFocusInWindow();
        warnIfNoAccount();
    }

    /** An RGB triple, so the two muted shades on the navy panel read as data. */
    private record ColorRule(int red, int green, int blue) {
        private java.awt.Color color() {
            return new java.awt.Color(red, green, blue);
        }
    }
}
