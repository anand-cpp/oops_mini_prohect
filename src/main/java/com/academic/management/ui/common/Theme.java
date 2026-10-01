package com.academic.management.ui.common;

import com.academic.management.util.Constants;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
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
 *
 * <h2>Why every shade in this file has a contrast receipt</h2>
 * The palette is not a set of colours someone liked. Each entry below names
 * the pair it was chosen for and the ratio that pair achieves, because a
 * token that exists only to look harmonious is exactly how an unreadable
 * screen gets shipped. The pairs that matter are:
 *
 * <table border="1">
 *   <caption>Contrast pairs enforced by this palette</caption>
 *   <tr><th>Foreground</th><th>Background</th><th>Ratio</th><th>Where</th></tr>
 *   <tr><td>{@code TEXT}</td><td>{@code CARD}</td><td>16.47</td><td>body text</td></tr>
 *   <tr><td>{@code TEXT_MUTED}</td><td>{@code TABLE_HEADER}</td><td>5.13</td><td>column titles</td></tr>
 *   <tr><td>{@code TEXT_ON_NAVY}</td><td>{@code NAVY}</td><td>12.13</td><td>rail labels</td></tr>
 *   <tr><td>{@code TEXT_ON_NAVY_MUTED}</td><td>{@code NAVY}</td><td>5.44</td><td>rail subtitles</td></tr>
 *   <tr><td>{@code NAVY}</td><td>{@code NAVY_SELECTED}</td><td>12.09</td><td>selected rail entry</td></tr>
 *   <tr><td>white</td><td>{@code PRIMARY}</td><td>4.81</td><td>primary button label</td></tr>
 *   <tr><td>white</td><td>{@code DANGER}</td><td>6.52</td><td>destructive button label</td></tr>
 *   <tr><td>{@code SUCCESS}</td><td>{@code TABLE_ROW_ALT}</td><td>5.65</td><td>pass verdict</td></tr>
 *   <tr><td>{@code DISABLED_TEXT}</td><td>{@code DISABLED_BACKGROUND}</td><td>4.99</td><td>disabled buttons</td></tr>
 *   <tr><td>{@code BORDER_STRONG}</td><td>{@code PAGE}</td><td>3.36</td><td>every component outline</td></tr>
 *   <tr><td>{@code FOCUS_RING}</td><td>{@code NAVY}</td><td>3.38</td><td>focus on the rail</td></tr>
 *   <tr><td>{@code FOCUS_RING}</td><td>{@code PAGE}</td><td>3.90</td><td>focus on a toolbar</td></tr>
 *   <tr><td>{@code DANGER}</td><td>{@code ERROR_BACKGROUND}</td><td>5.70</td><td>validation banner</td></tr>
 *   <tr><td>{@code TEXT_MUTED}</td><td>{@code LOCKED_BACKGROUND}</td><td>6.65</td><td>read-only fields</td></tr>
 * </table>
 *
 * <p>The one deliberate asymmetry is the focus ring: a single colour that
 * clears 3:1 against white, the page grey and the navy rail at once, which is
 * why {@link #FOCUS_RING} is never allowed to sit against a filled button -
 * the ring is painted in the component's own outer 2 pixels, so it is always
 * adjacent to the surrounding background rather than to the control's fill.
 */
public final class Theme {

    // ------------------------------------------------------------------
    // Palette
    // ------------------------------------------------------------------

    /** Application chrome: the sidebar and the top bar. */
    public static final Color NAVY = new Color(0x1F, 0x2A, 0x44);
    public static final Color NAVY_HOVER = new Color(0x2C, 0x3A, 0x5C);

    /**
     * The recessed block at the foot of the rail, one step below {@link #NAVY}
     * so the account details read as a separate region rather than as more
     * navigation.
     */
    public static final Color NAVY_INSET = new Color(0x1A, 0x24, 0x3A);

    /**
     * The selected navigation entry.
     *
     * <p>A pale blue rather than a saturated one, because the alternative
     * cannot satisfy the two rules at once: navy is already dark enough that
     * a mid-blue pill carrying white text tops out around 3.6:1, and any blue
     * light enough to give white text 4.5:1 stops being distinguishable from
     * the navy behind it. Inverting the pill - light surface, navy text -
     * clears both at over 12:1.
     */
    public static final Color NAVY_SELECTED = new Color(0xE4, 0xED, 0xFB);

    /** Page background and card surface. */
    public static final Color PAGE = new Color(0xF4, 0xF6, 0xF9);
    public static final Color CARD = Color.WHITE;

    /**
     * Interior separators only: table cell rules and the header underline.
     *
     * <p>1.25:1 against the page. It is not a boundary a user has to see, it
     * is a hint that a grid exists, and a 3:1 rule between every pair of cells
     * would turn a readable table into graph paper.
     */
    public static final Color BORDER = new Color(0xD8, 0xDE, 0xE7);

    /**
     * Every outline a user has to be able to see: cards, inputs, dropdowns
     * and table frames. 3.36:1 against the page, 3.64:1 against white.
     */
    public static final Color BORDER_STRONG = new Color(0x7C, 0x87, 0x98);

    /** Text. */
    public static final Color TEXT = new Color(0x1A, 0x1F, 0x2B);

    /**
     * Secondary text. Darker than it looks like it needs to be, because it is
     * used at 12px on the table header and on the selected row as well as on
     * white, and those are the two backgrounds it was failing on.
     */
    public static final Color TEXT_MUTED = new Color(0x5B, 0x64, 0x72);

    public static final Color TEXT_ON_NAVY = new Color(0xE8, 0xED, 0xF5);

    /** Secondary text on navy. 5.44:1 against {@link #NAVY}. */
    public static final Color TEXT_ON_NAVY_MUTED = new Color(0x93, 0xA0, 0xBE);

    /** Actions. */
    public static final Color PRIMARY = new Color(0x2F, 0x6F, 0xD6);

    /**
     * The resting action colour's hover state. Darkening the fill rather than
     * lightening it is deliberate: white on the darker blue is 6.42:1, so a
     * hover can never lower the label's contrast below the resting 4.81:1.
     */
    public static final Color PRIMARY_HOVER = new Color(0x25, 0x5C, 0xB4);

    /** Pressed, one step darker again: 8.53:1 against white. */
    public static final Color PRIMARY_PRESSED = new Color(0x1E, 0x4A, 0x94);

    /** A pass. 5.85:1 against white, 5.65:1 against the row stripe. */
    public static final Color SUCCESS = new Color(0x0F, 0x73, 0x50);

    /** A failure, and the fill of a destructive button. 6.52:1 against white. */
    public static final Color DANGER = new Color(0xB3, 0x27, 0x1B);

    /** Neither yet. 5.93:1 against white. */
    public static final Color WARNING = new Color(0x8A, 0x5A, 0x00);

    /**
     * The only blue that clears 3:1 against the navy rail, so it is what the
     * rail's own accent mark and the focus ring are painted in.
     */
    public static final Color ACCENT_ON_NAVY = new Color(0x3A, 0x7B, 0xD5);

    /**
     * The focus indicator, one colour for the whole application.
     *
     * <p>3.90:1 against the page, 4.22:1 against white, 3.38:1 against navy,
     * so the same ring is legible on a card, on a toolbar and inside the rail.
     */
    public static final Color FOCUS_RING = ACCENT_ON_NAVY;

    /** A control the user cannot currently use. */
    public static final Color DISABLED_BACKGROUND = new Color(0xEA, 0xEE, 0xF4);

    /**
     * 4.99:1 on {@link #DISABLED_BACKGROUND}. A disabled control is exempt
     * from the contrast minimum, but this one is not allowed to use that
     * exemption as a shortcut - the label is still text somebody is reading
     * to work out why the button is dead.
     */
    public static final Color DISABLED_TEXT = new Color(0x5C, 0x66, 0x75);

    /** Table. */
    public static final Color TABLE_HEADER = new Color(0xE9, 0xEE, 0xF5);
    public static final Color TABLE_ROW_ALT = new Color(0xFA, 0xFB, 0xFD);
    public static final Color TABLE_SELECTION = new Color(0xD3, 0xE3, 0xFB);

    /**
     * The fill of an inline validation banner.
     *
     * <p>A tint rather than white, so a rejected value is marked in place
     * rather than in a dialog that hides the field. {@link #DANGER} on it is
     * 5.7:1, so the sentence explaining the problem is as readable as any
     * other text in the window.
     */
    public static final Color ERROR_BACKGROUND = new Color(0xFD, 0xEC, 0xEA);

    /**
     * The fill of a field the user must not change, as distinct from one they
     * could change but have not yet: it reads as settled rather than as
     * broken. {@link #TEXT_MUTED} on it is 6.65:1.
     */
    public static final Color LOCKED_BACKGROUND = new Color(0xF1, 0xF3, 0xF6);

    // ------------------------------------------------------------------
    // Type scale
    // ------------------------------------------------------------------

    public static final Font H1 = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font H2 = new Font("Segoe UI", Font.BOLD, 17);
    public static final Font H3 = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font SMALL = new Font("Segoe UI", Font.PLAIN, 12);

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

    /** How thick the focus ring is, in logical pixels. */
    public static final int FOCUS_THICKNESS = 2;

    private Theme() {
        // constants only
    }

    // ------------------------------------------------------------------
    // Focus
    // ------------------------------------------------------------------

    /**
     * Wraps a border in the application's one focus indicator.
     *
     * <h2>Why the ring sits outside the control's own fill</h2>
     * A ring painted on top of a filled button would have to be a lighter
     * shade of the same blue to be seen at all, and that shade is 1.14:1
     * against the fill it is meant to describe - it would read as a smudge.
     * Painting the ring in the outer {@value #FOCUS_THICKNESS} pixels instead
     * puts it next to whatever the control is sitting on, which is what the
     * contrast requirement is actually about, and it means one colour works on
     * a white card, a grey toolbar and a navy rail.
     *
     * <p>The space is reserved whether or not the control has focus, so
     * nothing on screen moves when focus moves.
     *
     * @param inner the border the component would otherwise have, may be null
     */
    public static Border focusRing(Border inner) {
        return new FocusRingBorder(inner, null);
    }

    /**
     * Wraps a border in a focus ring that follows another component's focus.
     *
     * <p>Used for the scroll pane around a table: the ring belongs to the
     * frame of the whole table, but it must appear when the table inside it
     * has the focus rather than when the pane does, because the pane never
     * takes focus itself.
     *
     * @param inner       the border the pane would otherwise have, may be null
     * @param focusSource the component whose focus drives the ring
     */
    public static Border focusRing(Border inner, JComponent focusSource) {
        return new FocusRingBorder(inner, focusSource);
    }

    /** The 2-pixel ring, painted in the component's outermost pixels. */
    private static final class FocusRingBorder extends AbstractBorder {

        private static final long serialVersionUID = 1L;

        private final transient Border inner;
        private final transient JComponent focusSource;

        private FocusRingBorder(Border inner, JComponent focusSource) {
            this.inner = inner;
            this.focusSource = focusSource;
        }

        private boolean focused(Component component) {
            JComponent owner = focusSource;
            if (owner == null && component instanceof JComponent swing) {
                owner = swing;
            }
            // isFocusOwner rather than hasFocus: a window that is not the
            // active one has no focus owner, and a ring that vanished because
            // the user alt-tabbed away and came back would be a bug.
            return owner != null && owner.isFocusOwner();
        }

        @Override
        public void paintBorder(Component component, Graphics graphics,
                                int x, int y, int width, int height) {
            if (!focused(component)) {
                return;
            }
            Graphics target = graphics.create();
            try {
                target.setColor(FOCUS_RING);
                target.fillRect(x, y, width, FOCUS_THICKNESS);
                target.fillRect(x, y + height - FOCUS_THICKNESS, width, FOCUS_THICKNESS);
                target.fillRect(x, y, FOCUS_THICKNESS, height);
                target.fillRect(x + width - FOCUS_THICKNESS, y, FOCUS_THICKNESS, height);
            } finally {
                target.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component component) {
            Insets nested = inner == null
                    ? EMPTY_INSETS
                    : inner.getBorderInsets(component == null ? PROBE : component);
            return new Insets(
                    nested.top + FOCUS_THICKNESS,
                    nested.left + FOCUS_THICKNESS,
                    nested.bottom + FOCUS_THICKNESS,
                    nested.right + FOCUS_THICKNESS);
        }

        /**
         * The ring is a solid rectangle, which lets Swing clip painting to the
         * interior rather than repainting the whole component.
         */
        @Override
        public boolean isBorderOpaque() {
            return true;
        }
    }

    /**
     * A stand-in for the measuring calls Swing makes before a component has
     * a real parent. Swing's own borders ignore which component they are
     * asked about, so any component gives the right answer.
     */
    private static final Component PROBE = new JPanel();

    private static final Insets EMPTY_INSETS = new Insets(0, 0, 0, 0);

    // ------------------------------------------------------------------
    // Component factories
    // ------------------------------------------------------------------

    /**
     * A filled action button.
     *
     * <p>All four states are set explicitly rather than left to the look and
     * feel: resting, hover, pressed and disabled each name their own fill and
     * label colour, and each pair clears 4.5:1. Hover darkens the fill, which
     * raises the white label's contrast rather than lowering it, so no state
     * can make the label harder to read than the state it came from.
     *
     * @param text  the label
     * @param fill  the resting fill; the hover and pressed fills are derived
     */
    public static JButton button(String text, Color fill) {
        // The two named states are used when the fill is the palette's
        // primary, so the tokens in this file and the button's behaviour
        // cannot drift apart. Any other fill derives its own states, because
        // borrowing the primary's would pair a green fill with a blue hover.
        Color hover = PRIMARY.equals(fill) ? PRIMARY_HOVER : shade(fill, 0.88f);
        Color pressed = PRIMARY.equals(fill) ? PRIMARY_PRESSED : shade(fill, 0.74f);
        return statefulButton(text, fill, hover, pressed, false);
    }

    /** A secondary button: outlined rather than filled. */
    public static JButton secondaryButton(String text) {
        return statefulButton(text, CARD, SECONDARY_HOVER, SECONDARY_PRESSED, true);
    }

    /** A destructive action, coloured but still a normal button. */
    public static JButton dangerButton(String text) {
        return statefulButton(text, DANGER, DANGER_HOVER, DANGER_PRESSED, false);
    }

    /** Hover fill for an outlined button: 1.23:1 against its white resting fill. */
    private static final Color SECONDARY_HOVER = new Color(0xE1, 0xE8, 0xF2);

    /** Pressed fill for an outlined button: 1.40:1 against white. */
    private static final Color SECONDARY_PRESSED = new Color(0xD2, 0xDB, 0xE9);

    /** Hover fill for a destructive button: 7.30:1 against white. */
    private static final Color DANGER_HOVER = new Color(0xA1, 0x23, 0x18);

    /** Pressed fill for a destructive button: 8.24:1 against white. */
    private static final Color DANGER_PRESSED = new Color(0x8F, 0x1F, 0x16);

    /**
     * Builds a button whose every state is defined here.
     *
     * <p>The colours are applied from the button's own model rather than left
     * to the UI delegate, because a delegate that only paints the resting
     * fill leaves the disabled state looking identical to the enabled one -
     * which is the state a user has the least other evidence about.
     *
     * @param outlined whether the button shows its outline
     */
    private static JButton statefulButton(String text, Color rest, Color hover,
                                          Color pressed, boolean outlined) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setRolloverEnabled(true);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorderPainted(outlined);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(outlined
                ? focusRing(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_STRONG),
                        BorderFactory.createEmptyBorder(8, 16, 8, 16)))
                : focusRing(BorderFactory.createEmptyBorder(9, 18, 9, 18)));

        Runnable applyState = () -> {
            if (!button.isEnabled()) {
                button.setBackground(DISABLED_BACKGROUND);
                button.setForeground(DISABLED_TEXT);
                return;
            }
            boolean down = button.getModel().isPressed() || button.getModel().isArmed();
            button.setBackground(down ? pressed
                    : button.getModel().isRollover() ? hover : rest);
            button.setForeground(outlined ? TEXT : Color.WHITE);
        };
        button.getModel().addChangeListener(event -> applyState.run());
        button.addPropertyChangeListener("enabled", event -> applyState.run());
        button.getAccessibleContext().setAccessibleName(text);
        applyState.run();
        return button;
    }

    /** Scales a colour's channels towards black, for the pressed state. */
    private static Color shade(Color color, float towardsBlack) {
        return new Color(
                Math.round(color.getRed() * (1 - towardsBlack)),
                Math.round(color.getGreen() * (1 - towardsBlack)),
                Math.round(color.getBlue() * (1 - towardsBlack)));
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
        label.setFont(SMALL);
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
                BorderFactory.createLineBorder(BORDER_STRONG),
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

    /**
     * Wraps a table in a scroll pane with consistent styling: a filled
     * header, alternating row stripes, and row heights that are actually
     * clickable rather than 16 pixels tall.
     *
     * <p>The pane turns off {@code AUTO_RESIZE_LAST_COLUMN} because that
     * mode resizes the final column to whatever is left over, which on a
     * twelve-column table silently steals the width the first eleven columns
     * needed. Column widths are fitted deliberately instead - see
     * {@link DataTable#fit(int...)}.
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
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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
        // The ring wraps the whole table but answers to the table's focus.
        scroll.setBorder(focusRing(
                BorderFactory.createLineBorder(BORDER_STRONG), table));
        scroll.getViewport().setBackground(CARD);
        scroll.setOpaque(false);
        return scroll;
    }

    /**
     * The border a valid, editable input carries.
     *
     * <p>Named so a screen that clears a validation error has one place to
     * put the field back, rather than rebuilding a compound border by hand
     * and quietly losing the focus ring.
     */
    public static Border inputBorder() {
        return focusRing(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STRONG),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
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
                setFont(BODY);
                return stripe(component, row, selected);
            }
        };
        return renderer;
    }

    /**
     * A renderer that bolds a "Pass"/"Fail" style verdict in its colour.
     *
     * <p>The verdict colour survives the selected row rather than being
     * dropped for the selection foreground: both {@link #SUCCESS} and
     * {@link #DANGER} clear 4.5:1 against {@link #TABLE_SELECTION}, so the
     * colour does not have to be sacrificed to make the selection visible.
     */
    public static TableCellRenderer verdictCellRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, selected, focused, row, column);
                setHorizontalAlignment(CENTER);
                setFont(BODY);
                stripe(component, row, selected);
                if (value != null) {
                    String text = value.toString();
                    if (text.startsWith("Fail") || "F".equals(text)
                            || "Not Met".equals(text)) {
                        setForeground(DANGER);
                        setFont(BODY_BOLD);
                    } else if (text.startsWith("Pass") || "Regular".equals(text)
                            || "Good standing".equals(text)) {
                        setForeground(SUCCESS);
                        setFont(BODY_BOLD);
                    } else if ("Irregular".equals(text)) {
                        setForeground(WARNING);
                        setFont(BODY_BOLD);
                    } else if ("Completed".equals(text)) {
                        setForeground(SUCCESS);
                    } else if ("Withdrawn".equals(text)) {
                        setForeground(DANGER);
                    } else if ("Active".equals(text)) {
                        // The ordinary state, so it is left in body text: the
                        // two terminal states are the ones worth noticing, and
                        // tinting the common case would mean a green table.
                        setForeground(TEXT);
                    } else {
                        setForeground(TEXT_MUTED);
                    }
                }
                return component;
            }
        };
    }

    /** A left-aligned text renderer that respects the row stripes. */
    public static TableCellRenderer textCellRenderer() {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, selected, focused, row, column);
                setHorizontalAlignment(LEADING);
                setFont(BODY);
                return stripe(component, row, selected);
            }
        };
        return renderer;
    }

    /**
     * Paints the alternating row background.
     *
     * <p>Swing's default renderer fills every cell with the table's own
     * background, so a table that declares row stripes paints none unless a
     * renderer says otherwise. When the row is selected the renderer leaves
     * the selection colours alone.
     */
    private static Component stripe(Component component, int row, boolean selected) {
        if (!selected && component instanceof JComponent swing) {
            swing.setBackground(row % 2 == 0 ? CARD : TABLE_ROW_ALT);
            swing.setForeground(TEXT);
        }
        return component;
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

    /** Styles a text input consistently. */
    public static <T extends JComponent> T styleInput(T field) {
        field.setFont(BODY);
        field.setForeground(TEXT);
        field.setBackground(Color.WHITE);
        field.setBorder(inputBorder());
        field.setOpaque(true);
        return field;
    }

    /**
     * Styles a dropdown the same way as a text input.
     *
     * <p>A dropdown has its own UI delegate, which paints its own background
     * and border unless both are set explicitly - which is why a combo left
     * alone next to a styled text field reads as a different control even
     * when the two hold the same kind of value.
     */
    public static <T extends JComboBox<?>> T styleSelect(T combo) {
        combo.setFont(BODY);
        combo.setForeground(TEXT);
        combo.setBackground(Color.WHITE);
        combo.setOpaque(true);
        combo.setBorder(focusRing(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STRONG),
                BorderFactory.createEmptyBorder(5, 6, 5, 6))));
        return combo;
    }

    /** The same border, in the error colour, for a field that failed. */
    public static Border errorBorder() {
        return focusRing(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DANGER, 2),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));
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
     * Installs a plain look and feel so the palette above is what the user
     * actually sees. Swing's cross-platform defaults are the reason an
     * application looks unfinished out of the box.
     *
     * <p>The delegates that draw their own background - combos, text fields,
     * tables, option panes - are told the palette's colours as well, so a
     * component that was never passed through {@link #styleInput} still comes
     * out in the application's colours rather than the platform's.
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

                    javax.swing.UIManager.put("TextField.background", new ColorUIResource(CARD));
                    javax.swing.UIManager.put("TextField.foreground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("TextField.caretForeground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("PasswordField.background", new ColorUIResource(CARD));
                    javax.swing.UIManager.put("PasswordField.foreground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("PasswordField.caretForeground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("ComboBox.background", new ColorUIResource(CARD));
                    javax.swing.UIManager.put("ComboBox.foreground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("ComboBox.selectionBackground", new ColorUIResource(TABLE_SELECTION));
                    javax.swing.UIManager.put("ComboBox.selectionForeground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("ComboBox.buttonBackground", new ColorUIResource(PAGE));
                    javax.swing.UIManager.put("Table.background", new ColorUIResource(CARD));
                    javax.swing.UIManager.put("Table.foreground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("Table.selectionBackground", new ColorUIResource(TABLE_SELECTION));
                    javax.swing.UIManager.put("Table.selectionForeground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("TableHeader.background", new ColorUIResource(TABLE_HEADER));
                    javax.swing.UIManager.put("TableHeader.foreground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("Label.foreground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("Button.background", new ColorUIResource(PAGE));
                    javax.swing.UIManager.put("Button.foreground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("Button.text", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("Button.select", new ColorUIResource(TABLE_SELECTION));
                    javax.swing.UIManager.put("ProgressBar.selectionForeground",
                            new ColorUIResource(PRIMARY));
                    javax.swing.UIManager.put("OptionPane.background", new ColorUIResource(CARD));
                    javax.swing.UIManager.put("OptionPane.messageForeground", new ColorUIResource(TEXT));
                    javax.swing.UIManager.put("OptionPane.buttonFont", new javax.swing.plaf.FontUIResource(BODY_BOLD));
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