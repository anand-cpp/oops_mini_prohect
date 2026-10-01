package com.academic.management.ui.common;

import com.academic.management.util.Constants;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.SwingConstants;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * One place for every colour, font and spacing value the screens share.
 *
 * <h2>Why not a look-and-feel class</h2>
 * Swing's cross-platform look and feel is what produces the grey-on-grey
 * default appearance, and the quickest way to make an application look
 * deliberate is to stop letting the platform decide. Centralising the
 * palette here means a screen can never introduce a shade that disagrees
 * with the rest of the application, and a change of brand is an edit in one
 * file rather than a search across twenty.
 */
public final class Theme {

    // ------------------------------------------------------------------
    // Palette
    // ------------------------------------------------------------------

    /** Application chrome: the sidebar and the top bar. */
    public static final Color NAVY = new Color(0x1F, 0x2A, 0x44);
    public static final Color NAVY_HOVER = new Color(0x2C, 0x3A, 0x5C);
    public static final Color NAVY_SELECTED = new Color(0x3A, 0x7B, 0xD5);

    /** Page background and card surface. */
    public static final Color PAGE = new Color(0xF4, 0xF6, 0xF9);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xD8, 0xDE, 0xE7);
    public static final Color BORDER_STRONG = new Color(0xB4, 0xBE, 0xCD);

    /** Text. */
    public static final Color TEXT = new Color(0x1A, 0x1F, 0x2B);
    public static final Color TEXT_MUTED = new Color(0x63, 0x6E, 0x82);
    public static final Color TEXT_ON_NAVY = new Color(0xE8, 0xED, 0xF5);

    /** Actions. */
    public static final Color PRIMARY = new Color(0x2F, 0x6F, 0xD6);
    public static final Color PRIMARY_HOVER = new Color(0x25, 0x5C, 0xB4);
    public static final Color SUCCESS = new Color(0x1E, 0x8E, 0x5A);
    public static final Color DANGER = new Color(0xC0, 0x39, 0x2B);
    public static final Color WARNING = new Color(0xB8, 0x7A, 0x00);

    /** Table. */
    public static final Color TABLE_HEADER = new Color(0xE9, 0xEE, 0xF5);
    public static final Color TABLE_ROW_ALT = new Color(0xFA, 0xFB, 0xFD);
    public static final Color TABLE_SELECTION = new Color(0xD3, 0xE3, 0xFB);

    // ------------------------------------------------------------------
    // Type scale
    // ------------------------------------------------------------------

    public static final Font H1 = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font H2 = new Font("Segoe UI", Font.BOLD, 17);
    public static final Font H3 = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font LABEL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font MONO = new Font("Consolas", Font.PLAIN, 13);

    /** Tabular figures line up in a column; Segoe UI's default ones do not. */
    public static final Font NUMBER =
            new Font("Segoe UI", Font.PLAIN, 13);

    // ------------------------------------------------------------------
    // Spacing
    // ------------------------------------------------------------------

    public static final int GAP = 8;
    public static final int PAD = 16;
    public static final int PAD_LARGE = 24;

    /** The application's fixed-size window, in logical pixels. */
    public static final Dimension WINDOW = new Dimension(1360, 840);
    public static final Dimension DIALOG = new Dimension(520, 260);
    public static final Dimension WIDE_DIALOG = new Dimension(620, 320);

    private Theme() {
        // constants only
    }

    // ------------------------------------------------------------------
    // Component factories
    // ------------------------------------------------------------------

    /** A filled action button. */
    public static JButton button(String text, Color fill) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setForeground(Color.WHITE);
        button.setBackground(fill);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        button.setRolloverEnabled(true);
        // The change event's source is the button's ButtonModel, not the
        // button, so the button has to be captured from the enclosing
        // scope rather than cast out of the event.
        button.getModel().addChangeListener(e -> {
            boolean rollover = button.getModel().isRollover();
            boolean pressed = button.getModel().isPressed();
            button.setBackground(pressed || rollover ? fill.darker() : fill);
        });
        return button;
    }

    /** A secondary button: outlined rather than filled. */
    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setForeground(TEXT);
        button.setBackground(CARD);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STRONG),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getModel().addChangeListener(e ->
                button.setBackground(button.getModel().isRollover() ? PAGE : CARD));
        return button;
    }

    /** A destructive action, coloured but still a normal button. */
    public static JButton dangerButton(String text) {
        return button(text, DANGER);
    }

    /** A page or card title. */
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(H2);
        label.setForeground(TEXT);
        return label;
    }

    /** A muted explanatory line. */
    public static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL);
        label.setForeground(TEXT_MUTED);
        return label;
    }

    /**
     * A muted line for use on the navy chrome.
     *
     * <p>The muted grey is unreadable on navy, so text on that background
     * needs its own colour rather than a lower opacity - opacity would let
     * the navy show through and shift the shade again.
     */
    public static JLabel captionOnDark(String text, Color foreground) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL);
        label.setForeground(foreground);
        return label;
    }

    /** The label above an input field. */
    public static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL);
        label.setForeground(TEXT_MUTED);
        return label;
    }

    /** A section heading inside a card. */
    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(H3);
        label.setForeground(TEXT);
        return label;
    }

    /**
     * A white panel with a hairline border and rounded corners, used for
     * every block of content so the screens share one visual unit.
     */
    public static JPanel card() {
        JPanel panel = new JPanel();
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)));
        return panel;
    }

    /** An empty border, named so layouts read as intent rather than numbers. */
    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static Border padding(int all) {
        return BorderFactory.createEmptyBorder(all, all, all, all);
    }

    /** The page background every panel sits on. */
    public static JPanel page() {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setBackground(PAGE);
        return panel;
    }

    /**
     * Wraps a table in a scroll pane with consistent styling: a filled
     * header, alternating row stripes, and row heights that are actually
     * clickable rather than 16 pixels tall.
     */
    public static JScrollPane tableScroll(JTable table) {
        table.setFont(BODY);
        table.setForeground(TEXT);
        table.setBackground(CARD);
        table.setGridColor(BORDER);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setRowHeight(30);
        table.setSelectionBackground(TABLE_SELECTION);
        table.setSelectionForeground(TEXT);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        JTableHeader header = table.getTableHeader();
        header.setFont(BODY_BOLD);
        header.setBackground(TABLE_HEADER);
        header.setForeground(TEXT);
        header.setPreferredSize(new Dimension(header.getWidth(), 36));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_STRONG));

        // Strip the default opaque-only border so the colours above show.
        table.setBorder(BorderFactory.createLineBorder(BORDER));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(CARD);
        scroll.setOpaque(false);
        return scroll;
    }

    /** A renderer that right-aligns numbers so decimal points line up. */
    public static TableCellRenderer numericCellRenderer() {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, selected, focused, row, column);
                setHorizontalAlignment(RIGHT);
                setFont(NUMBER);
                if (!selected) {
                    component.setBackground(row % 2 == 0 ? CARD : TABLE_ROW_ALT);
                    component.setForeground(TEXT);
                }
                return component;
            }
        };
        return renderer;
    }

    /** A renderer that bolds a "Pass"/"Fail" style verdict in its colour. */
    public static TableCellRenderer verdictCellRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, selected, focused, row, column);
                setHorizontalAlignment(CENTER);
                if (!selected && value != null) {
                    String text = value.toString();
                    if (text.startsWith("Fail") || "F".equals(text)
                            || "Not Met".equals(text) || "Irregular".equals(text)) {
                        component.setForeground(DANGER);
                        setFont(BODY_BOLD);
                    } else if (text.startsWith("Pass") || "Regular".equals(text)
                            || "Good standing".equals(text)) {
                        component.setForeground(SUCCESS);
                        setFont(BODY_BOLD);
                    } else {
                        component.setForeground(TEXT_MUTED);
                    }
                }
                return component;
            }
        };
    }

    /** A left-aligned text renderer that respects the row stripes. */
    public static TableCellRenderer textCellRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, selected, focused, row, column);
                setHorizontalAlignment(LEADING);
                if (!selected) {
                    component.setBackground(row % 2 == 0 ? CARD : TABLE_ROW_ALT);
                    component.setForeground(TEXT);
                }
                return component;
            }
        };
    }

    /**
     * A tinted pill for the dashboard, for example "3 enrolments".
     *
     * <p>The background is the tint faded towards white rather than the
     * tint with an alpha, because an opaque label on a transparent
     * background composites against whatever is behind it and comes out a
     * different shade on every card.
     */
    public static JLabel badge(String text, Color tint) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL);
        label.setForeground(tint);
        label.setOpaque(true);
        label.setBackground(fade(tint, 0.88f));
        label.setBorder(padding(4, 10, 4, 10));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    /** Blends a colour towards white, keeping the text on it readable. */
    public static Color fade(Color color, float towardsWhite) {
        float clamped = Math.max(0f, Math.min(1f, towardsWhite));
        return new Color(
                Math.round(color.getRed() + (255 - color.getRed()) * clamped),
                Math.round(color.getGreen() + (255 - color.getGreen()) * clamped),
                Math.round(color.getBlue() + (255 - color.getBlue()) * clamped));
    }

    /** A label showing an application-wide rule, for example the grading scale. */
    public static JLabel ruleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL);
        label.setForeground(TEXT_MUTED);
        return label;
    }

    /**
     * A two-column form: labels on the left, fields on the right, with
     * consistent alignment and gaps.
     *
     * <p>Built incrementally through {@link Form#add} so each dialog
     * declares its own fields in reading order without repeating grid
     * arithmetic.
     */
    public static final class Form {

        private final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        public Form() {
            panel.setBackground(CARD);
            panel.setBorder(padding(0));
        }

        /** Adds a labelled field spanning both columns. */
        public Form add(String label, JComponent field) {
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.anchor = GridBagConstraints.LINE_START;
            labelConstraints.insets = new Insets(0, 0, GAP, 12);
            panel.add(fieldLabel(label), labelConstraints);

            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 1;
            fieldConstraints.gridy = row;
            fieldConstraints.weightx = 1;
            fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            fieldConstraints.insets = new Insets(0, 0, PAD, 0);
            panel.add(field, fieldConstraints);

            row++;
            return this;
        }

        /** Adds a row where the field sits under its own label. */
        public Form addStacked(String label, JComponent field) {
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.gridwidth = 2;
            labelConstraints.anchor = GridBagConstraints.LINE_START;
            labelConstraints.insets = new Insets(0, 0, 4, 0);
            panel.add(fieldLabel(label), labelConstraints);
            row++;

            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 0;
            fieldConstraints.gridy = row;
            fieldConstraints.gridwidth = 2;
            fieldConstraints.weightx = 1;
            fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            fieldConstraints.insets = new Insets(0, 0, PAD, 0);
            panel.add(field, fieldConstraints);
            row++;
            return this;
        }

        /** Two fields side by side, for related short values. */
        public Form addPair(String leftLabel, JComponent leftField,
                            String rightLabel, JComponent rightField) {
            GridBagConstraints leftLabelConstraints = new GridBagConstraints();
            leftLabelConstraints.gridx = 0;
            leftLabelConstraints.gridy = row;
            leftLabelConstraints.anchor = GridBagConstraints.LINE_START;
            leftLabelConstraints.insets = new Insets(0, 0, 4, 6);
            panel.add(fieldLabel(leftLabel), leftLabelConstraints);

            GridBagConstraints leftFieldConstraints = new GridBagConstraints();
            leftFieldConstraints.gridx = 0;
            leftFieldConstraints.gridy = row + 1;
            leftFieldConstraints.weightx = 0.5;
            leftFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            leftFieldConstraints.insets = new Insets(0, 0, PAD, 6);
            panel.add(leftField, leftFieldConstraints);

            GridBagConstraints rightLabelConstraints = new GridBagConstraints();
            rightLabelConstraints.gridx = 1;
            rightLabelConstraints.gridy = row;
            rightLabelConstraints.anchor = GridBagConstraints.LINE_START;
            rightLabelConstraints.insets = new Insets(0, 0, 4, 0);
            panel.add(fieldLabel(rightLabel), rightLabelConstraints);

            GridBagConstraints rightFieldConstraints = new GridBagConstraints();
            rightFieldConstraints.gridx = 1;
            rightFieldConstraints.gridy = row + 1;
            rightFieldConstraints.weightx = 0.5;
            rightFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            rightFieldConstraints.insets = new Insets(0, 0, PAD, 0);
            panel.add(rightField, rightFieldConstraints);

            row += 2;
            return this;
        }

        /** A full-width note between fields. */
        public Form addNote(JComponent component) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = row;
            constraints.gridwidth = 2;
            constraints.weightx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(0, 0, PAD, 0);
            panel.add(component, constraints);
            row++;
            return this;
        }

        /** A flexible gap that pushes following content to the bottom. */
        public Form addFiller() {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = row;
            constraints.gridwidth = 2;
            constraints.weighty = 1;
            constraints.fill = GridBagConstraints.BOTH;
            panel.add(javax.swing.Box.createVerticalGlue(), constraints);
            row++;
            return this;
        }

        public JPanel getPanel() {
            return panel;
        }
    }

    /** Styles an input field consistently. */
    public static <T extends JComponent> T styleInput(T field) {
        field.setFont(BODY);
        field.setForeground(TEXT);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STRONG),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        field.setOpaque(true);
        return field;
    }

    /** The same border, in the error colour, for a field that failed. */
    public static Border errorBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DANGER, 2),
                BorderFactory.createEmptyBorder(5, 7, 5, 7));
    }

    /** A small coloured square used on dashboard cards. */
    public static JComponent accentBar(Color color) {
        JPanel bar = new JPanel();
        bar.setBackground(color);
        bar.setPreferredSize(new Dimension(4, 40));
        bar.setOpaque(true);
        return bar;
    }

    /**
     * Applies the background of the page to a component tree.
     *
     * <p>Called once on the frame so no screen has to remember to set its
     * own background, which is how a stray white strip ends up under a
     * card.
     */
    public static void paintRecursively(Component component, Color background) {
        if (component instanceof JComponent swing && swing.getBackground() == null) {
            swing.setBackground(background);
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                paintRecursively(child, background);
            }
        }
    }

    /**
     * Installs a plain look and feel so the palette above is what the user
     * actually sees. Swing's cross-platform defaults are the reason an
     * application looks unfinished out of the box.
     */
    public static void installLookAndFeel() {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info
                    : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    javax.swing.UIManager.put("control", new ColorUIResource(CARD));
                    javax.swing.UIManager.put("nimbusBase", new ColorUIResource(NAVY));
                    javax.swing.UIManager.put("text", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("Table.showGrid", Boolean.TRUE);
                    return;
                }
            }
        } catch (Exception e) {
            // The cross-platform look and feel is a perfectly usable
            // fallback; the explicit colours above carry the design.
        }
    }

    /** The version string shown in the about box. */
    public static String version() {
        return Constants.APP_NAME + " " + Constants.APP_VERSION;
    }
}
