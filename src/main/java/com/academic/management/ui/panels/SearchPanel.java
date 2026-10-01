package com.academic.management.ui.panels;

import com.academic.management.exception.AppException;
import com.academic.management.service.SearchService;
import com.academic.management.service.ServiceRegistry;
import com.academic.management.ui.common.DataTable;
import com.academic.management.ui.common.DisplayTableModel;
import com.academic.management.ui.common.PagePanel;
import com.academic.management.ui.common.Theme;
import com.academic.management.ui.common.UiSupport;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * One text box that searches students, faculty and courses.
 *
 * <h2>Why search lives on its own page</h2>
 * The three entity pages each have a filter that only searches their own
 * table, so answering "which record is this?" across the whole system would
 * otherwise mean checking three pages in turn. Here one query runs against
 * all three and comes back in a single shape, so the answer is one list
 * rather than three sets of results.
 *
 * <h2>Why the scope is a dropdown rather than three buttons</h2>
 * Searching everything and narrowing when there is too much is what a user
 * actually wants, so "Everything" is the first entry and the default, and it
 * expands to the three scopes the service supports rather than becoming a
 * fourth one.
 *
 * <h2>Why typing does not search on every keystroke</h2>
 * Each keystroke would be a database round trip - a three-character term
 * would cost three queries and throw three away. A short pause after the
 * last keystroke collapses a burst of typing into one query, without making
 * the user press anything. The pause lives in {@link PagePanel}, so every
 * filter in the application settles at the same speed.
 *
 * <h2>Why the text is captured on the event thread</h2>
 * {@link #load()} runs on a worker thread, and a Swing component must not
 * be read from one. The term and the scope are therefore read from the
 * controls on the event thread and stored in fields before the load starts;
 * the worker reads only those fields, which {@code Thread.start} publishes
 * to it.
 */
public final class SearchPanel extends PagePanel {

    private static final long serialVersionUID = 1L;

    private static final String[] HEADERS = {"Type", "ID", "Code / Roll", "Name", "Detail", "Extra"};

    /**
     * The three columns whose content is unbounded and therefore worth the
     * spare width. A hit's name, detail and extra columns are free text from
     * three different tables, so a fixed width would either truncate a long
     * email or strand empty space beside a two-word name.
     */
    private static final int NAME_COLUMN = 3;
    private static final int DETAIL_COLUMN = 4;
    private static final int EXTRA_COLUMN = 5;

    private static final int[] WIDTHS = {90, 100, 120, 240, 240, 200};

    private static final String ALL_SCOPES = "Everything";

    private final transient ServiceRegistry services;
    private final transient DisplayTableModel model = new DisplayTableModel(HEADERS, WIDTHS);
    private final transient DataTable table = DataTable.over(model)
            .fit(NAME_COLUMN, DETAIL_COLUMN, EXTRA_COLUMN);

    private final JTextField queryField = Theme.styleInput(new JTextField());
    private final JComboBox<String> scopePicker = Theme.styleSelect(new JComboBox<>());

    /**
     * The centre of the page, holding either the table or the empty state.
     *
     * <p>Held as a field rather than built inside {@code buildBody} because
     * {@link #showRows} has to swap its child on every render.
     */
    private final JPanel tableArea = new JPanel(new BorderLayout());

    /** The request the next load will run, captured on the event thread. */
    private transient String term = "";
    private transient List<SearchService.Scope> scopes = allScopes();

    public SearchPanel(ServiceRegistry services) {
        super("Search", "Find a student, a member of faculty or a course from one box.");
        this.services = services;

        JButton search = Theme.button("Search", Theme.PRIMARY);
        search.addActionListener(event -> runSearch());
        addToolbarButton(search);

        JButton clear = Theme.secondaryButton("Clear");
        clear.addActionListener(event -> clearSearch());
        addToolbarButton(clear);

        JButton refresh = Theme.secondaryButton("Refresh");
        refresh.addActionListener(event -> runSearch());
        addActionButton(refresh);

        setContent(buildBody());
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildBody() {
        queryField.setPreferredSize(new Dimension(320, 30));
        queryField.setToolTipText("Type a name, an id, an email or a course code. "
                + "Leave it empty to list everything.");
        queryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                reloadWhenTypingSettles();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                reloadWhenTypingSettles();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                reloadWhenTypingSettles();
            }
        });

        scopePicker.setPreferredSize(new Dimension(140, 30));
        scopePicker.addItem(ALL_SCOPES);
        for (SearchService.Scope scope : SearchService.Scope.values()) {
            scopePicker.addItem(scope.getLabel());
        }
        scopePicker.addActionListener(event -> runSearch());

        JPanel filters = row(filterLabel("Search for:", queryField),
                filterLabel("In:", scopePicker),
                Theme.caption("Matches names, ids, emails and course codes."));
        filters.setBorder(Theme.padding(0, 0, Theme.GAP, 0));

        tableArea.setOpaque(false);
        tableArea.add(table.scroll(), BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, Theme.GAP));
        body.setOpaque(false);
        body.add(filters, BorderLayout.NORTH);
        body.add(tableArea, BorderLayout.CENTER);
        return body;
    }

    // ------------------------------------------------------------------
    // Searching
    // ------------------------------------------------------------------

    /**
     * Captures the request and runs it.
     *
     * <p>Reading the controls here, on the event thread, is what keeps
     * {@link #load()} free of them.
     */
    private void runSearch() {
        term = queryField.getText().trim();
        scopes = selectedScopes();
        reload();
    }

    private void clearSearch() {
        queryField.setText("");
        runSearch();
    }

    /**
     * Runs one search per selected scope, in scope order.
     *
     * <p>Three queries rather than one, because they are three different
     * tables. They run on the worker thread, so the window stays responsive
     * for a term that matches a lot of records.
     */
    @Override
    protected List<String[]> load() throws AppException {
        List<String[]> rows = new ArrayList<>();
        for (SearchService.Scope scope : scopes) {
            for (SearchService.SearchHit hit : services.search().search(scope, term)) {
                rows.add(new String[]{
                        scope.getLabel(),
                        hit.id(),
                        hit.primary(),
                        UiSupport.orNone(hit.name()),
                        UiSupport.orNone(hit.detail()),
                        UiSupport.orNone(hit.extra())});
            }
        }
        return rows;
    }

    @Override
    protected void render(List<String[]> rows) {
        fillTable(model, rows);
        setStatus(describe(rows.size()));
        showRows(tableArea, table.scroll(), rows.size(),
                term.isEmpty()
                        ? "Nothing to search yet. Type a name, an id, an email or a course "
                          + "code above."
                        : "Nothing matches '" + term + "' in this scope. Try a shorter "
                          + "term, or widen the scope.");
    }

    private String describe(int matches) {
        StringBuilder status = new StringBuilder(
                UiSupport.count(matches, "match", "matches"));
        if (!term.isEmpty()) {
            status.append(" for '").append(term).append('\'');
        }
        if (matches == 0) {
            status.append(" — try a shorter term, or widen the scope");
        }
        return status.toString();
    }

    /**
     * The scopes the dropdown currently selects.
     *
     * <p>"Everything" expands to all three rather than becoming a fourth
     * scope, so the list of scopes stays the set the service supports.
     */
    private List<SearchService.Scope> selectedScopes() {
        Object selected = scopePicker.getSelectedItem();
        String chosen = selected == null ? ALL_SCOPES : String.valueOf(selected);
        if (ALL_SCOPES.equals(chosen)) {
            return allScopes();
        }
        List<SearchService.Scope> matching = new ArrayList<>();
        for (SearchService.Scope scope : SearchService.Scope.values()) {
            if (scope.getLabel().equals(chosen)) {
                matching.add(scope);
            }
        }
        return matching;
    }

    /** Every scope, in the order the results are listed. */
    private static List<SearchService.Scope> allScopes() {
        return List.of(SearchService.Scope.values());
    }
}
