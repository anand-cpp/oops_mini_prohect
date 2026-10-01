package com.academic.management.ui.common;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumn;

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
 */
public final class DataTable {

    private final JTable table;

    private DataTable(JTable table) {
        this.table = table;
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
        return new DataTable(table);
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
        return Theme.tableScroll(table);
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
