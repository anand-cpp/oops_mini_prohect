package com.academic.management.ui.common;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;

/**
 * A styled {@link JTable} over a {@link DisplayTableModel}, plus the
 * selection plumbing every table screen needs.
 *
 * <h2>Why this exists</h2>
 * {@link Theme#tableScroll(JTable)} handles appearance, but each screen was
 * still having to remember three more things: install a renderer so the
 * alternating row stripes the scroll pane implies actually appear, apply the
 * preferred widths the model already carries, and convert the selected view
 * row back to a model row before reading a cell. All three are easy to get
 * wrong in a way that only shows up as a subtly broken screen, so they live
 * here once.
 *
 * <h2>Why the stripes need an explicit renderer</h2>
 * Swing's default cell renderer fills every cell with the table's own
 * background, which means a table that declares row stripes paints no
 * stripes at all. A renderer has to be installed per column for the banding
 * to appear, which is why this class installs one on every column by
 * default rather than leaving it to each screen.
 *
 * <h2>Why the widths are computed</h2>
 * The widths in the panel are guesses written before anyone had seen the
 * screen. Measured here instead, from the header titles and the values that
 * are actually loaded, the table fits the window it is in: the columns named
 * by {@link #fit(int...)} absorb whatever width is left over, so no column is
 * ever pushed off the right-hand edge and no scrollbar appears at the
 * application's own window size.
 */
public final class DataTable {

    /** No column is ever narrower than this, whatever its content says. */
    private static final int MIN_COLUMN_WIDTH = 64;

    /** Cell text needs its own margin; a value flush to the rule is unreadable. */
    private static final int CELL_PADDING = 12;

    /**
     * How much wider than its header a column is allowed to get for the sake
     * of one long value. Without a cap, a 120-character address would set the
     * width of the column and push the twelve that follow it off the screen.
     */
    private static final int MAX_VALUE_EXTRA = 140;

    /**
     * How many rows are measured. Enough that the widest value on a
     * realistically sized table is seen; beyond that the measurement costs
     * more than the layout gains, and the columns have long since reached
     * their cap.
     */
    private static final int SAMPLE_ROWS = 250;

    private final JTable table;
    private final JScrollPane scrollPane;

    /** Columns allowed to absorb spare width; empty until {@link #fit}. */
    private transient int[] flexible = new int[0];

    private DataTable(JTable table) {
        this.table = table;
        this.scrollPane = Theme.tableScroll(table);
        this.scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refitColumns();
            }
        });
    }

    /**
     * Builds a table for a model, applying its preferred widths and a
     * text renderer on every column.
     *
     * @param model the read-only model to display
     */
    public static DataTable over(DisplayTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        for (int column = 0; column < model.getColumnCount(); column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            tableColumn.setCellRenderer(Theme.textCellRenderer());
            int width = model.preferredWidth(column);
            if (width > 0) {
                tableColumn.setPreferredWidth(width);
            }
        }
        DataTable dataTable = new DataTable(table);
        // Refitted whenever the rows change, so a column that was sized from
        // an empty table is not left at its header width for ever.
        model.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent event) {
                SwingUtilities.invokeLater(dataTable::refitColumns);
            }
        });
        return dataTable;
    }

    /**
     * Right-aligns the given columns so their figures line up.
     *
     * <p>Column numbers, not names, because a panel builds its model from
     * constants and the index cannot drift out of step with the header the
     * way a string can.
     */
    public DataTable numeric(int... columns) {
        for (int column : columns) {
            if (column >= 0 && column < table.getColumnCount()) {
                table.getColumnModel().getColumn(column).setCellRenderer(
                        Theme.numericCellRenderer());
            }
        }
        return this;
    }

    /**
     * Marks the given columns as verdicts, so "Pass" and "Regular" are
     * coloured green and "Fail" red.
     */
    public DataTable verdict(int... columns) {
        for (int column : columns) {
            if (column >= 0 && column < table.getColumnCount()) {
                table.getColumnModel().getColumn(column).setCellRenderer(
                        Theme.verdictCellRenderer());
            }
        }
        return this;
    }

    /** The styled table, for adding a listener or reading its state. */
    public JTable component() {
        return table;
    }

    /** The table inside a scroll pane, ready to put in a page's content. */
    public JScrollPane scroll() {
        return scrollPane;
    }

    /**
     * Declares which columns absorb spare width when the window is resized.
     *
     * <p>Choose the column whose content is genuinely variable - a person's
     * name, a course title - rather than the one that merely looks emptiest.
     * A numeric column that grows to fill the window reads as a mistake,
     * because its figures end up scattered across 300 pixels of white space.
     *
     * @param flexibleColumns indexes of the columns allowed to grow and shrink
     */
    public DataTable fit(int... flexibleColumns) {
        int[] valid = Arrays.stream(flexibleColumns)
                .filter(column -> column >= 0 && column < table.getColumnCount())
                .distinct()
                .sorted()
                .toArray();
        this.flexible = valid;
        refitColumns();
        return this;
    }

    /**
     * Recomputes every column's width from its header and its content.
     *
     * <p>Each column ends up with two numbers: a floor that fits its header
     * title, so a column is never so narrow that its own name is unreadable,
     * and a preferred width that also fits its widest value. Then:
     *
     * <ol>
     *   <li>if the preferred widths fit, the spare width is shared equally
     *       between the flexible columns, so the table fills the pane exactly
     *       and no scrollbar appears;</li>
     *   <li>if they do not fit, the flexible columns give up their slack
     *       first, then every other column gives up a proportional share down
     *       to its floor;</li>
     *   <li>and only if the floors alone do not fit - twelve columns of
     *       header text in a 900-pixel window - do the columns keep their
     *       floors and the pane scrolls horizontally. Every column stays
     *       present and reachable in that case; none is ever dropped.</li>
     * </ol>
     */
    public void refitColumns() {
        int available = viewportWidth();
        int count = table.getColumnCount();
        if (available <= 0 || count == 0) {
            return;
        }
        FontMetrics cells = table.getFontMetrics(table.getFont());
        javax.swing.table.JTableHeader header = table.getTableHeader();
        FontMetrics titles = header.getFontMetrics(header.getFont());
        DisplayTableModel model = (DisplayTableModel) table.getModel();

        int[] floor = new int[count];
        int[] wanted = new int[count];
        int floorTotal = 0;
        int wantedTotal = 0;
        int measuredRows = Math.min(model.rowCount(), SAMPLE_ROWS);

        for (int column = 0; column < count; column++) {
            int titleWidth = titles.stringWidth(table.getColumnName(column)) + CELL_PADDING;
            int widestValue = 0;
            for (int row = 0; row < measuredRows; row++) {
                widestValue = Math.max(widestValue,
                        cells.stringWidth(model.valueAt(row, column)));
            }
            floor[column] = Math.max(MIN_COLUMN_WIDTH, titleWidth);
            wanted[column] = Math.max(floor[column],
                    Math.min(floor[column] + MAX_VALUE_EXTRA, widestValue + CELL_PADDING));
            floorTotal += floor[column];
            wantedTotal += wanted[column];
        }

        int[] result = distribute(wanted, floor, available, wantedTotal, floorTotal);
        for (int column = 0; column < count; column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            tableColumn.setPreferredWidth(result[column]);
            tableColumn.setMinWidth(Math.min(result[column], MIN_COLUMN_WIDTH));
            tableColumn.setMaxWidth(Integer.MAX_VALUE);
            tableColumn.setWidth(result[column]);
        }
        table.doLayout();
    }

    /**
     * Shares {@code available} across the columns.
     *
     * @param wanted      the width each column would like
     * @param floor       the width below which a column's header stops fitting
     * @param available   the viewport's width
     * @param wantedTotal the sum of {@code wanted}
     * @param floorTotal  the sum of {@code floor}
     */
    private int[] distribute(int[] wanted, int[] floor, int available,
                             int wantedTotal, int floorTotal) {
        int[] result = wanted.clone();
        if (wantedTotal <= available) {
            // Spare width, shared equally between the flexible columns so no
            // single one of them becomes a column of white space.
            int spare = available - wantedTotal;
            if (flexible.length > 0 && spare > 0) {
                int share = spare / flexible.length;
                int remainder = spare - share * flexible.length;
                for (int index = 0; index < flexible.length; index++) {
                    result[flexible[index]] += share
                            + (index == 0 ? remainder : 0);
                }
            } else if (flexible.length == 0) {
                // Nothing declared, so the widest column takes the slack
                // rather than the last one, which is usually a narrow figure.
                int widest = 0;
                for (int column = 1; column < result.length; column++) {
                    if (result[column] > result[widest]) {
                        widest = column;
                    }
                }
                result[widest] += spare;
            }
            return result;
        }

        int[] shrunk = wanted.clone();
        int shortfall = wantedTotal - available;

        // Flexible columns give up their slack first.
        for (int column : flexible) {
            if (shortfall <= 0) {
                break;
            }
            int give = Math.min(shortfall, shrunk[column] - floor[column]);
            shrunk[column] -= give;
            shortfall -= give;
        }

        // Then everything else gives up a proportional share, down to its
        // floor, so a wide column is not the only one that gets squeezed.
        if (shortfall > 0) {
            int slack = 0;
            for (int column = 0; column < shrunk.length; column++) {
                slack += shrunk[column] - floor[column];
            }
            if (slack > 0) {
                int applied = 0;
                for (int column = 0; column < shrunk.length; column++) {
                    int give = (int) ((long) (shrunk[column] - floor[column]) * shortfall / slack);
                    shrunk[column] -= give;
                    applied += give;
                }
                // The rounding remainder lands on whichever column has the
                // most room left, which keeps the total exact.
                if (applied < shortfall) {
                    for (int column = shrunk.length - 1; column >= 0 && applied < shortfall;
                            column--) {
                        int give = Math.min(shortfall - applied,
                                shrunk[column] - floor[column]);
                        shrunk[column] -= give;
                        applied += give;
                    }
                }
            }
        }
        return shrunk;
    }

    /**
     * The width the table has to work with.
     *
     * <p>The viewport rather than the pane: the pane's own width includes the
     * vertical scrollbar, so measuring it would size the columns for space
     * that a scrollbar then takes away - and would oscillate, growing the
     * scrollbar's job each time it did.
     */
    private int viewportWidth() {
        JViewport viewport = scrollPane.getViewport();
        return viewport.getWidth() - viewport.getInsets().left - viewport.getInsets().right;
    }

    /**
     * The model row currently selected.
     *
     * <p>Converts from the view row, which is what the user clicked, to the
     * model row, which is what the data is in. Reading a view index straight
     * out of the model is wrong as soon as the user sorts a column, and it is
     * wrong in a way that deletes the wrong record.
     *
     * @return the model row index, or -1 when nothing is selected
     */
    public int selectedRow() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
    }

    /**
     * The text of one cell in the selected row.
     *
     * @return the cell's text, or null when no row is selected or the
     *         column does not exist
     */
    public String selectedValue(int column) {
        int row = selectedRow();
        if (row < 0 || column < 0 || column >= table.getModel().getColumnCount()) {
            return null;
        }
        Object value = table.getModel().getValueAt(row, column);
        return value == null ? null : value.toString();
    }

    /**
     * Selects the row whose first-column value matches, for a screen that
     * arrives with a record already chosen.
     *
     * @return true when a row was found
     */
    public boolean selectWhereFirstColumnEquals(String value) {
        DisplayTableModel model = (DisplayTableModel) table.getModel();
        for (int row = 0; row < model.rowCount(); row++) {
            if (value.equals(model.valueAt(row, 0))) {
                int viewRow = table.convertRowIndexToView(row);
                table.setRowSelectionInterval(viewRow, viewRow);
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                return true;
            }
        }
        return false;
    }

    /** Drops the selection, so a stale row cannot be acted on later. */
    public void clearSelection() {
        table.clearSelection();
    }

    /** Puts the keyboard focus on the table. */
    public void focus() {
        table.requestFocusInWindow();
    }
}