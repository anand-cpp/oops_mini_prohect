package com.academic.management.ui.common;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * A read-only table model over a list of rows.
 *
 * <h2>Why the rows are already display strings</h2>
 * Every column class in this model is {@link String}. That is a deliberate
 * trade: the alternative is a model that holds domain objects and formats
 * them per column, which looks tidier but means the sorting, filtering and
 * copy-to-clipboard behaviour of {@code JTable} all operate on the
 * unformatted object. {@code "7.5"} then sorts as text, and a copy
 * produces "7.5%" with the percent sign baked in. Formatting once, in the
 * panel that understands the domain, keeps the table's own behaviour
 * correct and makes every value a user can read or paste.
 *
 * <p>The consequence is that this model is immutable from the table's
 * point of view: {@link #replaceAll} swaps the data and fires the right
 * events, and no cell is ever individually editable.
 */
public final class DisplayTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final String[] headers;
    private final transient List<String[]> rows = new ArrayList<>();
    private final int[] columnWidths;

    /**
     * @param headers column titles
     * @param columnWidths preferred width per column, or 0 to let the
     *                     table decide
     */
    public DisplayTableModel(String[] headers, int[] columnWidths) {
        this.headers = headers.clone();
        this.columnWidths = columnWidths == null ? new int[headers.length] : columnWidths.clone();
    }

    /**
     * Replaces every row.
     *
     * <p>Rebuilding from scratch rather than firing per-row insert and
     * delete events is both simpler and faster here, because a refresh
     * almost always replaces all of the data; the single
     * {@code fireTableDataChanged} keeps the selection and scroll position
     * from flickering row by row.
     */
    public void replaceAll(List<String[]> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        fireTableDataChanged();
    }

    /** The preferred width configured for a column. */
    public int preferredWidth(int column) {
        return column >= 0 && column < columnWidths.length ? columnWidths[column] : 0;
    }

    /** The row at the given index, or null when out of range. */
    public String[] rowAt(int index) {
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    /** One cell's text, or an empty string when out of range. */
    public String valueAt(int row, int column) {
        String[] row1 = rowAt(row);
        if (row1 == null || column < 0 || column >= row1.length) {
            return "";
        }
        String value = row1[column];
        return value == null ? "" : value;
    }

    /** How many rows are currently shown. */
    public int rowCount() {
        return rows.size();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return valueAt(rowIndex, columnIndex);
    }
}
